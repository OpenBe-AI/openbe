package com.openbe.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CountDownLatch;

/**
 * 工蜂应用入口 — 监听任务车道，调用 Ollama，将结果回报蜂后。
 *
 * keepAlive 修复：CountDownLatch.await() 在 SpringApplication.run() 返回后执行，
 * Spring 容器完全初始化（含订阅线程启动）后主线程再阻塞，避免 ApplicationReadyEvent 死锁。
 *
 * @EnableScheduling 启用定时任务，支持 HibernateManager 冬眠检测。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.openbe.worker", "com.openbe.gateway"})
public class WorkerBeeApplication {

    /** 永远不会 countDown，由 main() 在 run() 后持有 */
    private static final CountDownLatch KEEP_ALIVE = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(WorkerBeeApplication.class, args);
        // Spring 容器完全就绪后，主线程在此阻塞
        KEEP_ALIVE.await();
    }
}
