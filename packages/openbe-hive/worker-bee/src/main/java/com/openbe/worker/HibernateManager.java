package com.openbe.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 蜂蛹冬眠管理器 — 监控工蜂闲置时长，超过阈值后触发优雅自尽，释放 JVM 资源。
 *
 * 原理：
 *  每 30 秒检查一次 WorkerBeeService.lastTaskTime。
 *  若当前时间距上次任务超过 IDLE_TIMEOUT_MS（3 分钟），则进入 Pupae 冬眠状态：
 *  打印提示 + System.exit(0)，释放所有占用内存。
 *
 * 注意：需要在 WorkerBeeApplication 上启用 @EnableScheduling 才能生效。
 */
@Component
public class HibernateManager {

    /** 闲置超时阈值：3 分钟 */
    private static final long IDLE_TIMEOUT_MS = 3 * 60 * 1_000L;

    private final WorkerBeeService workerBeeService;

    public HibernateManager(WorkerBeeService workerBeeService) {
        this.workerBeeService = workerBeeService;
    }

    /**
     * 每 30 秒检查一次闲置状态。
     * fixedDelay：上次执行完成后再计时，避免检查本身堆积。
     */
    @Scheduled(fixedDelay = 30_000)
    public void checkIdle() {
        // 有任务正在处理中，不允许冬眠
        if (workerBeeService.hasActiveTasks()) return;

        long idleMs = System.currentTimeMillis() - workerBeeService.getLastTaskTime();
        if (idleMs >= IDLE_TIMEOUT_MS) {
            System.out.println("\033[33m╔══════════════════════════════════════════════╗\033[0m");
            System.out.println("\033[33m║  🫘 [WORKER] 闲置超时，进入蜂蛹 (Pupae)     ║\033[0m");
            System.out.println("\033[33m║      冬眠状态，释放系统资源...               ║\033[0m");
            System.out.println("\033[33m╚══════════════════════════════════════════════╝\033[0m");
            System.exit(0);
        }
    }
}
