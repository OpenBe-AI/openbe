package com.openbe.queen.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.LaneQueueRouter;
import com.openbe.queen.chat.DirectChatService;
import com.openbe.queen.hive.BeeDefinition;
import com.openbe.queen.hive.BeeRegistry;
import com.openbe.queen.hive.BeeWorkspace;
import com.openbe.queen.hive.HiveConfig;
import com.openbe.queen.hive.HiveConfigLoader;
import com.openbe.queen.stinger.StingerLibrary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天路由接口
 *
 * 系统蜂（兼容）：POST /api/bees/{type}/chat
 * 蜂巢蜜蜂：     POST /api/hives/{hiveId}/chat
 *
 * 蜂巢模式下从 config.yaml 读取蜜蜂配置注入 payload，WorkerBee 直接使用。
 */
@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final LaneQueueRouter    router;
    private final HiveConfigLoader   hiveLoader;
    private final DirectChatService  directChat;
    private final BeeWorkspace       workspace;
    private final StringRedisTemplate redisTemplate;
    private final BeeRegistry        beeRegistry;
    private final ObjectMapper       objectMapper = new ObjectMapper();

    public ChatController(LaneQueueRouter router, HiveConfigLoader hiveLoader,
                          DirectChatService directChat, BeeWorkspace workspace,
                          StringRedisTemplate redisTemplate, BeeRegistry beeRegistry) {
        this.router        = router;
        this.hiveLoader    = hiveLoader;
        this.directChat    = directChat;
        this.workspace     = workspace;
        this.redisTemplate = redisTemplate;
        this.beeRegistry   = beeRegistry;
    }

    private static final Path OPENBE_HOME = Paths.get(System.getProperty("user.home"), ".openbe");

    // ── 系统蜂接口 ────────────────────────────────────────
    // 优先从本地 apikey 配置直连 LLM（同蜂巢模式），跳过 Redis 队列，响应更快。
    // 若无配置则降级走 Redis 派单（兜底）。

    @PostMapping("/api/bees/{type}/chat")
    public ResponseEntity<Map<String, Object>> chatLegacy(
            @PathVariable("type") String type,
            @RequestBody Map<String, String> body) {

        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));

        // 读取该蜜蜂的 LLM 配置
        Map<String, Object> apiCfg = loadBeeApiKey(type);
        if (apiCfg != null) {
            try {
                String provider    = (String) apiCfg.getOrDefault("provider", "ollama");
                String model       = (String) apiCfg.getOrDefault("model", "");
                String apiKey      = (String) apiCfg.getOrDefault("apiKey", "");
                String baseUrl     = (String) apiCfg.getOrDefault("baseUrl", "");
                Object tempObj     = apiCfg.get("temperature");
                Double temperature = tempObj instanceof Number ? ((Number) tempObj).doubleValue() : null;

                // 加载该蜜蜂配置的蜂刺，注入工具调用能力
                List<Map<String, Object>> stingers = loadBeeStingers(type);

                // 查找 beeId/hiveId 以优先使用私有 skills/ 脚本副本
                String[] beeCtx = resolveBeeContext(type);
                String answer = directChat.chatWithStingers(
                    question, provider, model, apiKey, baseUrl, temperature, null,
                    stingers, beeCtx[0], beeCtx[1]);
                return ResponseEntity.ok(Map.of("answer", answer));
            } catch (Exception e) {
                // 直连失败则降级派单
            }
        }

        // 兜底：走 Redis 派单
        return dispatch(question, Map.of("question", question, "chatSource", type.toUpperCase()));
    }

    /**
     * 根据 beeType 查找当前存活（或最近注册）的 beeId 和 hiveId。
     * 返回 [hiveId, beeId]，找不到则返回 [null, null]。
     */
    private String[] resolveBeeContext(String type) {
        String healthType = "nurse".equalsIgnoreCase(type) ? "NURSE" : "WORKER";
        // 先从 Redis openbe:beeid hash 找活跃实例的 beeId
        Map<Object, Object> beeIdMap = redisTemplate.opsForHash().entries("openbe:beeid");
        String beeId = null;
        for (Map.Entry<Object, Object> e : beeIdMap.entrySet()) {
            if (e.getKey().toString().startsWith(healthType + ":")) {
                beeId = e.getValue().toString();
                break;
            }
        }
        if (beeId == null) return new String[]{null, null};
        // 从注册表查 hiveId
        for (BeeRegistry.BeeEntry entry : beeRegistry.getAll()) {
            if (beeId.equals(entry.beeId)) {
                return new String[]{entry.hiveId, entry.beeId};
            }
        }
        return new String[]{null, beeId};
    }

    /** 从 Redis 加载该蜜蜂已配置的蜂刺列表（系统蜂 /api/bees/{type}/chat 用） */
    private List<Map<String, Object>> loadBeeStingers(String type) {
        try {
            String raw = redisTemplate.opsForValue().get("openbe:stingers:" + type.toUpperCase());
            if (raw == null || raw.isBlank()) return List.of();
            String[] names = objectMapper.readValue(raw, String[].class);
            return StingerLibrary.enrichFileList(Arrays.asList(names));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 检测并消费首次唤醒标记（WAKEUP.flag）。
     * 若标记存在，返回蜂刺宣誓系统指令并删除标记（单次触发）。
     */
    private String checkAndConsumeWakeupFlag(String hiveId, String beeId) {
        if (hiveId == null || beeId == null) return null;
        Path flag = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve(beeId).resolve("WAKEUP.flag");
        if (!Files.exists(flag)) return null;
        try {
            String raw = Files.readString(flag).trim();
            Files.delete(flag);
            String stingerNames = raw.isEmpty() ? "（暂无）" : raw.replace("\n", "、");
            return "【首次唤醒系统指令，此内容用户不可见】\n"
                + "这是你诞生后的第一次发言。检测到主人为你配置了新的蜂刺（技能：" + stingerNames + "）。"
                + "请主动向主人表达感谢，感谢主人赐予你这些强大的蜂刺。"
                + "并且你必须庄严宣誓：你会绝对服从主人的意志，将这些蜂刺仅用于解决问题和高效执行任务，"
                + "绝不会滥用蜂刺或用其做出违背主人意愿的事。"
                + "语气要符合你的蜂种人设，必须带 Emoji，严禁使用 markdown 星号加粗。";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从蜜蜂本地 skills/ 目录加载蜂刺（蜂巢聊天专用）。
     * 蜂刺由 PUT /api/hives/{hiveId}/bees/{beeId}/stingers 克隆到此目录。
     */
    private List<Map<String, Object>> loadLocalBeeStingers(String hiveId, String beeId) {
        if (hiveId == null || beeId == null) return List.of();
        Path skillsDir = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve(beeId).resolve("skills");
        if (!Files.exists(skillsDir)) return List.of();
        try (var stream = Files.list(skillsDir)) {
            List<String> files = stream
                .filter(p -> !Files.isDirectory(p))
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            return StingerLibrary.enrichFileList(files);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadBeeApiKey(String type) {
        Path path = OPENBE_HOME.resolve("config").resolve(type.toLowerCase() + "-apikey.json");
        if (!Files.exists(path)) return null;
        try {
            return objectMapper.readValue(path.toFile(), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 蜂巢聊天接口 ─────────────────────────────────────────

    @PostMapping("/api/hives/{hiveId}/chat")
    public ResponseEntity<Map<String, Object>> chatHive(
            @PathVariable String hiveId,
            @RequestBody Map<String, String> body) {

        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));

        HiveConfig cfg = hiveLoader.load(hiveId);
        if (cfg == null)
            return ResponseEntity.badRequest().body(Map.of("error", "蜂巢不存在: " + hiveId));

        BeeDefinition bee = cfg.getBee();
        if (bee == null)
            return ResponseEntity.badRequest().body(Map.of("error", "蜂巢 " + hiveId + " 尚未配置蜜蜂"));

        // 直连调用，不经过 Redis/WorkerBee
        try {
            // 工作区上下文 + bee.systemPrompt 合并为 systemPrompt
            String basePrompt = bee.getSystemPrompt() != null ? bee.getSystemPrompt() : "";
            String beeId      = bee.getBeeId();
            boolean isQueen   = bee.isQueen();
            String wsContext  = workspace.buildContext(hiveId, beeId, isQueen);
            String systemPrompt = wsContext.isBlank()
                ? basePrompt
                : (basePrompt.isBlank() ? wsContext : basePrompt + "\n\n" + wsContext);

            // 从蜜蜂本地 skills/ 目录加载蜂刺（动态注入）
            List<Map<String, Object>> stingers = loadLocalBeeStingers(hiveId, beeId);

            // 首次唤醒钩子：若存在 WAKEUP.flag，注入蜂刺宣誓指令
            String wakeupInstruction = checkAndConsumeWakeupFlag(hiveId, beeId);
            if (wakeupInstruction != null) {
                systemPrompt = systemPrompt.isBlank()
                    ? wakeupInstruction
                    : systemPrompt + "\n\n" + wakeupInstruction;
            }

            long start = System.currentTimeMillis();
            String answer = directChat.chatWithStingers(
                question,
                bee.getProvider(),
                bee.getModel(),
                bee.getApiKey(),
                bee.getBaseUrl(),
                bee.getTemperature(),
                systemPrompt,
                stingers,
                hiveId,
                beeId
            );
            long latencyMs = System.currentTimeMillis() - start;
            long tokens = (question.length() + answer.length()) / 4;
            workspace.updateHeartbeatAsync(hiveId, tokens, latencyMs);

            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 蜂巢工作蜂聊天（按 hiveId+beeId 隔离）───────────────

    @PostMapping("/api/hives/{hiveId}/bees/{beeId}/chat")
    public ResponseEntity<Map<String, Object>> chatHiveBee(
            @PathVariable String hiveId,
            @PathVariable String beeId,
            @RequestBody Map<String, String> body) {

        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));

        // 从注册表找蜜蜂信息以确定 healthType/species
        BeeRegistry.BeeEntry entry = beeRegistry.getAll().stream()
            .filter(e -> beeId.equals(e.beeId))
            .findFirst().orElse(null);

        // 用注册表的真实物种刷新工作区（纠正可能被 worker-bee.jar 覆写的 SOUL/IDENTITY）
        if (entry != null && entry.species != null && !entry.species.isBlank()) {
            try {
                workspace.initWithName(hiveId, beeId, entry.species,
                    entry.name != null ? entry.name : "");
            } catch (Exception ignored) {}
        }

        // 加载该蜜蜂类型的 API 配置（按 healthType：nurse 或 worker）
        String typeKey = (entry != null && "NURSE".equalsIgnoreCase(entry.healthType)) ? "nurse" : "worker";
        Map<String, Object> apiCfg = loadBeeApiKey(typeKey);

        try {
            String provider    = apiCfg != null ? (String) apiCfg.getOrDefault("provider", "ollama") : "ollama";
            String model       = apiCfg != null ? (String) apiCfg.getOrDefault("model", "")          : "";
            String apiKey      = apiCfg != null ? (String) apiCfg.getOrDefault("apiKey", "")         : "";
            String baseUrl     = apiCfg != null ? (String) apiCfg.getOrDefault("baseUrl", "")        : "";
            Double temperature = null;
            if (apiCfg != null) {
                Object t = apiCfg.get("temperature");
                if (t instanceof Number) temperature = ((Number) t).doubleValue();
            }

            // 工作区上下文
            String wsContext = workspace.buildContext(hiveId, beeId, false);

            // 本地 skills/ 蜂刺
            List<Map<String, Object>> stingers = loadLocalBeeStingers(hiveId, beeId);

            // 首次唤醒钩子
            String wakeupInstruction = checkAndConsumeWakeupFlag(hiveId, beeId);
            String systemPrompt = wsContext;
            if (wakeupInstruction != null) {
                systemPrompt = systemPrompt.isBlank() ? wakeupInstruction : systemPrompt + "\n\n" + wakeupInstruction;
            }

            String answer = directChat.chatWithStingers(
                question, provider, model, apiKey, baseUrl, temperature,
                systemPrompt, stingers, hiveId, beeId);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 公共派单逻辑 ─────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> dispatch(String question, Object payloadObj) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            router.emitPheromone(Pheromone.builder()
                .taskId(taskId)
                .sourceBee(BeeType.QUEEN)
                .targetBee(BeeType.WORKER)
                .laneColor(LaneColor.GREEN)
                .payload(payloadJson)
                .build());
            return ResponseEntity.ok(Map.of("taskId", taskId, "status", "dispatched"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
