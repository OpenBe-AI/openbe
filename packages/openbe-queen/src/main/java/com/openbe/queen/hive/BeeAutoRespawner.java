package com.openbe.queen.hive;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

/**
 * BeeAutoRespawner — Queen 启动后自动重孵曾经孵化过的蜜蜂。
 *
 * 流程：
 *  1. 读取 ~/.openbe/bees-registry.json 中所有注册蜜蜂
 *  2. 检查 Redis openbe:health:{healthType}:{pid} 是否还有存活实例
 *  3. 若该蜜蜂对应的 jar 存在且当前没有同类存活实例，则自动重孵
 *
 * 注意：每个 beeId 是唯一的，重孵时进程 PID 会变化；
 *       已通过 ManagementController 的 spawn 逻辑更新 Redis。
 */
@Component
public class BeeAutoRespawner {

    private static final Path OPENBE_HOME  = Paths.get(System.getProperty("user.home"), ".openbe");
    private static final String HEALTH_PFX = "openbe:health:";

    private final BeeRegistry         registry;
    private final BeeWorkspace        workspace;
    private final StringRedisTemplate redisTemplate;

    public BeeAutoRespawner(BeeRegistry registry,
                            BeeWorkspace workspace,
                            StringRedisTemplate redisTemplate) {
        this.registry      = registry;
        this.workspace     = workspace;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void respawnAll() {
        List<BeeRegistry.BeeEntry> bees = registry.getAll();
        if (bees.isEmpty()) {
            System.out.println("[BeeAutoRespawner] 注册表为空，无需重孵");
            return;
        }
        System.out.printf("[BeeAutoRespawner] 发现 %d 只已注册蜜蜂，开始检查存活状态…%n", bees.size());

        for (BeeRegistry.BeeEntry entry : bees) {
            try {
                respawn(entry);
            } catch (Exception e) {
                System.err.printf("[BeeAutoRespawner] 重孵 %s 失败: %s%n", entry.beeId, e.getMessage());
            }
        }
    }

    private void respawn(BeeRegistry.BeeEntry entry) throws IOException {
        // 检查是否已有同 beeId 存活（极少情况下可能跨重启存活）
        Set<String> keys = redisTemplate.keys(HEALTH_PFX + entry.healthType.toUpperCase() + ":*");
        if (keys != null) {
            for (String key : keys) {
                // 获取该 key 的所有字段，查找 beeId 匹配的存活实例
                Object storedBeeId = redisTemplate.opsForHash().get(key, "beeId");
                if (entry.beeId.equals(storedBeeId)) {
                    System.out.printf("[BeeAutoRespawner] %s (%s) 已在运行，跳过%n",
                        entry.name, entry.beeId);
                    return;
                }
            }
        }

        // 选择 jar
        String jarName = "NURSE".equalsIgnoreCase(entry.healthType) ? "nurse-bee.jar" : "worker-bee.jar";
        Path jarPath = OPENBE_HOME.resolve(jarName);
        if (!Files.exists(jarPath)) {
            System.out.printf("[BeeAutoRespawner] jar 不存在 (%s)，跳过 %s%n", jarPath, entry.beeId);
            return;
        }

        String javaExe = ProcessHandle.current().info().command()
            .orElse(System.getProperty("java.home") + "/bin/java");

        String logFile = OPENBE_HOME.resolve(
            entry.species.toLowerCase() + "-" + entry.beeId + ".log").toString();

        ProcessBuilder pb = new ProcessBuilder(
            javaExe, "--sun-misc-unsafe-memory-access=allow",
            "-Dopenbe.bee.species=" + entry.species.toUpperCase(),
            "-Dopenbe.hive.id="    + entry.hiveId,
            "-Dopenbe.bee.id="     + entry.beeId,
            "-jar", jarPath.toString()
        )
            .directory(OPENBE_HOME.toFile())
            .redirectOutput(new File(logFile))
            .redirectErrorStream(true);

        Process process = pb.start();
        long newPid = process.pid();

        // 更新 Redis 元数据到新 PID
        String pidKey = entry.healthType.toUpperCase() + ":" + newPid;
        if (entry.name != null && !entry.name.isBlank())
            redisTemplate.opsForHash().put("openbe:beenames",  pidKey, entry.name);
        redisTemplate.opsForHash().put("openbe:beespecies", pidKey, entry.species.toUpperCase());
        redisTemplate.opsForHash().put("openbe:beeid",      pidKey, entry.beeId);

        // 刷新 IDENTITY.md 和 SOUL.md，确保物种和名字正确（覆盖旧的"工蜂"内容）
        if (entry.hiveId != null && !entry.hiveId.isBlank()) {
            try {
                String displayName = (entry.name != null && !entry.name.isBlank())
                    ? entry.name : entry.species;
                workspace.initWithName(entry.hiveId, entry.beeId, entry.species, displayName);
            } catch (Exception e) {
                System.err.printf("[BeeAutoRespawner] 刷新工作区失败 %s: %s%n", entry.beeId, e.getMessage());
            }
        }

        System.out.printf("[BeeAutoRespawner] ✅ 已重孵 %s (%s) → PID %d%n",
            entry.name.isBlank() ? entry.species : entry.name, entry.beeId, newPid);
    }
}
