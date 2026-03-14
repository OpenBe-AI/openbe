package com.openbe.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Soul 记忆存储 — 将提炼后的知识蜜糖（Honey）追加写入 ~/.openbe/workspace/soul.md。
 * 每次写入均携带时间戳和分隔线，方便人工阅读和后续检索。
 * 线程安全：方法加 synchronized，支持护士蜂并发写入。
 */
public class SoulStorage {

    private static final Path SOUL_FILE = Paths.get(
        System.getProperty("user.home"), ".openbe", "workspace", "soul.md");

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将一段 honey（已提炼的 JSON 知识点）追加写入 soul.md。
     *
     * @param honey 护士蜂压缩后的 JSON 内容
     */
    public synchronized void append(String honey) {
        try {
            ensureFileExists();
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String entry = "\n## " + timestamp + "\n" + honey.trim() + "\n\n---\n";
            Files.writeString(SOUL_FILE, entry,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[SoulStorage] 写入 soul.md 失败: " + e.getMessage());
        }
    }

    private void ensureFileExists() throws IOException {
        if (!Files.exists(SOUL_FILE)) {
            Files.createDirectories(SOUL_FILE.getParent());
            Files.writeString(SOUL_FILE,
                "# 🍯 OpenBe Soul — 蜂巢记忆文件\n\n> 由护士蜂自动提炼，记录每一次思考的精华。\n\n---\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        }
    }
}
