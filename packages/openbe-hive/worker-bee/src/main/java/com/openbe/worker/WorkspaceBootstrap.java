package com.openbe.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.gateway.LaneQueueRouter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorkspaceBootstrap — Worker 启动时加载 BOOTSTRAP.md 和 TOOLS.md。
 *
 * 职责：
 *  1. ApplicationReady 后从 ~/.openbe/workspace/ 读取这两个文件。
 *  2. 解析 TOOLS.md 的 Markdown 表格，将 Enabled=false 的工具放入禁用集合。
 *  3. 订阅 openbe.workspace.sync 频道，收到 TOOLS.md 变更时热重载。
 *  4. 向 WorkerBeeService 提供 isToolEnabled() 查询接口。
 */
@Component
public class WorkspaceBootstrap {

    private static final String WORKSPACE_CHANNEL = "openbe.workspace.sync";
    private static final Path   SYSTEM_WS =
        Paths.get(System.getProperty("user.home"), ".openbe", "workspace");

    private final LaneQueueRouter router;
    private final ObjectMapper    objectMapper = new ObjectMapper();
    private final Set<String>     disabledTools = ConcurrentHashMap.newKeySet();

    public WorkspaceBootstrap(LaneQueueRouter router) {
        this.router = router;
        // 在构造函数中注册，确保在容器 start() 前完成
        router.subscribeChannel(WORKSPACE_CHANNEL, this::onWorkspaceChange);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadWorkspace() {
        printBootstrap();
        loadTools();
    }

    /** 打印 BOOTSTRAP.md 内容（启动前置检查） */
    private void printBootstrap() {
        Path f = SYSTEM_WS.resolve("BOOTSTRAP.md");
        if (!Files.exists(f)) return;
        try {
            String content = Files.readString(f);
            System.out.println("\033[33m[WorkspaceBootstrap] ── BOOTSTRAP.md ──\033[0m");
            System.out.println(content.strip());
            System.out.println("\033[33m[WorkspaceBootstrap] ─────────────────\033[0m");
        } catch (Exception ignored) {}
    }

    /** 解析 TOOLS.md，更新禁用工具集合 */
    void loadTools() {
        Path f = SYSTEM_WS.resolve("TOOLS.md");
        if (!Files.exists(f)) return;
        try {
            String content = Files.readString(f);
            disabledTools.clear();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (!line.startsWith("|") || line.contains("---") || line.toLowerCase().contains("tool")) continue;
                String[] cols = line.split("\\|");
                if (cols.length < 3) continue;
                String tool    = cols[1].trim().toLowerCase().replace(" ", "_");
                String enabled = cols[2].trim().toLowerCase();
                if ("false".equals(enabled)) disabledTools.add(tool);
            }
            System.out.printf("\033[33m[WorkspaceBootstrap] TOOLS.md 已加载，禁用工具: %s\033[0m%n", disabledTools);
        } catch (Exception ignored) {}
    }

    /** 供 WorkerBeeService 查询某工具是否可用 */
    public boolean isToolEnabled(String toolName) {
        return !disabledTools.contains(toolName.toLowerCase().replace(" ", "_"));
    }

    /** Redis 工作区同步回调 */
    private void onWorkspaceChange(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String filename = node.path("filename").asText();
            String hiveId   = node.path("hiveId").asText("system");
            if ("system".equals(hiveId) || "TOOLS.md".equals(filename)) {
                if ("TOOLS.md".equals(filename))    loadTools();
                if ("BOOTSTRAP.md".equals(filename)) printBootstrap();
            }
        } catch (Exception ignored) {}
    }
}
