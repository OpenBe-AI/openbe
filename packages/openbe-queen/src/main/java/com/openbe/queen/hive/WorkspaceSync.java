package com.openbe.queen.hive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.gateway.LaneQueueRouter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * WorkspaceSync — Queen 侧文件监听器。
 *
 * 监听路径：
 *  - ~/.openbe/workspace/          （系统蜂公共工作区）
 *  - ~/.openbe/hives/{id}/workspace/ （蜂巢工作区）
 *
 * 任意 .md 文件变更时，将文件内容发布到 Redis 频道 openbe.workspace.sync，
 * 供 Worker/Nurse 实时感知并更新本地缓存（如 TOOLS.md 禁用规则）。
 */
@Component
public class WorkspaceSync {

    public static final String WORKSPACE_CHANNEL = "openbe.workspace.sync";

    private static final Path OPENBE_HOME = Paths.get(System.getProperty("user.home"), ".openbe");
    private static final Path SYSTEM_WS   = OPENBE_HOME.resolve("workspace");
    private static final Path HIVES_ROOT  = OPENBE_HOME.resolve("hives");

    private final LaneQueueRouter router;
    private final ObjectMapper    objectMapper = new ObjectMapper();

    public WorkspaceSync(LaneQueueRouter router) {
        this.router = router;
    }

    @PostConstruct
    public void start() throws IOException {
        Files.createDirectories(SYSTEM_WS);
        Thread t = new Thread(this::watchLoop, "workspace-watcher");
        t.setDaemon(true);
        t.start();
        System.out.println("\033[32m[WorkspaceSync] 文件监听器已启动\033[0m");
    }

    private void watchLoop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            SYSTEM_WS.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);

            // 注册已有蜂巢工作区
            if (Files.exists(HIVES_ROOT)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(HIVES_ROOT)) {
                    for (Path hiveDir : ds) {
                        Path ws = hiveDir.resolve("workspace");
                        if (Files.isDirectory(ws)) {
                            ws.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
                        }
                    }
                }
            }

            while (true) {
                WatchKey key;
                try { key = watcher.take(); } catch (InterruptedException e) { break; }

                Path watchedDir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    if (!filename.toString().endsWith(".md")) continue;

                    Path fullPath = watchedDir.resolve(filename);
                    try {
                        String content = Files.readString(fullPath);
                        String hiveId  = resolveHiveId(watchedDir);
                        String msg = objectMapper.writeValueAsString(Map.of(
                            "hiveId",   hiveId,
                            "filename", filename.toString(),
                            "content",  content
                        ));
                        router.publishChannel(WORKSPACE_CHANNEL, msg);
                        System.out.printf("\033[90m[WorkspaceSync] 广播变更 -> hive=%s file=%s\033[0m%n",
                            hiveId, filename);
                    } catch (Exception ignored) {}
                }
                key.reset();
            }
        } catch (IOException e) {
            System.err.printf("[WorkspaceSync] 监听器异常: %s%n", e.getMessage());
        }
    }

    /** system workspace → "system"；hive workspace → hiveId */
    private String resolveHiveId(Path dir) {
        if (dir.equals(SYSTEM_WS)) return "system";
        Path parent = dir.getParent(); // ~/.openbe/hives/{hiveId}
        return parent != null ? parent.getFileName().toString() : "unknown";
    }
}
