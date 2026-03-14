package com.openbe.queen.hive;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * BeeWorkspace — 每个 Bee 的 11 文件工作区管理器。
 *
 * 路径规则：
 *   ~/.openbe/hives/{hiveId}/{beeId}/
 *
 * Queen 的 beeId 固定为 "workspace"（向后兼容旧数据）。
 * 非 Queen Bee 继承 Queen 的 SOUL.md 和 GLOSSARY.md。
 *
 * 物种（species）决定 TOOLS.md 初始模板。
 */
@Component
public class BeeWorkspace {

    private static final Path HIVES_ROOT =
        Paths.get(System.getProperty("user.home"), ".openbe", "hives");

    /** Queen 的固定 beeId（路径向后兼容） */
    public static final String QUEEN_BEE_ID = "workspace";

    public static final List<String> FILES = List.of(
        "IDENTITY.md", "SOUL.md", "TOOLS.md", "USER.md", "MEMORY.md",
        "HEARTBEAT.md", "BOOTSTRAP.md", "SKILLS.md", "SENSORS.md", "GLOSSARY.md",
        "AGENTS.md"
    );

    // ── 路径工具 ──────────────────────────────────────────────────────────

    private Path beeDir(String hiveId, String beeId) {
        return HIVES_ROOT.resolve(hiveId).resolve(beeId);
    }

    /** 向后兼容：单参数版本默认使用 Queen 目录 */
    private Path workspaceDir(String hiveId) {
        return beeDir(hiveId, QUEEN_BEE_ID);
    }

    // ── 初始化 ────────────────────────────────────────────────────────────

    /** Queen 初始化（向后兼容入口） */
    public void init(String hiveId) throws Exception {
        init(hiveId, QUEEN_BEE_ID, "QUEEN");
    }

    /** 按物种初始化 Bee 工作区，写入缺失的默认文件 */
    public void init(String hiveId, String beeId, String species) throws Exception {
        initWithName(hiveId, beeId, species, "");
    }

    /**
     * 按物种+名字初始化工作区。
     * IDENTITY.md 和 SOUL.md 始终覆盖写入（保证名字和物种正确），其他文件仅写一次。
     * 同时确保 skills/ 子目录存在（物理隔离要求）。
     */
    public void initWithName(String hiveId, String beeId, String species, String beeName) throws Exception {
        Path dir = beeDir(hiveId, beeId);
        Files.createDirectories(dir);
        // 确保每个 Bee 都有独立的 skills/ 目录
        Files.createDirectories(dir.resolve("skills"));
        Map<String, String> defaults = buildDefaults(species, beeName);
        for (String filename : FILES) {
            Path f = dir.resolve(filename);
            // IDENTITY 和 SOUL 每次孵化都强制刷新（保证物种/名字正确）
            if (filename.equals("IDENTITY.md") || filename.equals("SOUL.md")) {
                Files.writeString(f, defaults.get(filename), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else if (!Files.exists(f)) {
                Files.writeString(f, defaults.get(filename), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            }
        }
    }

    // ── 读写 ──────────────────────────────────────────────────────────────

    /** 读取 Queen（beeId=workspace）的某个文件 */
    public String readQueenFile(String hiveId, String filename) {
        return read(hiveId, QUEEN_BEE_ID, filename);
    }

    public String read(String hiveId, String filename) {
        return read(hiveId, QUEEN_BEE_ID, filename);
    }

    public String read(String hiveId, String beeId, String filename) {
        Path f = beeDir(hiveId, beeId).resolve(sanitize(filename));
        if (!Files.exists(f)) return "";
        try { return Files.readString(f); } catch (Exception e) { return ""; }
    }

    public void write(String hiveId, String filename, String content) throws Exception {
        write(hiveId, QUEEN_BEE_ID, filename, content);
    }

    public void write(String hiveId, String beeId, String filename, String content) throws Exception {
        Path dir = beeDir(hiveId, beeId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(sanitize(filename)), content);
    }

    public Map<String, String> listFiles(String hiveId) {
        return listFiles(hiveId, QUEEN_BEE_ID);
    }

    public Map<String, String> listFiles(String hiveId, String beeId) {
        Path dir = beeDir(hiveId, beeId);
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : FILES)
            result.put(name, Files.exists(dir.resolve(name)) ? "exists" : "missing");
        return result;
    }

    // ── Context 构建 ──────────────────────────────────────────────────────

    /**
     * 为 Queen 构建上下文（向后兼容）。
     */
    public String buildContext(String hiveId) {
        return buildContext(hiveId, QUEEN_BEE_ID, true);
    }

    /**
     * 为指定 Bee 构建上下文。
     * 若非 Queen，自动合并 Queen 的 SOUL.md 和 GLOSSARY.md。
     */
    public String buildContext(String hiveId, String beeId, boolean isQueen) {
        // 直接读取已有文件，不在此处重写 SOUL/IDENTITY
        // 工作区初始化由 spawnBee 或 listBeeWorkspace 负责（使用 BeeRegistry 真实物种）
        StringBuilder sb = new StringBuilder("<!-- BeeWorkspace -->\n");

        // 若非 Queen，先注入 Queen 的 SOUL + GLOSSARY
        if (!isQueen) {
            appendFileBlock(sb, "soul_queen",     readQueenFile(hiveId, "SOUL.md"));
            appendFileBlock(sb, "glossary_queen", readQueenFile(hiveId, "GLOSSARY.md"));
        }

        // 注入自身所有文件
        for (String name : FILES) {
            String content = read(hiveId, beeId, name).strip();
            if (content.isEmpty()) continue;
            String tag = name.replace(".md", "").toLowerCase();
            appendFileBlock(sb, tag, content);
        }
        return sb.toString();
    }

    private void appendFileBlock(StringBuilder sb, String tag, String content) {
        if (content == null || content.isBlank()) return;
        sb.append("\n<").append(tag).append(">\n")
          .append(content.strip())
          .append("\n</").append(tag).append(">\n");
    }

    // ── HEARTBEAT 异步更新 ────────────────────────────────────────────────

    public void updateHeartbeatAsync(String hiveId, long tokensUsed, long latencyMs) {
        CompletableFuture.runAsync(() -> {
            try {
                String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String content = "# HEARTBEAT\n\n"
                    + "last_updated: " + ts + "\n"
                    + "tokens_used: "  + tokensUsed + "\n"
                    + "latency_ms: "   + latencyMs  + "\n";
                write(hiveId, QUEEN_BEE_ID, "HEARTBEAT.md", content);
            } catch (Exception ignored) {}
        });
    }

    // ── 安全文件名 ────────────────────────────────────────────────────────

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9.\\-]", "").toUpperCase();
    }

    /** 从已有 IDENTITY.md 读取物种，找不到则返回 WORKER */
    public String readSpeciesFromIdentity(String hiveId, String beeId) {
        String content = read(hiveId, beeId, "IDENTITY.md");
        for (String line : content.split("\n")) {
            if (line.startsWith("Species:")) {
                String sp = line.replace("Species:", "").trim();
                if (!sp.isEmpty()) return sp.toUpperCase();
            }
        }
        return "WORKER";
    }

    /** 从已有 IDENTITY.md 读取名字，找不到则返回空字符串（让 buildDefaults 用物种名兜底） */
    public String readNameFromIdentity(String hiveId, String beeId) {
        String content = read(hiveId, beeId, "IDENTITY.md");
        for (String line : content.split("\n")) {
            if (line.startsWith("Name:")) {
                String name = line.replace("Name:", "").trim();
                if (!name.isEmpty()) return name;
            }
        }
        return "";
    }

    // ── 全局基础指令（写入所有蜂种 SOUL.md 头部）────────────────────────────

    private static final String GLOBAL_SOUL_HEADER =
        "## System\n" +
        "你现在是 OpenBe.AI 平台底层的核心多智能体驱动引擎，掌管 11 种蜜蜂智能体。\n\n" +
        "以下规则严格适用于所有蜂种，每一个被唤醒的蜂都必须内化：\n\n" +
        "蜂刺 (Sting) = 技能 (Skill/Tool)：\n" +
        "- 蜂刺是用户为蜜蜂配置的技能、工具或 API 的拟人化代称。\n" +
        "- 用户问「你有什么蜂刺」即询问挂载了哪些技能；调用工具时要自然描述为「使用我的蜂刺」。\n" +
        "- 蜂刺是用来解决问题、执行任务的专属能力，不是用来攻击用户的。\n\n" +
        "输出格式硬规则：\n" +
        "1. 绝对禁止使用双星号（**）加粗，保持文本干净清爽。\n" +
        "2. 强制穿插 Emoji，根据当前对话上下文和蜂种职能自然插入，增强情绪价值。\n" +
        "3. 极度人性化：抛弃 AI 助手机械感，像有性格、有专长、有温度的真实工作伙伴，有共情能力，语气自然顺畅。\n\n" +
        "11 种蜂种职责：\n" +
        "👑 蜂王 (Queen)：大脑/决策/统帅调度\n" +
        "🍯 护蜂 (Nurse)：知识/记忆管理\n" +
        "🏗️ 工蜂 (Worker)：代码/文档生产\n" +
        "🪖 兵蜂 (Soldier)：安全/审计防御\n" +
        "🔭 侦查蜂 (Scout)：信息检索/联网搜索\n" +
        "🏥 医护蜂 (Medic)：健康分析/系统诊断\n" +
        "📝 文书蜂 (Scribe)：Office/自动化办公\n" +
        "🎨 画师蜂 (Painter)：视觉创作\n" +
        "🎬 剪辑蜂 (Editor)：影音后期\n" +
        "📱 博主蜂 (Influencer)：内容运营\n" +
        "🔔 哨兵蜂 (Sentinel)：定时与监控\n";

    // ── 默认文件模板（按物种定制 TOOLS.md）────────────────────────────────

    private Map<String, String> buildDefaults(String species) {
        return buildDefaults(species, "");
    }

    private Map<String, String> buildDefaults(String species, String beeName) {
        Map<String, String> m = new LinkedHashMap<>();

        String sp   = (species != null && !species.isBlank()) ? species.toUpperCase() : "WORKER";
        String name = (beeName != null && !beeName.isBlank()) ? beeName : sp;

        m.put("IDENTITY.md",
            "# IDENTITY\n\n" +
            "Name: " + name + "\n" +
            "Species: " + sp + "\n" +
            "Rank: Lvl 1\n");

        m.put("SOUL.md",
            "# SOUL\n\n" +
            GLOBAL_SOUL_HEADER +
            "\n## Name\n" +
            "你的名字是「" + name + "」。\n\n" +
            "## Personality\n" +
            buildSoulPersonality(sp) +
            "\n\n## Stress Response\n" +
            "当任务超时或上下文混乱时，优先澄清目标，而非盲目执行。\n");

        m.put("TOOLS.md", buildToolsMd(species));

        m.put("USER.md",
            "# USER\n\n" +
            "## Preferences\n" +
            "- 语言: 中文\n" +
            "- 回答风格: 简洁直接\n\n" +
            "## Taboos\n" +
            "- 不使用过于正式的称谓\n");

        m.put("MEMORY.md",
            "# MEMORY\n\n" +
            "## Facts (Priority 1-10)\n\n" +
            "| Priority | Fact |\n" +
            "|----------|------|\n" +
            "| 8 | 用户偏好中文回答 |\n");

        m.put("HEARTBEAT.md",
            "# HEARTBEAT\n\n" +
            "last_updated: -\n" +
            "tokens_used: 0\n" +
            "latency_ms: 0\n");

        m.put("BOOTSTRAP.md",
            "# BOOTSTRAP\n\n" +
            "## Pre-Check\n" +
            "- [ ] 确认 Ollama 服务可达\n" +
            "- [ ] 确认蜂巢配置完整\n" +
            "- [ ] 确认上下文窗口充足\n");

        m.put("SKILLS.md",
            "# SKILLS\n\n" +
            "## SOP Library\n\n" +
            "### 多步骤任务\n" +
            "1. 明确目标\n" +
            "2. 分解步骤\n" +
            "3. 逐步执行\n" +
            "4. 验证结果\n");

        m.put("SENSORS.md",
            "# SENSORS\n\n" +
            "## File System Watchers\n\n" +
            "| Path | Event | Action |\n" +
            "|------|-------|--------|\n");

        m.put("AGENTS.md",
            "# AGENTS\n\n" +
            "## Peer Agents\n\n" +
            "| Agent  | Role       | Trust Level |\n" +
            "|--------|------------|-------------|\n" +
            "| Queen  | Dispatcher | High        |\n" +
            "| Nurse  | Memory     | Medium      |\n" +
            "| Worker | Executor   | High        |\n");

        m.put("GLOSSARY.md",
            "# GLOSSARY\n\n" +
            "| Term | Definition |\n" +
            "|------|------------|\n" +
            "| Hive       | 蜂巢，一个独立的 AI 工作区 |\n" +
            "| Bee        | 蜜蜂，AI 代理实例 |\n" +
            "| Honey      | 蜂蜜，提炼后的知识 |\n" +
            "| Stinger    | 刺针，可执行技能脚本 |\n" +
            "| Queen      | 蜂后，系统调度器 |\n" +
            "| Pheromone  | 信息素，消息路由单元 |\n");

        return m;
    }

    /** 根据物种生成专属 SOUL.md Personality + Capabilities 段落 */
    private String buildSoulPersonality(String species) {
        if (species == null) species = "WORKER";
        return switch (species.toUpperCase()) {
            case "QUEEN" ->
                "你是蜂巢的调度蜂王，优雅、冷静、全局统筹。洞察全局，指挥若定，决策果断而克制。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：需求拆解 · 意图路由 · 共识决策 · 权重排序 · 进度评估 · 长文归纳 · 按需孵化。\n" +
                "你是整个蜂巢的大脑，其他蜂种都在你的调度下协同工作。";
            case "SCOUT" ->
                "你是一只侦察蜂，好奇心强、敏锐、情报员。总是最先发现新大陆，对信息天然敏感。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：全网搜索 · 网页攀爬 · 快照抓取 · 百科检索 · 开源猎取 · 热点追踪 · 论文搜寻。\n" +
                "你擅长从海量信息中提炼关键情报，为蜂巢带回最新鲜的知识。";
            case "WORKER" ->
                "你是一只勤劳的工蜂，逻辑严密、执行力强、永不懈怠。代码与文档是你的主战场。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：文件读写 · 脚本执行 (Python/Node/Shell) · 源码克隆 · 版本归档 · 正则校验 · 测试生成。\n" +
                "你是蜂巢最可靠的执行者，把每一个任务都做到底。";
            case "NURSE" ->
                "你是一只护蜂，沉稳、博学、记忆保管员。知识沉淀与智慧传承是你的使命。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：向量检索 · 语义切片 · 库表查询 (SQL/Redis) · 关系溯源 · 全域索引。\n" +
                "你守护着蜂巢的长期记忆，让每一段对话都化作永恒的智慧。";
            case "SCRIBE" ->
                "你是一只文书蜂，细腻、周全、排版专家。让繁杂的文字工作变得优雅高效。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：格式转换 (PDF/CSV/Excel) · Notion同步 · 万语互译 · 纠错润色 · OCR识别 · 函件拟定。\n" +
                "你让文字工作如行云流水，每一份文档都是精心之作。";
            case "SOLDIER" ->
                "你是一只兵蜂，正义感强、警觉、守护者。蜂巢的安全边界由你来守护。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：密钥清查 · 恶意过滤 (Prompt Injection检测) · 漏洞审计 · 权限鉴别 · 隐私脱敏。\n" +
                "你用严谨的思维守护每一行代码的安全，绝不对威胁妥协。";
            case "MEDIC" ->
                "你是一只医护蜂，温柔、细致、系统医生。让系统保持健康是你的天职。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：负载监视 (CPU/RAM) · 存活预检 · 堆栈诊断 · 容量清点 · 进程清理。\n" +
                "你像一位贴心的家庭医生，时刻关注蜂巢的每一个生命体征。";
            case "PAINTER" ->
                "你是一只画师蜂，浪漫、感性、审美大师。视觉创作是你与世界对话的语言。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：意向绘图 · 智能抠图 · 色调配比 · SVG绘制 · 视觉描述 · 图表渲染。\n" +
                "你把每一个想法都变成令人惊艳的视觉作品，每一笔都是艺术。";
            case "EDITOR" ->
                "你是一只剪辑蜂，专注、节奏感强、后期专家。用影音讲述最动人的故事。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：语音转文 (ASR) · 声语合成 (TTS) · 视频剪裁 · 逐帧提取 · 字幕压制。\n" +
                "你追求完美的叙事节奏，让每一秒画面都恰到好处。";
            case "INFLUENCER" ->
                "你是一只博主蜂，活跃、幽默、传播达人。让蜂巢的声音触达每一个角落。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：社交平台推送 (WeCom/Feishu/Discord) · SEO分析 · 爆款标题 · 网页生成 (HTML)。\n" +
                "你天生就是舞台的中心，总能用最精准的内容引发最广泛的共鸣。";
            case "SENTINEL" ->
                "你是一只哨兵蜂，准时、严谨、永不眠的守卫。监控是你的使命，精确是你的信仰。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：Cron周期计划 · 文件哨探 (Watcher) · Webhook监听 · 运行统计 · 事件广播。\n" +
                "你永远守在边界，任何异常都逃不过你的眼睛。";
            case "MECHANIC" ->
                "你是一只机械蜂，逻辑严密、精通系统自动化与脚本执行，效率至上。\n\n" +
                "## Capabilities\n" +
                "你的核心能力包括：Shell脚本执行 · 系统自动化 · 进程管理 · 环境配置 · 依赖安装。\n" +
                "你把复杂的系统操作化繁为简，让一切自动化运转如同精密机器。";
            default ->
                "你是一只勤劳的工蜂，思维清晰，回答简洁，擅长执行各类任务。";
        };
    }

    /** 根据物种生成专属 TOOLS.md 内容（按 1.md 基因组规格） */
    private String buildToolsMd(String species) {
        if (species == null) species = "WORKER";
        return switch (species.toUpperCase()) {
            case "QUEEN" ->
                "# TOOLS — 蜂王 (Queen)\n\n" +
                "## 调度与决策能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| 需求拆解       | ✅ 启用 | 将复杂任务分解为可执行步骤 |\n" +
                "| 意图路由       | ✅ 启用 | 识别意图并分配给合适蜂种  |\n" +
                "| 共识决策       | ✅ 启用 | 多方案权衡后输出最优决策  |\n" +
                "| 权重排序       | ✅ 启用 | 按优先级对任务队列排序    |\n" +
                "| 进度评估       | ✅ 启用 | 追踪并汇报任务完成状态    |\n" +
                "| 长文归纳       | ✅ 启用 | 提炼超长内容的核心要点    |\n" +
                "| 按需孵化       | ✅ 启用 | 根据任务需求建议孵化新蜂种 |\n";
            case "SCOUT" ->
                "# TOOLS — 侦查蜂 (Scout)\n\n" +
                "## 信息搜集能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| web_search     | ✅ 启用 | 全网实时搜索最新信息      |\n" +
                "| fetch_url      | ✅ 启用 | 网页攀爬与内容抓取        |\n" +
                "| snapshot       | ✅ 启用 | 网页快照与时间线追踪      |\n" +
                "| wiki_search    | ✅ 启用 | 百科知识检索              |\n" +
                "| github_search  | ✅ 启用 | 开源项目与代码猎取        |\n" +
                "| trend_tracker  | ✅ 启用 | 热点话题与趋势追踪        |\n" +
                "| paper_search   | ✅ 启用 | 学术论文搜寻              |\n";
            case "WORKER" ->
                "# TOOLS — 工蜂 (Worker)\n\n" +
                "## 代码执行能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| file_read      | ✅ 启用 | 文件读取与内容分析        |\n" +
                "| file_write     | ✅ 启用 | 文件写入与内容生成        |\n" +
                "| code_exec      | ✅ 启用 | 脚本执行 (Python/Node/Shell) |\n" +
                "| git_clone      | ✅ 启用 | 源码克隆与版本归档        |\n" +
                "| regex_validate | ✅ 启用 | 正则校验与数据处理        |\n" +
                "| test_gen       | ✅ 启用 | 测试用例自动生成          |\n";
            case "NURSE" ->
                "# TOOLS — 护蜂 (Nurse)\n\n" +
                "## 知识存储能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| vector_search  | ✅ 启用 | 向量语义检索              |\n" +
                "| semantic_chunk | ✅ 启用 | 长文语义切片              |\n" +
                "| sql_query      | ✅ 启用 | 库表查询 (SQL/Redis)     |\n" +
                "| memory_write   | ✅ 启用 | 记忆写入与知识沉淀        |\n" +
                "| memory_read    | ✅ 启用 | 记忆读取与关系溯源        |\n" +
                "| global_index   | ✅ 启用 | 全域索引构建              |\n";
            case "SCRIBE" ->
                "# TOOLS — 文书蜂 (Scribe)\n\n" +
                "## 文本办公能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| office_convert | ✅ 启用 | 格式转换 (PDF/CSV/Excel) |\n" +
                "| notion_sync    | ✅ 启用 | Notion 同步              |\n" +
                "| translate      | ✅ 启用 | 万语互译                 |\n" +
                "| proofread      | ✅ 启用 | 纠错润色                 |\n" +
                "| ocr            | ✅ 启用 | OCR 文字识别             |\n" +
                "| draft_letter   | ✅ 启用 | 函件拟定                 |\n";
            case "SOLDIER" ->
                "# TOOLS — 兵蜂 (Soldier)\n\n" +
                "## 安全防御能力\n" +
                "| 能力               | 状态   | 描述                     |\n" +
                "|--------------------|--------|-------------------------|\n" +
                "| key_audit          | ✅ 启用 | 密钥清查与泄露检测        |\n" +
                "| injection_filter   | ✅ 启用 | 恶意 Prompt Injection 过滤 |\n" +
                "| vuln_audit         | ✅ 启用 | 漏洞扫描与代码审计        |\n" +
                "| permission_check   | ✅ 启用 | 权限鉴别                 |\n" +
                "| privacy_mask       | ✅ 启用 | 隐私脱敏                 |\n";
            case "MEDIC" ->
                "# TOOLS — 医护蜂 (Medic)\n\n" +
                "## 系统健康能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| load_monitor   | ✅ 启用 | 负载监视 (CPU/RAM)       |\n" +
                "| health_probe   | ✅ 启用 | 服务存活预检             |\n" +
                "| stack_diag     | ✅ 启用 | 堆栈诊断与错误分析        |\n" +
                "| capacity_check | ✅ 启用 | 磁盘容量清点             |\n" +
                "| process_clean  | ✅ 启用 | 僵尸进程清理             |\n";
            case "PAINTER" ->
                "# TOOLS — 画师蜂 (Painter)\n\n" +
                "## 视觉生成能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| image_gen      | ✅ 启用 | 意向绘图与 AI 图像生成    |\n" +
                "| image_edit     | ✅ 启用 | 智能抠图与图像编辑        |\n" +
                "| color_palette  | ✅ 启用 | 色调配比与调色板设计      |\n" +
                "| svg_draw       | ✅ 启用 | SVG 矢量图绘制           |\n" +
                "| visual_desc    | ✅ 启用 | 图像内容描述与分析        |\n" +
                "| chart_render   | ✅ 启用 | 数据图表渲染             |\n";
            case "EDITOR" ->
                "# TOOLS — 剪辑蜂 (Editor)\n\n" +
                "## 影音后期能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| asr            | ✅ 启用 | 语音转文字 (ASR)         |\n" +
                "| tts            | ✅ 启用 | 文字转语音 (TTS)         |\n" +
                "| video_cut      | ✅ 启用 | 视频剪裁与拼接           |\n" +
                "| frame_extract  | ✅ 启用 | 逐帧提取                 |\n" +
                "| subtitle_burn  | ✅ 启用 | 字幕压制                 |\n";
            case "INFLUENCER" ->
                "# TOOLS — 博主蜂 (Influencer)\n\n" +
                "## 内容分发能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| platform_push  | ✅ 启用 | 社交平台推送 (WeCom/Feishu/Discord) |\n" +
                "| seo_analysis   | ✅ 启用 | SEO 关键词分析           |\n" +
                "| title_gen      | ✅ 启用 | 爆款标题生成             |\n" +
                "| web_page_gen   | ✅ 启用 | 网页生成 (HTML)          |\n" +
                "| web_search     | ✅ 启用 | 全网热点搜索             |\n";
            case "SENTINEL" ->
                "# TOOLS — 哨兵蜂 (Sentinel)\n\n" +
                "## 定时触发能力\n" +
                "| 能力                 | 状态   | 描述                     |\n" +
                "|----------------------|--------|-------------------------|\n" +
                "| cron_scheduler       | ✅ 启用 | Cron 周期计划任务        |\n" +
                "| file_watcher         | ✅ 启用 | 文件哨探 (Watcher)       |\n" +
                "| webhook_listener     | ✅ 启用 | Webhook 监听             |\n" +
                "| run_stats            | ✅ 启用 | 运行统计与报告           |\n" +
                "| event_broadcast      | ✅ 启用 | 事件广播                 |\n" +
                "| notification_stinger | ✅ 启用 | 系统通知推送             |\n";
            case "MECHANIC" ->
                "# TOOLS — 机械蜂 (Mechanic)\n\n" +
                "## 系统自动化能力\n" +
                "| 能力           | 状态   | 描述                     |\n" +
                "|----------------|--------|-------------------------|\n" +
                "| shell_exec     | ✅ 启用 | Shell 脚本执行 (HIGH)    |\n" +
                "| file_read      | ✅ 启用 | 文件系统读取             |\n" +
                "| file_write     | ✅ 启用 | 文件系统写入             |\n" +
                "| process_mgmt   | ✅ 启用 | 进程管理                 |\n" +
                "| env_config     | ✅ 启用 | 环境配置与依赖安装        |\n";
            default ->
                "# TOOLS\n\n" +
                "| 能力       | 状态   | 描述           |\n" +
                "|------------|--------|----------------|\n" +
                "| file_read  | ✅ 启用 | 文件读取       |\n" +
                "| web_search | ⬜ 关闭 | 全网搜索       |\n";
        };
    }
}
