package com.openbe.worker;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * WorkspaceHeartbeat — Worker 心跳写入器。
 *
 * 每 5 秒将当前进程的运行指标写入 ~/.openbe/workspace/HEARTBEAT.md，
 * Queen 的 WorkspaceSync 监听到变更后广播给 UI，实现系统蜂状态卡实时刷新。
 */
@Component
public class WorkspaceHeartbeat {

    private static final Path HEARTBEAT_FILE =
        Paths.get(System.getProperty("user.home"), ".openbe", "workspace", "HEARTBEAT.md");

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkerBeeService workerBeeService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "workspace-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public WorkspaceHeartbeat(WorkerBeeService workerBeeService) {
        this.workerBeeService = workerBeeService;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::write, 5, 5, TimeUnit.SECONDS);
    }

    private void write() {
        try {
            Runtime rt      = Runtime.getRuntime();
            long usedMB     = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
            long maxMB      = rt.maxMemory() / 1_048_576;
            long pid        = ProcessHandle.current().pid();
            boolean active  = workerBeeService.hasActiveTasks();
            long idleMs     = System.currentTimeMillis() - workerBeeService.getLastTaskTime();

            String content = "# HEARTBEAT\n\n"
                + "last_updated: "  + LocalDateTime.now().format(FMT) + "\n"
                + "bee_type: WORKER\n"
                + "pid: "           + pid    + "\n"
                + "memory_used_mb: "+ usedMB + "\n"
                + "memory_max_mb: " + maxMB  + "\n"
                + "status: "        + (active ? "BUSY" : "ONLINE") + "\n"
                + "idle_seconds: "  + (idleMs / 1000) + "\n";

            Files.createDirectories(HEARTBEAT_FILE.getParent());
            Files.writeString(HEARTBEAT_FILE, content);
        } catch (Exception ignored) {}
    }
}
