package com.openbe.queen.web;

import com.openbe.common.BeeType;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import com.openbe.gateway.LaneQueueRouter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 紧急制动 API — 向 RED 车道广播自毁信令，所有蜜蜂收到后立即 System.exit(0)。
 */
@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "*")
public class EmergencyController {

    private final LaneQueueRouter      router;
    private final StringRedisTemplate  redis;

    public EmergencyController(LaneQueueRouter router, StringRedisTemplate redis) {
        this.router = router;
        this.redis  = redis;
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> emergencyStop() {
        String taskId = "emergency-" + UUID.randomUUID().toString().substring(0, 8);

        // 向 RED 车道广播：WORKER
        router.emitPheromone(Pheromone.builder()
            .taskId(taskId)
            .sourceBee(BeeType.QUEEN)
            .targetBee(BeeType.WORKER)
            .laneColor(LaneColor.RED)
            .payload("{\"command\":\"TERMINATE\"}")
            .build());

        // 向 RED 车道广播：NURSE
        router.emitPheromone(Pheromone.builder()
            .taskId(taskId)
            .sourceBee(BeeType.QUEEN)
            .targetBee(BeeType.NURSE)
            .laneColor(LaneColor.RED)
            .payload("{\"command\":\"TERMINATE\"}")
            .build());

        System.out.println("\033[31m╔══════════════════════════════════════════╗\033[0m");
        System.out.println("\033[31m║  🚨 紧急制动已触发！所有蜜蜂将自毁...    ║\033[0m");
        System.out.println("\033[31m╚══════════════════════════════════════════╝\033[0m");

        // Queen 自身延迟退出（给 HTTP 响应充足时间发送完毕）
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "emergency-self-destruct").start();

        return ResponseEntity.ok(Map.of(
            "status", "TERMINATING",
            "taskId", taskId,
            "message", "🚨 紧急制动已触发，所有蜜蜂将在 1.5 秒内终止"));
    }

    /**
     * 琥珀封存 — 清除全部 OpenBe 数据：
     *  1. 删除 Redis 中所有 openbe:* 前缀的 key
     *  2. 删除 ~/.openbe/config/ 目录（本地 JSON 配置）
     *  3. 删除 ~/.openbe/*.log 日志文件
     */
    @PostMapping("/wipe")
    public ResponseEntity<Map<String, Object>> amberSealWipe() {
        Map<String, Object> report = new LinkedHashMap<>();

        // ── 1. Redis Flush ──────────────────────────────────────
        try {
            Set<String> keys = redis.keys("openbe:*");
            long redisCount = 0;
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                redisCount = keys.size();
            }
            report.put("redisKeysDeleted", redisCount);
        } catch (Exception e) {
            report.put("redisError", e.getMessage());
        }

        // ── 2. ~/.openbe/config/ 目录 ───────────────────────────
        Path configDir = Paths.get(System.getProperty("user.home"), ".openbe", "config");
        report.put("configDir", deleteDir(configDir));

        // ── 3. ~/.openbe/*.log 文件 ─────────────────────────────
        Path openbeHome = Paths.get(System.getProperty("user.home"), ".openbe");
        long logsDeleted = 0;
        try (var stream = Files.list(openbeHome)) {
            logsDeleted = stream
                .filter(p -> p.getFileName().toString().endsWith(".log"))
                .mapToLong(p -> { try { Files.delete(p); return 1; } catch (Exception e) { return 0; } })
                .sum();
        } catch (Exception ignored) {}
        report.put("logsDeleted", logsDeleted);

        report.put("status", "WIPED");
        System.out.println("\033[33m╔══════════════════════════════════════════╗\033[0m");
        System.out.println("\033[33m║  ☢  琥珀封存已执行 — 蜂巢数据已清除      ║\033[0m");
        System.out.println("\033[33m╚══════════════════════════════════════════╝\033[0m");
        return ResponseEntity.ok(report);
    }

    private String deleteDir(Path dir) {
        if (!Files.exists(dir)) return "not_found";
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.delete(f); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.delete(d); return FileVisitResult.CONTINUE;
                }
            });
            return "deleted";
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }
}
