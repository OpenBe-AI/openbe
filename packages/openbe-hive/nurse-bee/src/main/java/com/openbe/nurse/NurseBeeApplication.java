package com.openbe.nurse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CountDownLatch;

/**
 * 护士蜂应用入口 — 监听 YELLOW 车道，将问答提炼为知识蜜糖并写入 Soul 记忆文件。
 *
 * keepAlive 修复：CountDownLatch.await() 在 SpringApplication.run() 返回后执行。
 */
@SpringBootApplication(scanBasePackages = {"com.openbe.nurse", "com.openbe.gateway"})
public class NurseBeeApplication {

    private static final CountDownLatch KEEP_ALIVE = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(NurseBeeApplication.class, args);
        KEEP_ALIVE.await();
    }
}
