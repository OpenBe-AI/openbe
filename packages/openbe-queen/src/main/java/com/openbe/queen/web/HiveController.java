package com.openbe.queen.web;

import com.openbe.queen.hive.BeeDefinition;
import com.openbe.queen.hive.BeeRegistry;
import com.openbe.queen.hive.BeeWorkspace;
import com.openbe.queen.hive.HiveConfig;
import com.openbe.queen.hive.HiveConfigLoader;
import com.openbe.queen.stinger.StingerLibrary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * 蜂巢管理 API（一个蜂巢只养一只蜜蜂）
 *
 * GET    /api/hives                               — 列出所有蜂巢（含蜜蜂信息）
 * POST   /api/hives                               — 新建蜂巢（可同时配置蜜蜂）
 * DELETE /api/hives/{hiveId}                      — 删除蜂巢
 * GET    /api/hives/{hiveId}/bee                  — 获取蜂巢的蜜蜂配置（apiKey 掩码）
 * PUT    /api/hives/{hiveId}/bee                  — 更新蜜蜂配置（同时初始化 Queen 工作区）
 * DELETE /api/hives/{hiveId}/bee                  — 清空蜜蜂配置
 * GET    /api/hives/{hiveId}/bees/{beeId}/stingers — 获取蜜蜂已分配蜂刺
 * PUT    /api/hives/{hiveId}/bees/{beeId}/stingers — 分配蜂刺（克隆到 skills/，更新 TOOLS.md）
 */
@RestController
@RequestMapping("/api/hives")
@CrossOrigin(origins = "*")
public class HiveController {

    private static final String HEALTH_PREFIX = "openbe:health:";
    private static final String BEENAMES_KEY  = "openbe:beenames";
    private static final String BEESPECIES_KEY = "openbe:beespecies";
    private static final String BEEID_KEY     = "openbe:beeid";

    private final HiveConfigLoader    loader;
    private final BeeWorkspace         workspace;
    private final StringRedisTemplate  redisTemplate;
    private final BeeRegistry          beeRegistry;

    public HiveController(HiveConfigLoader loader, BeeWorkspace workspace,
                          StringRedisTemplate redisTemplate, BeeRegistry beeRegistry) {
        this.loader        = loader;
        this.workspace     = workspace;
        this.redisTemplate = redisTemplate;
        this.beeRegistry   = beeRegistry;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listHives() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HiveConfig cfg : loader.loadAll()) {
            result.add(toHiveMap(cfg, true));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createHive(@RequestBody Map<String, Object> body) {
        String hiveId = str(body, "hiveId");
        String name   = str(body, "name", hiveId);
        String desc   = str(body, "description", "");
        if (hiveId == null || hiveId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "hiveId 不能为空"));
        hiveId = hiveId.trim().toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
        try {
            HiveConfig cfg = loader.create(hiveId, name, desc);
            // 如果 body 里含蜜蜂配置，一起保存并初始化 Queen 工作区
            if (body.containsKey("provider") || body.containsKey("model")) {
                BeeDefinition bee = buildBee(body);
                loader.setBee(hiveId, bee);
                initQueenWorkspace(hiveId, bee);
                cfg = loader.load(hiveId);
            }
            return ResponseEntity.ok(toHiveMap(cfg, true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{hiveId}")
    public ResponseEntity<Map<String, Object>> updateHive(
            @PathVariable String hiveId,
            @RequestBody Map<String, Object> body) {
        try {
            loader.updateMeta(hiveId, str(body, "name"), str(body, "description"));
            HiveConfig cfg = loader.load(hiveId);
            return ResponseEntity.ok(toHiveMap(cfg, true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{hiveId}")
    public ResponseEntity<Map<String, Object>> deleteHive(@PathVariable String hiveId) {
        try {
            terminateHiveBees(hiveId);
            loader.deleteHive(hiveId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 终止属于该蜂巢的所有蜜蜂进程，清理 Redis 和注册表 */
    private void terminateHiveBees(String hiveId) {
        List<BeeRegistry.BeeEntry> entries = beeRegistry.unregisterByHiveId(hiveId);
        if (entries.isEmpty()) return;

        Set<String> targetBeeIds = new HashSet<>();
        entries.forEach(e -> targetBeeIds.add(e.beeId));

        // 反查 openbe:beeid 哈希（TYPE:PID → beeId），找到需要终止的进程
        Map<Object, Object> beeIdMap = redisTemplate.opsForHash().entries(BEEID_KEY);
        for (Map.Entry<Object, Object> entry : beeIdMap.entrySet()) {
            String typePid = entry.getKey().toString();
            String beeId   = entry.getValue().toString();
            if (!targetBeeIds.contains(beeId)) continue;

            String[] parts = typePid.split(":", 2);
            if (parts.length < 2) continue;
            try {
                long pid = Long.parseLong(parts[1]);
                ProcessHandle.of(pid).ifPresent(ph -> {
                    ph.destroy();
                    System.out.printf("[HiveController] 终止蜜蜂 beeId=%s pid=%d%n", beeId, pid);
                });
                redisTemplate.delete(HEALTH_PREFIX + typePid);
                redisTemplate.opsForHash().delete(BEENAMES_KEY,   typePid);
                redisTemplate.opsForHash().delete(BEESPECIES_KEY, typePid);
                redisTemplate.opsForHash().delete(BEEID_KEY,      typePid);
            } catch (NumberFormatException ignored) {}
        }
    }

    @GetMapping("/{hiveId}/bee")
    public ResponseEntity<Map<String, Object>> getBee(@PathVariable String hiveId) {
        HiveConfig cfg = loader.load(hiveId);
        if (cfg == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toSafeBeemap(cfg.getBee()));
    }

    /**
     * 配置蜂王 — 保存 config.yaml 后立即初始化 Queen 工作区（SOUL.md 等 11 个文件）。
     * 修复：原来只写 config.yaml，首次聊天时才懒加载，现在立即完成。
     */
    @PutMapping("/{hiveId}/bee")
    public ResponseEntity<Map<String, Object>> updateBee(
            @PathVariable String hiveId,
            @RequestBody Map<String, Object> body) {
        try {
            BeeDefinition bee = buildBee(body);
            loader.setBee(hiveId, bee);
            initQueenWorkspace(hiveId, bee);
            return ResponseEntity.ok(toSafeBeemap(loader.load(hiveId).getBee()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{hiveId}/bee")
    public ResponseEntity<Map<String, Object>> deleteBee(@PathVariable String hiveId) {
        try {
            loader.deleteBee(hiveId);
            // 硬删除：同步清空 Queen 工作区文件（白板初始化保证）
            deleteWorkspaceDir(hiveId, BeeWorkspace.QUEEN_BEE_ID);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 工作区 API（Queen，beeId=workspace）──────────────

    @GetMapping("/{hiveId}/workspace")
    public ResponseEntity<Map<String, Object>> listWorkspace(@PathVariable String hiveId) {
        try {
            workspace.init(hiveId);
            return ResponseEntity.ok(Map.of("files", workspace.listFiles(hiveId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{hiveId}/workspace/{file}")
    public ResponseEntity<Map<String, Object>> readWorkspace(
            @PathVariable String hiveId,
            @PathVariable String file) {
        return ResponseEntity.ok(Map.of("content", workspace.read(hiveId, file)));
    }

    @PutMapping("/{hiveId}/workspace/{file}")
    public ResponseEntity<Map<String, Object>> writeWorkspace(
            @PathVariable String hiveId,
            @PathVariable String file,
            @RequestBody Map<String, String> body) {
        try {
            workspace.write(hiveId, file, body.getOrDefault("content", ""));
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 工作区 API（per-beeId）────────────────────────────

    @GetMapping("/{hiveId}/bees/{beeId}/workspace")
    public ResponseEntity<Map<String, Object>> listBeeWorkspace(
            @PathVariable String hiveId,
            @PathVariable String beeId) {
        try {
            // 从注册表获取真实物种，防止 worker-bee.jar 以 WORKER 类型覆写 IDENTITY.md 导致物种丢失
            BeeRegistry.BeeEntry entry = beeRegistry.getAll().stream()
                .filter(e -> beeId.equals(e.beeId))
                .findFirst().orElse(null);
            String species = (entry != null && entry.species != null && !entry.species.isBlank())
                ? entry.species : workspace.readSpeciesFromIdentity(hiveId, beeId);
            String beeName = (entry != null && entry.name != null && !entry.name.isBlank())
                ? entry.name : "";
            workspace.initWithName(hiveId, beeId, species, beeName);
            return ResponseEntity.ok(Map.of("files", workspace.listFiles(hiveId, beeId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{hiveId}/bees/{beeId}/workspace/{file}")
    public ResponseEntity<Map<String, Object>> readBeeWorkspace(
            @PathVariable String hiveId,
            @PathVariable String beeId,
            @PathVariable String file) {
        return ResponseEntity.ok(Map.of("content", workspace.read(hiveId, beeId, file)));
    }

    @PutMapping("/{hiveId}/bees/{beeId}/workspace/{file}")
    public ResponseEntity<Map<String, Object>> writeBeeWorkspace(
            @PathVariable String hiveId,
            @PathVariable String beeId,
            @PathVariable String file,
            @RequestBody Map<String, String> body) {
        try {
            workspace.write(hiveId, beeId, file, body.getOrDefault("content", ""));
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 蜂刺分配 API（per-beeId）──────────────────────────

    /**
     * 获取指定蜜蜂已分配的蜂刺列表（读取 skills/ 目录）。
     */
    @GetMapping("/{hiveId}/bees/{beeId}/stingers")
    public ResponseEntity<List<Map<String, Object>>> getBeeStingers(
            @PathVariable String hiveId,
            @PathVariable String beeId) {
        Path skillsDir = skillsDir(hiveId, beeId);
        List<String> files = new ArrayList<>();
        if (Files.exists(skillsDir)) {
            try (var stream = Files.list(skillsDir)) {
                stream.filter(p -> !Files.isDirectory(p))
                      .map(p -> p.getFileName().toString())
                      .sorted()
                      .forEach(files::add);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(StingerLibrary.enrichFileList(files));
    }

    /**
     * 分配蜂刺给指定蜜蜂：
     *  1. 从全局库克隆脚本到 ~/.openbe/hives/{hiveId}/{beeId}/skills/
     *  2. 更新该蜜蜂的 TOOLS.md（Assigned Stingers 段落）
     */
    @PutMapping("/{hiveId}/bees/{beeId}/stingers")
    public ResponseEntity<Map<String, Object>> assignBeeStingers(
            @PathVariable String hiveId,
            @PathVariable String beeId,
            @RequestBody List<String> stingerNames) {
        try {
            Path skillsDir = skillsDir(hiveId, beeId);
            Files.createDirectories(skillsDir);

            List<String> provisioned = new ArrayList<>();
            for (String name : stingerNames) {
                String safe = name.replaceAll("[^A-Za-z0-9._\\-]", "");
                Path src = StingerLibrary.getStingersDir().resolve(safe);
                if (!Files.exists(src)) continue;
                Path dst = skillsDir.resolve(safe);
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                if (safe.endsWith(".sh")) {
                    try {
                        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(dst);
                        perms.add(PosixFilePermission.OWNER_EXECUTE);
                        Files.setPosixFilePermissions(dst, perms);
                    } catch (Exception ignored) { dst.toFile().setExecutable(true); }
                }
                provisioned.add(safe);
            }

            updateStingersSection(hiveId, beeId, provisioned);

            // 写入首次唤醒标记：下次对话触发蜂刺宣誓仪式
            Path wakeupFlag = skillsDir.getParent().resolve("WAKEUP.flag");
            Files.writeString(wakeupFlag, String.join("\n", provisioned),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return ResponseEntity.ok(Map.of("status", "ok", "provisioned", provisioned));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 内部工具 ───────────────────────────────────────────

    /** 配置蜂王后立即初始化工作区，确保 SOUL.md 等文件在聊天前就存在 */
    private void initQueenWorkspace(String hiveId, BeeDefinition bee) {
        String queenName = (bee.getName() != null && !bee.getName().isBlank())
            ? bee.getName() : "QUEEN";
        try {
            workspace.initWithName(hiveId, BeeWorkspace.QUEEN_BEE_ID, "QUEEN", queenName);
        } catch (Exception e) {
            System.err.printf("[HiveController] Queen 工作区初始化失败: %s%n", e.getMessage());
        }
    }

    /** 更新 TOOLS.md 中的 Assigned Stingers 段落 */
    private void updateStingersSection(String hiveId, String beeId, List<String> stingers) {
        try {
            String current = workspace.read(hiveId, beeId, "TOOLS.md");
            // 移除旧的 Stingers 段落
            int idx = current.indexOf("\n## Assigned Stingers");
            String base = (idx >= 0 ? current.substring(0, idx) : current).stripTrailing();

            StringBuilder sb = new StringBuilder(base);
            sb.append("\n\n## Assigned Stingers\n\n");
            sb.append("| Stinger | Local Path |\n");
            sb.append("|---------|------------|\n");
            for (String s : stingers) {
                sb.append("| ").append(s).append(" | skills/").append(s).append(" |\n");
            }
            workspace.write(hiveId, beeId, "TOOLS.md", sb.toString());
        } catch (Exception e) {
            System.err.printf("[HiveController] 更新 TOOLS.md 失败: %s%n", e.getMessage());
        }
    }

    private Path skillsDir(String hiveId, String beeId) {
        return Paths.get(System.getProperty("user.home"), ".openbe", "hives", hiveId, beeId, "skills");
    }

    /** 硬删除指定蜜蜂的工作区目录（彻底清空记忆，白板初始化保证） */
    private void deleteWorkspaceDir(String hiveId, String beeId) {
        Path dir = Paths.get(System.getProperty("user.home"), ".openbe", "hives", hiveId, beeId);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            System.err.printf("[HiveController] 清空工作区失败 %s/%s: %s%n", hiveId, beeId, e.getMessage());
        }
    }

    private Map<String, Object> toHiveMap(HiveConfig cfg, boolean maskKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hiveId",      cfg.getHiveId());
        m.put("name",        cfg.getName());
        m.put("description", cfg.getDescription());
        m.put("bee",         maskKey ? toSafeBeemap(cfg.getBee()) : toBeeMap(cfg.getBee()));
        return m;
    }

    private Map<String, Object> toSafeBeemap(BeeDefinition b) {
        if (b == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",         b.getName());
        m.put("provider",     b.getProvider());
        m.put("model",        b.getModel());
        String raw = b.getApiKey();
        m.put("apiKeyMasked", raw != null && raw.length() > 4
            ? raw.substring(0, 4) + "••••" : (raw != null && !raw.isEmpty() ? "••••" : ""));
        m.put("baseUrl",      b.getBaseUrl());
        m.put("systemPrompt", b.getSystemPrompt());
        m.put("temperature",  b.getTemperature());
        m.put("beeId",        b.getBeeId());
        m.put("isQueen",      b.isQueen());
        m.put("species",      b.getSpecies());
        return m;
    }

    private Map<String, Object> toBeeMap(BeeDefinition b) {
        if (b == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",         b.getName());
        m.put("provider",     b.getProvider());
        m.put("model",        b.getModel());
        m.put("apiKey",       b.getApiKey());
        m.put("baseUrl",      b.getBaseUrl());
        m.put("systemPrompt", b.getSystemPrompt());
        m.put("temperature",  b.getTemperature());
        return m;
    }

    private BeeDefinition buildBee(Map<String, Object> body) {
        BeeDefinition bee = new BeeDefinition();
        bee.setName(str(body, "name", ""));
        bee.setProvider(str(body, "provider", "ollama"));
        bee.setModel(str(body, "model", ""));
        bee.setApiKey(str(body, "apiKey", ""));
        bee.setBaseUrl(str(body, "baseUrl", ""));
        bee.setSystemPrompt(str(body, "systemPrompt", ""));
        Object temp = body.get("temperature");
        if (temp != null) {
            try { bee.setTemperature(Double.parseDouble(temp.toString())); } catch (Exception ignored) {}
        }
        return bee;
    }

    private String str(Map<String, Object> m, String key) { return str(m, key, null); }
    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
