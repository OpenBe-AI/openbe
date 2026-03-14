package com.openbe.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.BeeConfigReader;
import com.openbe.gateway.HealthReporter;
import com.openbe.gateway.LaneQueueRouter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工蜂服务 — 订阅 GREEN 车道，处理 WORKER 任务，调用 Ollama，结果回报蜂后。
 *
 * keepAlive 修复：onReady() 不再阻塞，keepAlive 由 WorkerBeeApplication.main() 负责。
 */
@Service
public class WorkerBeeService {

    private static final String TASK_LOCK_PREFIX = "openbe:task:lock:";
    private static final long   TASK_LOCK_TTL    = 10; // minutes

    private static final java.nio.file.Path CONFIG_DIR =
        Paths.get(System.getProperty("user.home"), ".openbe", "config");

    private static final File APIKEY_CFG =
        CONFIG_DIR.resolve("worker-apikey.json").toFile();

    private final LaneQueueRouter     router;
    private final OllamaClient        ollamaClient;
    private final ExternalApiClient   externalApiClient;
    private final StringRedisTemplate redisTemplate;
    private final HealthReporter      health;
    private final BeeConfigReader     configReader;
    private final ObjectMapper        objectMapper = new ObjectMapper();

    /** 独立线程池：Ollama 调用在此执行，不占用订阅回调线程 */
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "worker-ollama");
        t.setDaemon(false);
        return t;
    });

    private final AtomicLong    lastTaskTime    = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    public WorkerBeeService(LaneQueueRouter router,
                            OllamaClient ollamaClient,
                            ExternalApiClient externalApiClient,
                            StringRedisTemplate redisTemplate,
                            HealthReporter health,
                            BeeConfigReader configReader) {
        this.router            = router;
        this.ollamaClient      = ollamaClient;
        this.externalApiClient = externalApiClient;
        this.redisTemplate     = redisTemplate;
        this.health            = health;
        this.configReader      = configReader;
        // 构造函数中注册监听器，确保在 SmartLifecycle.start() 前完成注册
        router.listenLane(LaneColor.GREEN, this::handlePheromone);
        // RED 车道：紧急制动自毁
        router.listenLane(LaneColor.RED, p -> {
            System.out.println("\033[31m[WORKER] 🚨 收到紧急制动信号，执行自毁...\033[0m");
            System.exit(0);
        });
    }

    public long getLastTaskTime()  { return lastTaskTime.get(); }
    public boolean hasActiveTasks() { return activeTaskCount.get() > 0; }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println("\033[33m╔══════════════════════════════════════════════╗\033[0m");
        System.out.println("\033[33m║  🐝 工蜂已就绪，等待蜂后派发任务...         ║\033[0m");
        System.out.println("\033[33m╚══════════════════════════════════════════════╝\033[0m");
        System.out.println("\033[32m  ✓ 已订阅 GREEN 车道，持续监听中...\033[0m");
        health.setStatus("ONLINE");
        health.setExtra("activeTasks", "0");
        health.setExtra("ollamaAvailable", "unknown");
    }

    /** 订阅回调 — 只做申领，立即提交，不阻塞订阅线程 */
    private void handlePheromone(Pheromone pheromone) {
        if (pheromone.getTargetBee() != BeeType.WORKER) return;

        String taskId = pheromone.getTaskId();

        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
            TASK_LOCK_PREFIX + taskId, "1", TASK_LOCK_TTL, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(claimed)) {
            System.out.printf("\033[90m[WORKER] 任务已申领，跳过重复（taskId: %s）\033[0m%n", taskId);
            return;
        }

        System.out.printf("\033[36m[WORKER] 接单 -> taskId: %s, 来自: %s\033[0m%n",
            taskId, pheromone.getSourceBee());

        String payload       = pheromone.getPayload();
        String question      = extractField(payload, "question", payload);
        String modelOverride = extractField(payload, "model",    null);
        String chatSource    = extractField(payload, "chatSource", "WORKER");
        // Hive 模式：从 payload 直接读取完整 Bee 配置
        String provider      = extractField(payload, "provider",     null);
        String apiKey        = extractField(payload, "apiKey",       null);
        String baseUrl       = extractField(payload, "baseUrl",      "");
        String systemPrompt  = extractField(payload, "systemPrompt", null);
        String tempStr = extractField(payload, "temperature", null);
        Double tempVal = null;
        if (tempStr != null) { try { tempVal = Double.parseDouble(tempStr); } catch (Exception ignored) {} }
        final Double temperature = tempVal;

        int active = activeTaskCount.incrementAndGet();
        health.setStatus("BUSY");
        health.setExtra("activeTasks", String.valueOf(active));
        taskExecutor.submit(() -> processTask(pheromone, taskId, question, modelOverride,
            chatSource, provider, apiKey, baseUrl, systemPrompt, temperature));
    }

    private void processTask(Pheromone pheromone, String taskId,
                             String question, String modelOverride, String chatSource,
                             String payloadProvider, String payloadApiKey, String payloadBaseUrl,
                             String payloadSystemPrompt, Double payloadTemperature) {
        try {
            String answer;

            // Hive 模式：payload 携带完整配置，直接使用，跳过 Redis/文件查找
            boolean hiveMode = payloadProvider != null && !payloadProvider.isBlank()
                               && !"ollama".equalsIgnoreCase(payloadProvider);
            if (hiveMode) {
                System.out.printf("\033[36m[WORKER] Hive 模式（provider: %s），问题：%s\033[0m%n", payloadProvider, question);
                String finalModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride
                    : (payloadProvider.equals("ollama") ? configReader.getModel() : "");
                answer = externalApiClient.call(question, payloadProvider, finalModel,
                    payloadApiKey, payloadBaseUrl, payloadTemperature, payloadSystemPrompt);
            } else if (payloadProvider != null && "ollama".equalsIgnoreCase(payloadProvider)) {
                // Hive 模式但使用 Ollama
                String finalModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : configReader.getModel();
                Double finalTemp  = (payloadTemperature != null) ? payloadTemperature : configReader.getTemperature();
                String finalSys   = (payloadSystemPrompt != null && !payloadSystemPrompt.isBlank()) ? payloadSystemPrompt : configReader.getSystemPrompt();
                System.out.printf("\033[36m[WORKER] Hive 模式（Ollama），问题：%s\033[0m%n", question);
                answer = ollamaClient.generate(question, finalModel, finalTemp, finalSys);
            } else {
                // 传统模式：读取 chatSource 对应的 apikey.json 和 Redis 热调配置
                String configBee = (chatSource != null && !chatSource.isBlank()) ? chatSource.toUpperCase() : "WORKER";
                System.out.printf("\033[36m[WORKER] 接单（代理身份：%s），问题：%s\033[0m%n", configBee, question);

                String liveModel = configReader.getForType(configBee, "model");
                if (liveModel == null) liveModel = configReader.getModel();
                String tempStr   = configReader.getForType(configBee, "temperature");
                Double liveTemp  = null;
                if (tempStr != null) { try { liveTemp = Double.parseDouble(tempStr); } catch (Exception ignored) {} }
                if (liveTemp == null) liveTemp = configReader.getTemperature();
                String liveSys   = configReader.getForType(configBee, "systemPrompt");
                if (liveSys == null) liveSys = configReader.getSystemPrompt();

                String finalModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : liveModel;

                File apikeyFile = CONFIG_DIR.resolve(configBee.toLowerCase() + "-apikey.json").toFile();
                if (!apikeyFile.exists()) apikeyFile = APIKEY_CFG;

                answer = generateAnswer(question, finalModel, liveTemp, liveSys, apikeyFile);
            }
            health.setExtra("ollamaAvailable", "true");
            System.out.printf("\033[32m[WORKER] 回复完毕（taskId: %s）\033[0m%n", taskId);

            String resultPayload = buildResultPayload(taskId, question, answer);

            // 1. 汇报蜂后（GREEN）
            router.emitPheromone(Pheromone.builder()
                .taskId(taskId)
                .sourceBee(BeeType.WORKER)
                .targetBee(BeeType.QUEEN)
                .laneColor(LaneColor.GREEN)
                .payload(resultPayload)
                .build());

            // 2. 触发酿蜜（YELLOW → NURSE）
            System.out.printf("\033[33m[WORKER] 交接酿蜜 -> 护士蜂（taskId: %s）\033[0m%n", taskId);
            router.emitPheromone(Pheromone.builder()
                .taskId(taskId)
                .sourceBee(BeeType.WORKER)
                .targetBee(BeeType.NURSE)
                .laneColor(LaneColor.YELLOW)
                .payload(resultPayload)
                .build());

        } finally {
            lastTaskTime.set(System.currentTimeMillis());
            int remaining = activeTaskCount.decrementAndGet();
            health.setExtra("activeTasks", String.valueOf(remaining));
            if (remaining == 0) health.setStatus("ONLINE");
        }
    }

    /**
     * 路由选择：优先读取本地 apikey.json，若 provider 非 ollama 则调用外部 API。
     */
    @SuppressWarnings("unchecked")
    private String generateAnswer(String question, String model,
                                  Double temperature, String systemPrompt, File apikeyFile) {
        if (apikeyFile.exists()) {
            try {
                java.util.Map<String, Object> cfg = objectMapper.readValue(apikeyFile, java.util.Map.class);
                String provider = (String) cfg.getOrDefault("provider", "ollama");
                if (provider != null && !provider.isBlank() && !"ollama".equalsIgnoreCase(provider)) {
                    String apiKey   = (String) cfg.getOrDefault("apiKey",  "");
                    String baseUrl  = (String) cfg.getOrDefault("baseUrl", "");
                    String cfgModel = (String) cfg.getOrDefault("model",   "");
                    String useModel = (model != null && !model.isBlank()) ? model : cfgModel;
                    System.out.printf("\033[36m[WORKER] 路由到外部 API（%s / %s）\033[0m%n", provider, useModel);
                    return externalApiClient.call(question, provider, useModel, apiKey, baseUrl, temperature, systemPrompt);
                }
            } catch (Exception e) {
                System.out.printf("\033[31m[WORKER] 读取 apikey.json 失败，回退到 Ollama：%s\033[0m%n", e.getMessage());
            }
        }
        return ollamaClient.generate(question, model, temperature, systemPrompt);
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

    private String buildResultPayload(String taskId, String question, String answer) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("taskId",   taskId);
            node.put("question", question);
            node.put("answer",   answer);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"answer\":\"" + answer.replace("\"", "\\\"") + "\"}";
        }
    }
}
