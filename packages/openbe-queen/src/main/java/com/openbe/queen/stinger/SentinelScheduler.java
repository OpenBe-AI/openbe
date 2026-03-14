package com.openbe.queen.stinger;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * SentinelScheduler — Sentinel 蜂的后台定时任务调度器。
 *
 * 支持：
 *  - POST /api/sentinel/schedule — 注册一个定时提醒
 *  - GET  /api/sentinel/jobs     — 列出所有待执行任务
 *  - DELETE /api/sentinel/jobs/{id} — 取消任务
 *
 * 到时间后通过 osascript 触发 macOS 原生通知。
 *
 * 任务存储在内存中（重启后丢失）。
 */
@Component
@RestController
@RequestMapping("/api/sentinel")
@CrossOrigin(origins = "*")
public class SentinelScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 待执行任务队列 */
    private final ConcurrentHashMap<String, SentinelJob> jobs = new ConcurrentHashMap<>();

    // ── REST API ──────────────────────────────────────────

    /**
     * 注册定时任务。
     * Body: { "title": "提醒标题", "message": "内容", "at": "2026-03-13 18:00" }
     */
    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> schedule(@RequestBody Map<String, String> body) {
        String title   = body.getOrDefault("title",   "OpenBe 提醒");
        String message = body.getOrDefault("message", "");
        String atStr   = body.getOrDefault("at",      "");

        if (atStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "at 字段（执行时间）不能为空"));
        }

        LocalDateTime at;
        try {
            at = LocalDateTime.parse(atStr, FMT);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "时间格式错误，应为 yyyy-MM-dd HH:mm"));
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        jobs.put(id, new SentinelJob(id, title, message, at));

        System.out.printf("\033[33m[Sentinel] 任务已注册 id=%s at=%s title=%s\033[0m%n", id, atStr, title);
        return ResponseEntity.ok(Map.of("id", id, "title", title, "at", atStr, "status", "scheduled"));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> listJobs() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SentinelJob j : jobs.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       j.id());
            m.put("title",    j.title());
            m.put("message",  j.message());
            m.put("at",       j.at().format(FMT));
            m.put("fired",    j.fired());
            result.add(m);
        }
        result.sort(Comparator.comparing(m -> m.get("at").toString()));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String id) {
        SentinelJob removed = jobs.remove(id);
        if (removed == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("status", "cancelled", "id", id));
    }

    // ── 调度核心：每分钟检查 ──────────────────────────────

    @Scheduled(fixedDelay = 30_000) // 每 30 秒检查一次
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (SentinelJob job : jobs.values()) {
            if (!job.fired() && !job.at().isAfter(now)) {
                fireJob(job);
            }
        }
        // 清理已触发超过 1 小时的任务
        jobs.entrySet().removeIf(e ->
            e.getValue().fired() && e.getValue().at().isBefore(now.minusHours(1)));
    }

    private void fireJob(SentinelJob job) {
        job.markFired();
        System.out.printf("\033[35m[Sentinel] 触发任务 id=%s title=%s\033[0m%n", job.id(), job.title());
        try {
            // macOS 原生通知
            String script = String.format(
                "display notification \"%s\" with title \"%s\" sound name \"Ping\"",
                escapeAppleScript(job.message()), escapeAppleScript(job.title()));
            new ProcessBuilder("osascript", "-e", script).start();
        } catch (Exception e) {
            System.err.printf("[Sentinel] 通知失败: %s%n", e.getMessage());
        }
    }

    private String escapeAppleScript(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── 任务实体 ──────────────────────────────────────────

    private static class SentinelJob {
        private final String        id;
        private final String        title;
        private final String        message;
        private final LocalDateTime at;
        private volatile boolean    fired = false;

        SentinelJob(String id, String title, String message, LocalDateTime at) {
            this.id = id; this.title = title; this.message = message; this.at = at;
        }
        String        id()      { return id;      }
        String        title()   { return title;   }
        String        message() { return message; }
        LocalDateTime at()      { return at;      }
        boolean       fired()   { return fired;   }
        void          markFired(){ this.fired = true; }
    }
}
