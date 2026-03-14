package com.openbe.queen.hive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * BeeRegistry — 持久化蜜蜂注册表，存储于 ~/.openbe/bees-registry.json。
 *
 * 蜜蜂的 Redis 健康 key 带有 TTL，进程停止后即消失。
 * 注册表作为补充，记录所有曾经孵化过的蜜蜂，确保重启后信息不丢失。
 */
@Component
public class BeeRegistry {

    private static final Path FILE = Paths.get(
        System.getProperty("user.home"), ".openbe", "bees-registry.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BeeEntry {
        public String beeId;
        public String species;   // MEDIC / SCOUT / WORKER ...
        public String healthType;// WORKER / NURSE (jar 类型)
        public String name;      // 用户取的名字
        public String hiveId;
        public String spawnedAt;

        public BeeEntry() {}
        public BeeEntry(String beeId, String species, String healthType,
                        String name, String hiveId) {
            this.beeId      = beeId;
            this.species    = species;
            this.healthType = healthType;
            this.name       = name != null ? name : "";
            this.hiveId     = hiveId != null ? hiveId : "";
            this.spawnedAt  = Instant.now().toString();
        }
    }

    // ── 读写 ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public synchronized List<BeeEntry> getAll() {
        if (!Files.exists(FILE)) return new ArrayList<>();
        try {
            List<BeeEntry> list = MAPPER.readValue(FILE.toFile(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, BeeEntry.class));
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[BeeRegistry] 读取失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized void register(BeeEntry entry) {
        List<BeeEntry> list = getAll();
        // 防止重复（相同 beeId）
        list.removeIf(e -> entry.beeId.equals(e.beeId));
        list.add(entry);
        save(list);
        System.out.printf("[BeeRegistry] 注册 %s (%s) beeId=%s%n",
            entry.name.isBlank() ? entry.species : entry.name, entry.species, entry.beeId);
    }

    /** 终止时按 beeId 注销 */
    public synchronized void unregisterById(String beeId) {
        List<BeeEntry> list = getAll();
        int before = list.size();
        list.removeIf(e -> beeId.equals(e.beeId));
        save(list);
        System.out.printf("[BeeRegistry] 注销 beeId=%s (移除 %d 条)%n", beeId, before - list.size());
    }

    /** 按 hiveId 批量注销，返回被移除的完整条目列表 */
    public synchronized List<BeeEntry> unregisterByHiveId(String hiveId) {
        List<BeeEntry> list = getAll();
        List<BeeEntry> removed = new ArrayList<>();
        list.removeIf(e -> {
            if (hiveId.equals(e.hiveId)) {
                removed.add(e);
                return true;
            }
            return false;
        });
        save(list);
        System.out.printf("[BeeRegistry] 注销蜂巢 %s 下的蜜蜂 (移除 %d 条)%n", hiveId, removed.size());
        return removed;
    }

    /** 按 healthType 批量注销（terminate 命令） */
    public synchronized List<String> unregisterByHealthType(String healthType) {
        List<BeeEntry> list = getAll();
        List<String> removedIds = new ArrayList<>();
        list.removeIf(e -> {
            if (healthType.equalsIgnoreCase(e.healthType)) {
                removedIds.add(e.beeId);
                return true;
            }
            return false;
        });
        save(list);
        return removedIds;
    }

    // ── 内部写入 ──────────────────────────────────────────────────

    private void save(List<BeeEntry> list) {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), list);
        } catch (Exception e) {
            System.err.println("[BeeRegistry] 写入失败: " + e.getMessage());
        }
    }
}
