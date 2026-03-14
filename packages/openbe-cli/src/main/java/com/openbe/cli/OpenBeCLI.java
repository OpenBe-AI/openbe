package com.openbe.cli;

import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.LaneQueueRouter;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * OpenBe CLI 主入口
 * 蜂群架构本地 AI 智能体系统的命令行控制中心
 */
@Command(
    name = "openbe",
    version = "OpenBe 0.1.0",
    mixinStandardHelpOptions = true,
    description = "🐝 OpenBe — 本地去中心化 AI 智能体系统（蜂群架构）",
    subcommands = {
        OpenBeCLI.StartCommand.class,
        OpenBeCLI.StopCommand.class,
        OpenBeCLI.StatusCommand.class,
        OpenBeCLI.PingCommand.class,
        OpenBeCLI.AskCommand.class,
        OpenBeCLI.ResetCommand.class,
        OpenBeCLI.OnboardCommand.class,
        HelpCommand.class
    }
)
public class OpenBeCLI implements Runnable {

    private static final String OPENBE_HOME = System.getProperty("user.home") + "/.openbe";
    private static final String PID_FILE    = OPENBE_HOME + "/openbe-queen.pid";

    // ── ASCII Banner ──────────────────────────────────────────────────────────
    private static final String BANNER = """
            \033[33m
             ██████╗ ██████╗ ███████╗███╗   ██╗██████╗ ███████╗
            ██╔═══██╗██╔══██╗██╔════╝████╗  ██║██╔══██╗██╔════╝
            ██║   ██║██████╔╝█████╗  ██╔██╗ ██║██████╔╝█████╗
            ██║   ██║██╔═══╝ ██╔══╝  ██║╚██╗██║██╔══██╗██╔══╝
            ╚██████╔╝██║     ███████╗██║ ╚████║██████╔╝███████╗
             ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═══╝╚═════╝ ╚══════╝
            \033[0m
            \033[90m  本地去中心化 AI 智能体系统 · 蜂群架构 v0.1.0\033[0m
            """;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OpenBeCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // 无子命令时打印 Banner + 帮助
        System.out.println(BANNER);
        new CommandLine(this).usage(System.out);
    }

    // ── start ─────────────────────────────────────────────────────────────────
    @Command(name = "start", description = "启动 OpenBe 蜂巢（Redis + Queen 核心）")
    static class StartCommand implements Runnable {

        @CommandLine.Option(names = {"--port", "-p"}, description = "Queen 监听端口（默认 8080）", defaultValue = "8080")
        int port;

        @Override
        public void run() {
            System.out.println(BANNER);
            System.out.println("\033[36m[1/3] 🐝 正在启动基础设施 (Redis)...\033[0m");

            // 先确认 Docker daemon 是否可用
            if (!runCommand("docker info", null, false)) {
                System.err.println("\033[31m✗ Docker daemon 未运行，请先启动 Docker Desktop / dockerd\033[0m");
                return;
            }

            String dc = resolveDockerCompose();
            System.out.println("\033[90m  使用命令：" + dc + "\033[0m");
            if (!runCommand(dc + " up -d", resolveWorkspaceDir(), true)) {
                System.err.println("\033[31m✗ Redis 启动失败，见上方输出\033[0m");
                return;
            }
            System.out.println("\033[32m✓ Redis 容器已就绪（端口 6379）\033[0m");

            System.out.printf("\033[36m[2/3] 🐝 正在启动 OpenBe Queen（端口 %d）...\033[0m%n", port);

            Path queenJar = findQueenJar();
            if (queenJar == null) {
                System.err.println("\033[31m✗ 未找到 openbe-queen.jar，请先执行 mvn package。\033[0m");
                return;
            }

            try {
                // 用绝对路径，避免子进程 PATH 中找不到 java
                String javaBin = ProcessHandle.current().info().command()
                    .map(cmd -> cmd.endsWith("/java") ? cmd : null)
                    .orElseGet(() -> {
                        for (String p : new String[]{
                            System.getProperty("user.home") + "/.orbstack/bin/java",
                            "/opt/homebrew/opt/openjdk/bin/java",
                            "/opt/homebrew/opt/openjdk@21/bin/java",
                            "/usr/local/opt/openjdk/bin/java",
                            "/usr/bin/java"}) {
                            if (new File(p).canExecute()) return p;
                        }
                        return "java";
                    });
                ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-jar", queenJar.toString(),
                    "--server.port=" + port
                );
                pb.redirectOutput(new File(OPENBE_HOME + "/queen.log"));
                pb.redirectError(new File(OPENBE_HOME + "/queen.log"));
                Process process = pb.start();

                Files.createDirectories(Paths.get(OPENBE_HOME));
                Files.writeString(Paths.get(PID_FILE), String.valueOf(process.pid()));

                System.out.println("\033[32m✓ OpenBe Queen 已在后台启动（PID: " + process.pid() + "）\033[0m");
            } catch (IOException e) {
                System.err.println("\033[31m✗ 启动 Queen 失败：" + e.getMessage() + "\033[0m");
                return;
            }

            System.out.println();
            System.out.println("\033[33m[3/3] 🍯 蜂巢状态 ─────────────────────────────\033[0m");
            System.out.printf("       🐝 OpenBe 蜂巢已启动，监听本地端口 %d%n", port);
            System.out.println("       📡 Redis 信息素总线：localhost:6379");
            System.out.println("       📋 日志：" + OPENBE_HOME + "/queen.log");
            System.out.println("\033[33m──────────────────────────────────────────────\033[0m");
            System.out.println("\033[90m  运行 `openbe status` 查看运行状态\033[0m");
        }
    }

    // ── stop ──────────────────────────────────────────────────────────────────
    @Command(name = "stop", description = "优雅关闭 OpenBe 蜂巢")
    static class StopCommand implements Runnable {

        @Override
        public void run() {
            System.out.println("\033[36m[1/2] 🛑 正在关闭 OpenBe Queen...\033[0m");

            Path pidPath = Paths.get(PID_FILE);
            if (Files.exists(pidPath)) {
                try {
                    long pid = Long.parseLong(Files.readString(pidPath).trim());
                    ProcessHandle.of(pid).ifPresentOrElse(
                        ph -> {
                            ph.destroy();
                            System.out.println("\033[32m✓ Queen 进程（PID: " + pid + "）已终止\033[0m");
                        },
                        () -> System.out.println("\033[90m  Queen 进程已不存在（可能已退出）\033[0m")
                    );
                    Files.deleteIfExists(pidPath);
                } catch (IOException | NumberFormatException e) {
                    System.err.println("\033[31m✗ 读取 PID 文件失败：" + e.getMessage() + "\033[0m");
                }
            } else {
                System.out.println("\033[90m  未找到 PID 文件，Queen 可能未运行\033[0m");
            }

            System.out.println("\033[36m[2/2] 🛑 正在关闭基础设施 (Redis)...\033[0m");
            String dc = resolveDockerCompose();
            if (runCommand(dc + " down", resolveWorkspaceDir(), true)) {
                System.out.println("\033[32m✓ Redis 容器已停止\033[0m");
            } else {
                System.err.println("\033[31m✗ " + dc + " down 执行失败\033[0m");
            }

            System.out.println("\033[33m🐝 OpenBe 蜂巢已安全关闭\033[0m");
        }
    }

    // ── status ────────────────────────────────────────────────────────────────
    @Command(name = "status", description = "检查 OpenBe 蜂巢运行状态")
    static class StatusCommand implements Runnable {

        @Override
        public void run() {
            System.out.println("\033[33m🐝 OpenBe 蜂巢状态检查 ─────────────────────\033[0m");

            // 检查 Queen 进程
            Path pidPath = Paths.get(PID_FILE);
            boolean queenAlive = false;
            if (Files.exists(pidPath)) {
                try {
                    long pid = Long.parseLong(Files.readString(pidPath).trim());
                    queenAlive = ProcessHandle.of(pid)
                        .map(ProcessHandle::isAlive)
                        .orElse(false);
                    if (queenAlive) {
                        System.out.println("  🟢 Queen    ：运行中（PID: " + pid + "）");
                    } else {
                        System.out.println("  🔴 Queen    ：进程已退出（PID 文件残留）");
                    }
                } catch (IOException | NumberFormatException e) {
                    System.out.println("  🔴 Queen    ：状态未知（" + e.getMessage() + "）");
                }
            } else {
                System.out.println("  🔴 Queen    ：未运行");
            }

            // 检查 Redis（尝试 TCP 连接）
            boolean redisAlive = checkTcpPort("127.0.0.1", 6379, 1000);
            if (redisAlive) {
                System.out.println("  🟢 Redis    ：运行中（localhost:6379）");
            } else {
                System.out.println("  🔴 Redis    ：未运行或无法连接");
            }

            // 检查 Docker daemon
            boolean dockerRunning = runCommand("docker info", null, false);
            String dcCmd = resolveDockerCompose();
            System.out.println("  " + (dockerRunning ? "🟢" : "🔴") + " Docker   ："
                + (dockerRunning ? "运行中（" + dcCmd + "）" : "未运行"));

            System.out.println("\033[33m─────────────────────────────────────────────\033[0m");
            if (queenAlive && redisAlive) {
                System.out.println("\033[32m  ✓ 蜂巢运行正常，所有系统就绪 🍯\033[0m");
            } else {
                System.out.println("\033[31m  ✗ 蜂巢状态异常，运行 `openbe start` 启动\033[0m");
            }
        }
    }

    // ── ping ──────────────────────────────────────────────────────────────────
    @Command(name = "ping", description = "向绿色车道发射测试信息素，验证神经总线连通性")
    static class PingCommand implements Runnable {

        @CommandLine.Option(
            names = {"--host"}, description = "Redis 地址（默认 localhost）", defaultValue = "localhost")
        String redisHost;

        @CommandLine.Option(
            names = {"--port", "-p"}, description = "Redis 端口（默认 6379）", defaultValue = "6379")
        int redisPort;

        /** 最小化 Spring 上下文 —— 仅扫描 gateway，不启 Web，不打印 Banner/日志 */
        @SpringBootApplication(scanBasePackages = "com.openbe.gateway")
        static class PingBootstrap {}

        @Override
        public void run() {
            System.out.println("\033[36m[CLI] 正在连接信息素总线（Redis "
                + redisHost + ":" + redisPort + "）...\033[0m");

            // 把 Redis 地址传给 Spring 上下文
            System.setProperty("spring.data.redis.host", redisHost);
            System.setProperty("spring.data.redis.port", String.valueOf(redisPort));

            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PingBootstrap.class)
                    .bannerMode(Banner.Mode.OFF)
                    .logStartupInfo(false)
                    .properties(
                        "spring.main.web-application-type=none",
                        "logging.level.root=OFF")
                    .run(new String[0])) {

                LaneQueueRouter router = ctx.getBean(LaneQueueRouter.class);

                Pheromone pheromone = Pheromone.builder()
                    .sourceBee(BeeType.SCOUT)
                    .targetBee(BeeType.QUEEN)
                    .laneColor(LaneColor.GREEN)
                    .payload("{\"ping\":\"hello hive\"}")
                    .build();

                router.emitPheromone(pheromone);

                System.out.println("\033[32m[CLI] 信息素已发射至绿色车道！\033[0m");
                System.out.printf("\033[90m      taskId : %s%n      payload : %s\033[0m%n",
                    pheromone.getTaskId(), pheromone.getPayload());

            } catch (Exception e) {
                System.err.println("\033[31m[CLI] 发射失败：" + e.getMessage() + "\033[0m");
                System.err.println("\033[31m      请确认蜂巢已启动（运行 openbe start）\033[0m");
            }
        }
    }

    // ── ask ───────────────────────────────────────────────────────────────────
    @Command(name = "ask", description = "向蜂巢提问，由工蜂调用大模型回答")
    static class AskCommand implements Runnable {

        @CommandLine.Parameters(index = "0", arity = "1..*",
            description = "您的问题（多个词会自动拼接）")
        String[] words;

        @CommandLine.Option(
            names = {"--host"}, description = "Redis 地址（默认 localhost）", defaultValue = "localhost")
        String redisHost;

        @CommandLine.Option(
            names = {"--port", "-p"}, description = "Redis 端口（默认 6379）", defaultValue = "6379")
        int redisPort;

        @CommandLine.Option(
            names = {"--model", "-m"}, description = "指定大模型名称，覆盖工蜂默认配置（如 llama3、qwen3.5:9b）")
        String model;

        @SpringBootApplication(scanBasePackages = "com.openbe.gateway")
        static class AskBootstrap {}

        @Override
        public void run() {
            String question = String.join(" ", words);
            System.out.println("\033[36m[CLI] 正在将问题发送至蜂巢...\033[0m");
            System.out.println("\033[90m      问题：" + question + "\033[0m");
            if (model != null && !model.isBlank()) {
                System.out.println("\033[90m      模型：" + model + "\033[0m");
            }

            System.setProperty("spring.data.redis.host", redisHost);
            System.setProperty("spring.data.redis.port", String.valueOf(redisPort));

            try (var ctx = new org.springframework.boot.builder.SpringApplicationBuilder(AskBootstrap.class)
                    .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                    .logStartupInfo(false)
                    .properties(
                        "spring.main.web-application-type=none",
                        "logging.level.root=OFF")
                    .run(new String[0])) {

                LaneQueueRouter router = ctx.getBean(LaneQueueRouter.class);

                // 构造 payload，可选携带 model 覆盖参数
                com.fasterxml.jackson.databind.node.ObjectNode payloadNode =
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                payloadNode.put("question", question);
                if (model != null && !model.isBlank()) {
                    payloadNode.put("model", model);
                }
                String payload = payloadNode.toString();

                Pheromone pheromone = Pheromone.builder()
                    .sourceBee(BeeType.SCOUT)
                    .targetBee(BeeType.QUEEN)
                    .laneColor(LaneColor.GREEN)
                    .payload(payload)
                    .build();

                router.emitPheromone(pheromone);

                System.out.println("\033[32m[CLI] 问题已发射至蜂巢！\033[0m");
                System.out.printf("\033[90m      taskId : %s\033[0m%n", pheromone.getTaskId());
                System.out.println("\033[90m      结果将在蜂后日志中输出，查看方式：\033[0m");
                System.out.println("\033[90m      tail -f ~/.openbe/queen.log\033[0m");

            } catch (Exception e) {
                System.err.println("\033[31m[CLI] 发送失败：" + e.getMessage() + "\033[0m");
                System.err.println("\033[31m      请确认蜂巢已启动（运行 openbe start）\033[0m");
            }
        }
    }

    // ── reset ─────────────────────────────────────────────────────────────────
    @Command(
        name = "reset",
        description = {
            "清理 OpenBe 本地数据",
            "",
            "  软清理（默认）：删除蜂巢记忆、日志、注册表，保留配置和 jar 包",
            "  硬清理（--hard）：彻底删除 ~/.openbe，系统回到安装后白板状态"
        }
    )
    static class ResetCommand implements Runnable {

        @CommandLine.Option(
            names = {"--hard", "--nuke"},
            description = "硬核清理：彻底销毁 ~/.openbe 全部内容（无法恢复）")
        boolean hard;

        @CommandLine.Option(
            names = {"--yes", "-y"},
            description = "跳过交互确认，直接执行（脚本模式）")
        boolean yes;

        // 软清理：只删这些目录/文件，保留配置和 jar
        private static final String[] SOFT_DIRS  = { "hives", "logs", "system" };
        private static final String[] SOFT_GLOBS = { "*.log", "*.pid", "bees-registry.json" };

        @Override
        public void run() {
            Path home = Paths.get(OPENBE_HOME);

            if (!Files.exists(home)) {
                System.out.println("\033[90m  ~/.openbe 不存在，无需清理。\033[0m");
                return;
            }

            printBanner();

            if (hard) {
                runHardReset(home);
            } else {
                runSoftReset(home);
            }
        }

        // ── 软清理 ────────────────────────────────────────────────────────────

        private void runSoftReset(Path home) {
            System.out.println("\033[36m┌─────────────────────────────────────────┐\033[0m");
            System.out.println("\033[36m│         🧹 软清理 (Soft Reset)           │\033[0m");
            System.out.println("\033[36m└─────────────────────────────────────────┘\033[0m");
            System.out.println();
            System.out.println("  将删除以下内容：");
            System.out.println("\033[90m  • ~/.openbe/hives/     — 所有蜂巢记忆刻录（SOUL.md、MEMORY.md 等）\033[0m");
            System.out.println("\033[90m  • ~/.openbe/logs/      — 运行日志\033[0m");
            System.out.println("\033[90m  • ~/.openbe/system/    — 系统临时状态\033[0m");
            System.out.println("\033[90m  • ~/.openbe/*.log      — 根目录散落日志\033[0m");
            System.out.println("\033[90m  • ~/.openbe/*.pid      — 进程 ID 文件\033[0m");
            System.out.println("\033[90m  • ~/.openbe/bees-registry.json — 蜜蜂注册表\033[0m");
            System.out.println();
            System.out.println("  将保留以下内容：");
            System.out.println("\033[32m  ✓ ~/.openbe/config/    — API Key 与全局配置\033[0m");
            System.out.println("\033[32m  ✓ ~/.openbe/stingers/  — 蜂刺脚本库\033[0m");
            System.out.println("\033[32m  ✓ ~/.openbe/library/   — 资产库\033[0m");
            System.out.println("\033[32m  ✓ ~/.openbe/*.jar      — 可执行程序\033[0m");
            System.out.println();

            if (!confirm("\033[33m⚠️  这将永久删除所有蜂巢记忆和蜜蜂状态数据。是否继续？[y/N]\033[0m ")) {
                System.out.println("\033[90m  已取消。\033[0m");
                return;
            }

            System.out.println();
            stopQueenGracefully();
            System.out.println();

            long[] deleted = { 0 };

            // 删除指定目录
            for (String dir : SOFT_DIRS) {
                Path target = home.resolve(dir);
                if (Files.exists(target)) {
                    spin("删除 ~/.openbe/" + dir + "/");
                    deleted[0] += deleteRecursive(target);
                    System.out.println("\r\033[32m  ✓\033[0m 已删除 ~/.openbe/" + dir + "/              ");
                }
            }

            // 删除散落文件（*.log, *.pid, bees-registry.json）
            for (String glob : SOFT_GLOBS) {
                try (var stream = Files.list(home)) {
                    stream.filter(p -> !Files.isDirectory(p) && matchGlob(p.getFileName().toString(), glob))
                          .forEach(p -> {
                              try {
                                  spin("删除 " + p.getFileName());
                                  Files.delete(p);
                                  deleted[0]++;
                                  System.out.println("\r\033[32m  ✓\033[0m 已删除 " + p.getFileName() + "                  ");
                              } catch (IOException e) {
                                  System.err.println("\r\033[31m  ✗\033[0m 删除失败: " + p.getFileName() + " — " + e.getMessage());
                              }
                          });
                } catch (IOException ignored) {}
            }

            printSuccess("软清理完成，共删除 " + deleted[0] + " 个文件/目录。\nAPI Key 与蜂刺脚本已保留，可直接 `openbe start` 重启蜂巢。");
        }

        // ── 硬清理 ────────────────────────────────────────────────────────────

        private void runHardReset(Path home) {
            System.out.println("\033[31m╔═════════════════════════════════════════╗\033[0m");
            System.out.println("\033[31m║     💣  硬核清理 (Hard Reset / NUKE)    ║\033[0m");
            System.out.println("\033[31m╚═════════════════════════════════════════╝\033[0m");
            System.out.println();
            System.out.println("\033[31m  ⚠️  高危操作警告 ⚠️\033[0m");
            System.out.println("\033[31m  这将彻底删除 ~/.openbe 中的所有内容，包括：\033[0m");
            System.out.println("\033[90m  • 全部蜂巢数据和蜜蜂记忆\033[0m");
            System.out.println("\033[90m  • 所有 API Key 配置文件\033[0m");
            System.out.println("\033[90m  • 所有蜂刺脚本\033[0m");
            System.out.println("\033[90m  • 所有可执行 jar 包\033[0m");
            System.out.println("\033[31m  此操作不可逆，系统将回到 install.sh 初始白板状态。\033[0m");
            System.out.println();

            if (!confirm("\033[31m💀 确认要核弹清除所有数据吗？请输入大写 YES 继续：\033[0m ")) {
                System.out.println("\033[90m  已取消。\033[0m");
                return;
            }

            System.out.println();
            stopQueenGracefully();
            System.out.println();

            spin("正在销毁 ~/.openbe/");
            long deleted = deleteRecursive(home);
            System.out.println("\r\033[32m  ✓\033[0m 已销毁 ~/.openbe（共 " + deleted + " 个项目）                ");

            printSuccess("硬核清理完成。\nOpenBe 已回到出厂白板状态。\n重新运行 install.sh 可重新部署蜂巢。");
        }

        // ── 工具 ──────────────────────────────────────────────────────────────

        private boolean confirm(String prompt) {
            if (yes) {
                System.out.println(prompt + "\033[90m（已通过 --yes 自动确认）\033[0m");
                return true;
            }
            System.out.print(prompt);
            try {
                java.io.Console console = System.console();
                String input;
                if (console != null) {
                    input = console.readLine();
                } else {
                    input = new java.util.Scanner(System.in).nextLine();
                }
                if (input == null) return false;
                // 硬清理要求大写 YES；软清理接受 y / Y
                if (hard) return "YES".equals(input.trim());
                return input.trim().equalsIgnoreCase("y");
            } catch (Exception e) {
                return false;
            }
        }

        private void stopQueenGracefully() {
            Path pidPath = Paths.get(PID_FILE);
            if (!Files.exists(pidPath)) return;
            try {
                long pid = Long.parseLong(Files.readString(pidPath).trim());
                ProcessHandle.of(pid).ifPresent(ph -> {
                    System.out.print("  🛑 正在停止 Queen 进程（PID: " + pid + "）...");
                    ph.destroy();
                    System.out.println(" \033[32m已停止\033[0m");
                });
                Files.deleteIfExists(pidPath);
            } catch (Exception ignored) {}
        }

        private static final char[] SPINNER = { '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏' };
        private int spinIdx = 0;

        private void spin(String msg) {
            System.out.print("\r  \033[36m" + SPINNER[spinIdx % SPINNER.length] + "\033[0m " + msg + "...");
            spinIdx++;
            try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        /** 递归删除目录或文件，返回已删除条目数 */
        private long deleteRecursive(Path path) {
            long[] count = { 0 };
            if (!Files.exists(path)) return 0;
            try (var stream = Files.walk(path)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); count[0]++; } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
            return count[0];
        }

        /** 简单 glob 匹配（支持 * 通配符） */
        private boolean matchGlob(String name, String pattern) {
            if (!pattern.contains("*")) return name.equals(pattern);
            String[] parts = pattern.split("\\*", -1);
            if (parts.length == 2) {
                return name.startsWith(parts[0]) && name.endsWith(parts[1]);
            }
            return name.equals(pattern);
        }

        private void printBanner() {
            System.out.println();
            System.out.println("\033[33m  🐝 OpenBe 数据清理工具\033[0m");
            System.out.println("\033[90m  数据根目录：" + OPENBE_HOME + "\033[0m");
            System.out.println();
        }

        private void printSuccess(String msg) {
            System.out.println();
            System.out.println("\033[32m╔═════════════════════════════════════════╗\033[0m");
            System.out.println("\033[32m║  ✨ 清理完毕！                          ║\033[0m");
            System.out.println("\033[32m╚═════════════════════════════════════════╝\033[0m");
            for (String line : msg.split("\n")) {
                System.out.println("  \033[32m" + line + "\033[0m");
            }
            System.out.println();
        }
    }

    // ── onboard ───────────────────────────────────────────────────────────────
    @Command(name = "onboard", description = "在浏览器中打开 OpenBe 控制台（http://localhost:8080）")
    static class OnboardCommand implements Runnable {

        @CommandLine.Option(
            names = {"--port", "-p"}, description = "Queen 监听端口（默认 8080）", defaultValue = "8080")
        int port;

        @Override
        public void run() {
            String url = "http://localhost:" + port;
            System.out.println(BANNER);
            System.out.println("\033[36m🌐 正在打开 OpenBe 控制台...\033[0m");
            System.out.println("\033[90m   " + url + "\033[0m");
            System.out.println();

            // 检测操作系统，选择打开浏览器的命令
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] cmd;
            if (os.contains("mac")) {
                cmd = new String[]{ "open", url };
            } else if (os.contains("win")) {
                cmd = new String[]{ "cmd", "/c", "start", url };
            } else {
                cmd = new String[]{ "xdg-open", url };
            }

            try {
                new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
                System.out.println("\033[32m✓ 控制台已在浏览器中打开\033[0m");
                System.out.println("\033[33m──────────────────────────────────────────────\033[0m");
                System.out.println("  🐝 如果浏览器未自动打开，请手动访问：");
                System.out.println("     \033[4m" + url + "\033[0m");
                System.out.println("\033[33m──────────────────────────────────────────────\033[0m");
                System.out.println("\033[90m  提示：确保蜂巢已运行（openbe start）\033[0m");
            } catch (java.io.IOException e) {
                System.err.println("\033[31m✗ 无法自动打开浏览器：" + e.getMessage() + "\033[0m");
                System.err.println("\033[33m  请手动在浏览器中打开：" + url + "\033[0m");
            }
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    /**
     * 在指定目录执行 shell 命令。
     * @param printOutput true = 实时打印 stdout/stderr；false = 仅在失败时打印 stderr
     */
    private static boolean runCommand(String command, File workingDir, boolean printOutput) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (workingDir != null) pb.directory(workingDir);

            // 确保子进程能找到 docker / java 等命令（继承并补全 PATH）
            // 包含 OrbStack (~/.orbstack/bin) 和 Homebrew (Intel/Apple Silicon) 路径
            String home = System.getProperty("user.home");
            pb.environment().put("PATH",
                home + "/.orbstack/bin:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:"
                + pb.environment().getOrDefault("PATH", ""));

            pb.redirectErrorStream(true);          // stderr 合并到 stdout
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (printOutput) System.out.println("    " + line);
                    else             output.append(line).append("\n");
                }
            }

            boolean ok = process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0;
            if (!ok && !printOutput && !output.isEmpty()) {
                System.err.println("\033[31m  命令输出：\033[0m");
                output.toString().lines().forEach(l -> System.err.println("    " + l));
            }
            return ok;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("\033[31m  执行异常：" + e.getMessage() + "\033[0m");
            return false;
        }
    }

    /** 兼容 docker compose（v2 插件）和 docker-compose（v1 独立命令）*/
    private static String resolveDockerCompose() {
        // 先试 v2 插件
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "docker compose version");
            pb.environment().put("PATH",
                "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:"
                + pb.environment().getOrDefault("PATH", ""));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "docker compose";
            }
        } catch (Exception ignored) {}
        // 降级到 v1
        return "docker-compose";
    }

    /** 向后兼容的 runCommand（静默模式） */
    private static boolean runCommand(String command, File workingDir) {
        return runCommand(command, workingDir, false);
    }

    /** 尝试 TCP 连接检测服务是否存活 */
    private static boolean checkTcpPort(String host, int port, int timeoutMs) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 解析 docker-compose.yml 所在目录（openbe-workspace 根目录）*/
    private static File resolveWorkspaceDir() {
        // 优先从环境变量读取，其次使用脚本安装时写入的默认路径
        String home = System.getenv("OPENBE_WORKSPACE");
        if (home != null && !home.isBlank()) return new File(home);
        return new File(System.getProperty("user.home"), ".openbe/workspace");
    }

    /** 在常见路径搜索 openbe-queen fat-jar */
    private static Path findQueenJar() {
        String[] candidates = {
            resolveWorkspaceDir() + "/packages/openbe-queen/target/openbe-queen.jar",
            System.getProperty("user.home") + "/.openbe/openbe-queen.jar"
        };
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.exists(p)) return p;
        }
        return null;
    }
}
