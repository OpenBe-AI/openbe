package com.openbe.queen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.HealthReporter;
import com.openbe.gateway.LaneQueueRouter;
import com.openbe.queen.hive.HiveConfigLoader;
import com.openbe.queen.web.LogWebSocketHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * OpenBe Queen — 蜂后核心调度中心 + Dashboard 宿主
 *
 * 启用 Web（Tomcat 保活 JVM，无需 CountDownLatch）。
 * 监听器在构造函数中注册，确保在 SmartLifecycle.start() 前就绪。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.openbe.queen", "com.openbe.gateway"})
public class QueenApplication {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LaneQueueRouter     router;
    private final LogWebSocketHandler logWs;
    private final HealthReporter      health;
    private final HiveConfigLoader    hiveLoader;
    private final ObjectMapper        objectMapper = new ObjectMapper();

    public QueenApplication(LaneQueueRouter router,
                            LogWebSocketHandler logWs,
                            HealthReporter health,
                            HiveConfigLoader hiveLoader) {
        this.router     = router;
        this.logWs      = logWs;
        this.health     = health;
        this.hiveLoader = hiveLoader;

        // 在构造函数中注册监听器 — 早于 SmartLifecycle.start()
        router.listenLane(LaneColor.GREEN,  this::handleGreenLane);
        router.listenLane(LaneColor.YELLOW, this::handleYellowLane);
        router.listenLane(LaneColor.RED,    this::handleRedLane);
    }

    public static void main(String[] args) {
        SpringApplication.run(QueenApplication.class, args);
        // Tomcat 非守护线程保活 JVM，无需额外 CountDownLatch
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println("\033[33m╔══════════════════════════════════════════════╗\033[0m");
        System.out.println("\033[33m║  🐝 蜂后已就绪，开始监听信息素总线...       ║\033[0m");
        System.out.println("\033[33m╚══════════════════════════════════════════════╝\033[0m");
        System.out.println("\033[32m  ✓ 已订阅车道：GREEN | YELLOW\033[0m");
        System.out.println("\033[32m  ✓ Dashboard → http://localhost:8080\033[0m");

        health.setStatus("ONLINE");
    }

    // ── 绿色车道处理 ──────────────────────────────────────

    private void handleGreenLane(Pheromone pheromone) {
        String t = now();

        if (pheromone.getSourceBee() == BeeType.WORKER) {
            // Worker 汇报结果
            printWorkerResult(pheromone);
            broadcastLog("GREEN", "🍯",
                String.format("WORKER → QUEEN | taskId: %s | %s",
                    abbrev(pheromone.getTaskId()), previewAnswer(pheromone.getPayload())), t, "WORKER");
            // 广播完整结果供前端私聊面板接收
            String fullAnswer = extractFullAnswer(pheromone.getPayload());
            logWs.broadcast(String.format(
                "{\"type\":\"result\",\"taskId\":\"%s\",\"answer\":\"%s\",\"bee\":\"WORKER\"}",
                pheromone.getTaskId(), escJson(fullAnswer)));

        } else if (pheromone.getTargetBee() == BeeType.QUEEN
                   && pheromone.getSourceBee() != BeeType.QUEEN) {
            // 外部任务请求 → 转发给 Worker
            System.out.printf("\033[36m[QUEEN] 收到任务请求 -> 来自: %s, 任务ID: %s\033[0m%n",
                pheromone.getSourceBee(), pheromone.getTaskId());
            broadcastLog("GREEN", "📥",
                String.format("%s → QUEEN | taskId: %s | %s",
                    pheromone.getSourceBee(), abbrev(pheromone.getTaskId()), previewPayload(pheromone.getPayload())), t, "QUEEN");

            Pheromone task = Pheromone.builder()
                .taskId(pheromone.getTaskId())
                .sourceBee(BeeType.QUEEN)
                .targetBee(BeeType.WORKER)
                .laneColor(LaneColor.GREEN)
                .payload(pheromone.getPayload())
                .build();
            router.emitPheromone(task);
            broadcastLog("GREEN", "📤",
                String.format("QUEEN → WORKER | taskId: %s", abbrev(pheromone.getTaskId())), t, "QUEEN");

        } else {
            System.out.printf("\033[32m[QUEEN 收到信息素] -> 来自: %s, 车道: GREEN, 任务ID: %s\033[0m%n",
                pheromone.getSourceBee(), pheromone.getTaskId());
            if (!"boot-self-check".equals(pheromone.getTaskId())) {
                broadcastLog("GREEN", "💬",
                    String.format("%s | taskId: %s", pheromone.getSourceBee(), abbrev(pheromone.getTaskId())), t, "");
            }
        }
    }

    private void handleYellowLane(Pheromone pheromone) {
        String t = now();
        System.out.printf("\033[33m[QUEEN YELLOW] -> 来自: %s, 任务ID: %s\033[0m%n",
            pheromone.getSourceBee(), pheromone.getTaskId());
        broadcastLog("YELLOW", "🌸",
            String.format("%s → NURSE | taskId: %s | 酿蜜触发",
                pheromone.getSourceBee(), abbrev(pheromone.getTaskId())), t, "NURSE");
    }

    private void handleRedLane(Pheromone pheromone) {
        System.out.println("\033[31m[QUEEN RED] 🚨 收到紧急制动信号，执行自毁...\033[0m");
        logWs.broadcast("{\"type\":\"emergency\",\"msg\":\"🚨 紧急制动信号已触发，蜂后即将自毁\"}");
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "queen-self-destruct").start();
    }

    // ── 工具方法 ──────────────────────────────────────────

    private void broadcastLog(String lane, String icon, String msg, String time) {
        broadcastLog(lane, icon, msg, time, "");
    }

    private void broadcastLog(String lane, String icon, String msg, String time, String bee) {
        String json = String.format(
            "{\"lane\":\"%s\",\"icon\":\"%s\",\"msg\":\"%s\",\"time\":\"%s\",\"bee\":\"%s\"}",
            lane, icon, escJson(msg), time, bee);
        logWs.broadcast(json);
    }

    private String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    private String abbrev(String taskId) {
        if (taskId == null) return "?";
        return taskId.length() > 8 ? taskId.substring(0, 8) : taskId;
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private String previewPayload(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("question")) return node.get("question").asText().substring(0,
                Math.min(40, node.get("question").asText().length()));
        } catch (Exception ignored) {}
        if (payload == null) return "";
        return payload.length() > 40 ? payload.substring(0, 40) + "…" : payload;
    }

    private String extractFullAnswer(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("answer")) return node.get("answer").asText();
        } catch (Exception ignored) {}
        return payload != null ? payload : "";
    }

    private String previewAnswer(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("answer")) {
                String a = node.get("answer").asText();
                return a.length() > 40 ? a.substring(0, 40) + "…" : a;
            }
        } catch (Exception ignored) {}
        if (payload == null) return "";
        return payload.length() > 40 ? payload.substring(0, 40) + "…" : payload;
    }

    private void printWorkerResult(Pheromone pheromone) {
        String answer = pheromone.getPayload();
        try {
            JsonNode node = objectMapper.readTree(pheromone.getPayload());
            if (node.has("answer")) answer = node.get("answer").asText();
        } catch (Exception ignored) {}

        System.out.println("\033[35m╔══════════════════════════════════════════════╗\033[0m");
        System.out.printf( "\033[35m║  🍯 WORKER 汇报完毕 (taskId: %-15s)║\033[0m%n",
            pheromone.getTaskId().length() > 15
                ? pheromone.getTaskId().substring(0, 15) : pheromone.getTaskId());
        System.out.println("\033[35m╠══════════════════════════════════════════════╣\033[0m");
        for (String line : answer.split("\n")) {
            System.out.printf("\033[35m║  \033[0m%-44s\033[35m║\033[0m%n",
                line.length() > 44 ? line.substring(0, 44) : line);
        }
        System.out.println("\033[35m╚══════════════════════════════════════════════╝\033[0m");
    }
}
