package com.openbe.queen.hive;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读写 ~/.openbe/hives/{hiveId}/config.yaml
 * 每次调用均实时读文件，支持热更新。
 * 一个蜂巢只养一只蜜蜂。
 */
@Service
public class HiveConfigLoader {

    private static final Path HIVES_ROOT =
        Paths.get(System.getProperty("user.home"), ".openbe", "hives");

    private final BeeWorkspace beeWorkspace;

    public HiveConfigLoader(BeeWorkspace beeWorkspace) {
        this.beeWorkspace = beeWorkspace;
    }

    public List<HiveConfig> loadAll() {
        List<HiveConfig> result = new ArrayList<>();
        if (!Files.exists(HIVES_ROOT)) return result;
        try (var stream = Files.list(HIVES_ROOT)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                HiveConfig cfg = load(dir.getFileName().toString());
                if (cfg != null) result.add(cfg);
            });
        } catch (Exception ignored) {}
        result.sort(Comparator.comparing(h -> h.getName() == null ? "" : h.getName()));
        return result;
    }

    public HiveConfig load(String hiveId) {
        Path file = HIVES_ROOT.resolve(hiveId).resolve("config.yaml");
        if (!Files.exists(file)) return null;
        try (var in = new FileInputStream(file.toFile())) {
            LoaderOptions opts = new LoaderOptions();
            opts.setTagInspector(tag -> true);
            Yaml yaml = new Yaml(new Constructor(HiveConfig.class, opts));
            HiveConfig cfg = yaml.load(in);
            if (cfg != null && cfg.getHiveId() == null) cfg.setHiveId(hiveId);
            return cfg;
        } catch (Exception e) {
            System.err.printf("[HiveLoader] 读取 %s 失败: %s%n", file, e.getMessage());
            return null;
        }
    }

    public HiveConfig create(String hiveId, String name, String description) throws Exception {
        Path dir = HIVES_ROOT.resolve(hiveId);
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("notes"));

        HiveConfig cfg = new HiveConfig();
        cfg.setHiveId(hiveId);
        cfg.setName(name);
        cfg.setDescription(description == null ? "" : description);

        save(cfg);
        beeWorkspace.init(hiveId);
        return cfg;
    }

    public void setBee(String hiveId, BeeDefinition bee) throws Exception {
        HiveConfig cfg = load(hiveId);
        if (cfg == null) throw new IllegalArgumentException("蜂巢不存在: " + hiveId);
        // 蜂巢配置里的蜜蜂始终是 Queen，固定 beeId 确保路径向后兼容
        bee.setBeeId(BeeWorkspace.QUEEN_BEE_ID);
        bee.setQueen(true);
        if (bee.getSpecies() == null || bee.getSpecies().isBlank()) bee.setSpecies("QUEEN");
        cfg.setBee(bee);
        save(cfg);
    }

    public void updateMeta(String hiveId, String name, String description) throws Exception {
        HiveConfig cfg = load(hiveId);
        if (cfg == null) throw new IllegalArgumentException("蜂巢不存在: " + hiveId);
        if (name != null && !name.isBlank()) cfg.setName(name.trim());
        if (description != null) cfg.setDescription(description);
        save(cfg);
    }

    public void deleteBee(String hiveId) throws Exception {
        HiveConfig cfg = load(hiveId);
        if (cfg == null) return;
        cfg.setBee(null);
        save(cfg);
    }

    public void deleteHive(String hiveId) throws Exception {
        Path dir = HIVES_ROOT.resolve(hiveId);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (Exception ignored) {}
            });
        }
    }

    private void save(HiveConfig cfg) throws Exception {
        Path file = HIVES_ROOT.resolve(cfg.getHiveId()).resolve("config.yaml");
        DumperOptions dumpOpts = new DumperOptions();
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumpOpts.setPrettyFlow(true);
        Representer representer = new Representer(dumpOpts);
        representer.addClassTag(HiveConfig.class,    Tag.MAP);
        representer.addClassTag(BeeDefinition.class, Tag.MAP);
        Yaml yaml = new Yaml(representer, dumpOpts);
        try (var writer = new FileWriter(file.toFile())) {
            yaml.dump(cfg, writer);
        }
    }

    public void ensureDefaultHive() {
        if (loadAll().isEmpty()) {
            try {
                HiveConfig def = create("default", "默认蜂巢", "OpenBe 默认工作区");
                BeeDefinition bee = new BeeDefinition();
                bee.setName("通用助手");
                bee.setProvider("ollama");
                bee.setModel("");
                bee.setSystemPrompt("你是一个有帮助的 AI 助手。");
                bee.setTemperature(0.7);
                def.setBee(bee);
                save(def);
            } catch (Exception e) {
                System.err.printf("[HiveLoader] 创建默认蜂巢失败: %s%n", e.getMessage());
            }
        }
    }
}
