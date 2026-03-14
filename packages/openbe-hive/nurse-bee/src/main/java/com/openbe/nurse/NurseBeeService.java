package com.openbe.nurse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.BeeConfigReader;
import com.openbe.gateway.HealthReporter;
import com.openbe.gateway.LaneQueueRouter;
import com.openbe.memory.SoulStorage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 护士蜂服务 — 监听 YELLOW（后台维护）车道，对 NURSE 目标信息素执行酿蜜操作：
 *  1. 从 payload 中提取 question + answer（Nectar 花蜜）
 *  2. 同步调用 Ollama 提炼为极简 JSON 知识蜜糖（Honey）
 *  3. 通过 SoulStorage 追加写入 ~/.openbe/workspace/soul.md
 *
 * keepAlive 修复：onReady() 不再阻塞，keepAlive 由 NurseBeeApplication.main() 负责。
 */
@Service
public class NurseBeeService {

    private static final File APIKEY_CFG =
        Paths.get(System.getProperty("user.home"), ".openbe", "config", "nurse-apikey.json").toFile();

    private static final Path MEMORY_FILE =
        Paths.get(System.getProperty("user.home"), ".openbe", "workspace", "MEMORY.md");

    private final LaneQueueRouter        router;
    private final NurseOllamaClient      ollamaClient;
    private final NurseExternalApiClient externalApiClient;
    private final SoulStorage            soulStorage;
    private final HealthReporter         health;
    private final BeeConfigReader        configReader;
    private final ObjectMapper           objectMapper = new ObjectMapper();

    private final java.util.concurrent.atomic.AtomicInteger honeyCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /** 独立线程池：酿蜜同步调用 Ollama，不占用订阅回调线程 */
    private final ExecutorService honeyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nurse-honey");
        t.setDaemon(false);
        return t;
    });

    public NurseBeeService(LaneQueueRouter router,
                           NurseOllamaClient ollamaClient,
                           NurseExternalApiClient externalApiClient,
                           SoulStorage soulStorage,
                           HealthReporter health,
                           BeeConfigReader configReader) {
        this.router            = router;
        this.ollamaClient      = ollamaClient;
        this.externalApiClient = externalApiClient;
        this.soulStorage       = soulStorage;
        this.health            = health;
        this.configReader      = configReader;
        // 构造函数中注册监听器，确保在 SmartLifecycle.start() 前完成注册
        router.listenLane(LaneColor.YELLOW, this::handlePheromone);
        // RED 车道：紧急制动自毁
        router.listenLane(LaneColor.RED, p -> {
            System.out.println("\033[31m[NURSE] 🚨 收到紧急制动信号，执行自毁...\033[0m");
            System.exit(0);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println("\033[35m╔══════════════════════════════════════════════╗\033[0m");
        System.out.println("\033[35m║  🌸 护士蜂已就绪，开始酿造知识蜜糖...       ║\033[0m");
        System.out.println("\033[35m╚══════════════════════════════════════════════╝\033[0m");
        System.out.println("\033[32m  ✓ 已订阅 YELLOW 车道，持续监听中...\033[0m");
        health.setStatus("ONLINE");
        health.setExtra("totalHoney", "0");
    }

    /** 订阅回调 — 只过滤+提交，立即返回，不阻塞订阅线程 */
    private void handlePheromone(Pheromone pheromone) {
        if (pheromone.getTargetBee() != BeeType.NURSE) return;

        System.out.printf("\033[35m[NURSE] 收到酿蜜请求 -> taskId: %s\033[0m%n",
            pheromone.getTaskId());

        health.setStatus("BUSY");
        honeyExecutor.submit(() -> distillAndStore(pheromone));
    }

    /**
     * 酿蜜核心逻辑（在独立线程同步执行）：
     *  - 从 payload 提取 Nectar（question + answer）
     *  - 调用 Ollama 同步蒸馏为 Honey（极简 JSON 知识点）
     *  - 写入 soul.md
     */
    private void distillAndStore(Pheromone pheromone) {
        String question = extractField(pheromone.getPayload(), "question", "未知问题");
        String answer   = extractField(pheromone.getPayload(), "answer",   "未知回答");

        System.out.printf("\033[35m[NURSE] 开始蒸馏 Nectar -> taskId: %s\033[0m%n",
            pheromone.getTaskId());
        System.out.printf("\033[35m[NURSE] 问题：%s\033[0m%n", question);

        // 读取热调配置，实时生效（零重启）
        String honey = distillAnswer(question, answer,
            configReader.getModel(), configReader.getTemperature(), configReader.getSystemPrompt());

        System.out.printf("\033[35m[NURSE] 蒸馏完成 -> Honey: %s\033[0m%n", honey);

        // 追加写入 soul.md
        soulStorage.append(honey);

        // 同步写入 MEMORY.md（系统工作区，供 Worker 查询）
        appendToMemory(question, honey);

        int total = honeyCount.incrementAndGet();
        health.setExtra("totalHoney", String.valueOf(total));
        health.setStatus("ONLINE");

        System.out.printf("\033[32m[NURSE] ✓ 知识已固化 soul.md + MEMORY.md（taskId: %s，累计: %d）\033[0m%n",
            pheromone.getTaskId(), total);
    }

    @SuppressWarnings("unchecked")
    private String distillAnswer(String question, String answer,
                                  String model, Double temperature, String systemPrompt) {
        if (APIKEY_CFG.exists()) {
            try {
                java.util.Map<String, Object> cfg = objectMapper.readValue(APIKEY_CFG, java.util.Map.class);
                String provider = (String) cfg.getOrDefault("provider", "ollama");
                if (provider != null && !provider.isBlank() && !"ollama".equalsIgnoreCase(provider)) {
                    String apiKey  = (String) cfg.getOrDefault("apiKey",  "");
                    String baseUrl = (String) cfg.getOrDefault("baseUrl", "");
                    String cfgModel = (String) cfg.getOrDefault("model",  "");
                    String useModel = (model != null && !model.isBlank()) ? model : cfgModel;
                    String sys = (systemPrompt != null && !systemPrompt.isBlank())
                        ? systemPrompt : NurseOllamaClient.DEFAULT_HONEY_SYSTEM;
                    String prompt = "问题：" + question + "\n\n回答：" + answer;
                    System.out.printf("\033[35m[NURSE] 路由到外部 API（%s / %s）\033[0m%n", provider, useModel);
                    return externalApiClient.call(prompt, provider, useModel, apiKey, baseUrl, temperature, sys);
                }
            } catch (Exception e) {
                System.out.printf("\033[31m[NURSE] 读取 apikey.json 失败，回退到 Ollama：%s\033[0m%n", e.getMessage());
            }
        }
        return ollamaClient.distill(question, answer, model, temperature, systemPrompt);
    }

    /** 将提炼结果追加到 ~/.openbe/workspace/MEMORY.md */
    private void appendToMemory(String question, String honey) {
        try {
            Files.createDirectories(MEMORY_FILE.getParent());
            String q = question.replace("|", "\\|");
            if (q.length() > 80) q = q.substring(0, 80) + "…";
            String h = honey.replace("|", "\\|");
            if (h.length() > 120) h = h.substring(0, 120) + "…";

            if (!Files.exists(MEMORY_FILE)) {
                Files.writeString(MEMORY_FILE,
                    "# MEMORY\n\n## Facts\n\n| Question | Honey |\n|----------|-------|\n");
            }
            Files.writeString(MEMORY_FILE,
                "| " + q + " | " + h + " |\n",
                StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private String extractField(String payload, String field, String fallback) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has(field) && !node.get(field).isNull()) {
                String val = node.get(field).asText();
                return val.isBlank() ? fallback : val;
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
