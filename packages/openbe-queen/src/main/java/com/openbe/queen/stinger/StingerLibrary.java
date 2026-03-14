package com.openbe.queen.stinger;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

/**
 * StingerLibrary — 蜂刺资产库。
 *
 * 存储路径迁移至 ~/.openbe/library/stingers/（旧路径 ~/.openbe/stingers/ 中的文件会自动迁移）。
 * 每个蜂刺有元数据：名称、图标、描述、稀有度（blue/purple/gold）、危险标记。
 */
@Component
public class StingerLibrary {

    private static final Path STINGERS_DIR =
        Paths.get(System.getProperty("user.home"), ".openbe", "library", "stingers");

    private static final Path OLD_STINGERS_DIR =
        Paths.get(System.getProperty("user.home"), ".openbe", "stingers");

    // ── 元数据注册表 ─────────────────────────────────────────

    private static final Map<String, Map<String, Object>> METADATA = new LinkedHashMap<>();

    static {
        reg("notify.sh",           "桌面通知",   "🔔", "发送 macOS 原生系统通知",              "blue",   false);
        reg("desktop_organize.sh", "桌面整理",   "🗂", "按类型自动归类整理 ~/Desktop 文件",     "blue",   true);
        reg("mail_read.scpt",      "读取邮件",   "📬", "读取 Mail.app 最新收件箱列表",           "purple", true);
        reg("mail_draft.scpt",     "起草邮件",   "✉️", "通过 Mail.app 起草并预览新邮件",        "purple", true);
        reg("office_create.scpt",  "文档创建",   "📄", "通过 Pages 快速创建新文档",              "purple", true);
        reg("media_cut.sh",        "媒体剪辑",   "🎬", "使用 FFmpeg 精准截取视频片段",           "purple", true);
        reg("vision_analyze.sh",   "图像分析",   "🔍", "提取图像元数据与基础视觉特征",           "gold",   false);
    }

    private static void reg(String id, String name, String icon, String desc, String rarity, boolean dangerous) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          id);
        m.put("name",        name);
        m.put("icon",        icon);
        m.put("description", desc);
        m.put("rarity",      rarity);
        m.put("dangerous",   dangerous);
        METADATA.put(id, Collections.unmodifiableMap(m));
    }

    // ── 启动初始化 ───────────────────────────────────────────

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(STINGERS_DIR);

            // 迁移旧路径中的自定义脚本
            if (Files.exists(OLD_STINGERS_DIR)) {
                try (var stream = Files.list(OLD_STINGERS_DIR)) {
                    stream.filter(p -> !Files.isDirectory(p)).forEach(src -> {
                        Path dest = STINGERS_DIR.resolve(src.getFileName());
                        if (!Files.exists(dest)) {
                            try {
                                Files.copy(src, dest);
                                if (dest.toString().endsWith(".sh")) dest.toFile().setExecutable(true);
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception ignored) {}
            }

            // 写入默认脚本（如不存在）
            for (Map.Entry<String, String> e : defaults().entrySet()) {
                Path f = STINGERS_DIR.resolve(e.getKey());
                if (!Files.exists(f)) {
                    Files.writeString(f, e.getValue());
                    if (e.getKey().endsWith(".sh")) f.toFile().setExecutable(true);
                }
            }
            System.out.println("\033[32m[StingerLibrary] 蜂刺资产库已就绪 → " + STINGERS_DIR + "\033[0m");
        } catch (Exception e) {
            System.err.printf("[StingerLibrary] 初始化失败: %s%n", e.getMessage());
        }
    }

    // ── 公共查询方法 ─────────────────────────────────────────

    public static Path getStingersDir() { return STINGERS_DIR; }

    public static Map<String, Object> getMetadata(String id) {
        if (METADATA.containsKey(id)) return METADATA.get(id);
        // 尝试读取 sidecar .meta.json（用户锻造的自定义蜂刺）
        Path metaFile = STINGERS_DIR.resolve(id + ".meta.json");
        if (Files.exists(metaFile)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(metaFile.toFile(), Map.class);
                m.put("id", id);
                m.putIfAbsent("dangerous", false);
                return m;
            } catch (Exception ignored) {}
        }
        // 未知蜂刺：根据文件名推断
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          id);
        m.put("name",        id.replaceAll("\\.[a-z]+$", "").replace("_", " "));
        m.put("icon",        "⚡");
        m.put("description", "自定义蜂刺脚本");
        m.put("rarity",      "blue");
        m.put("dangerous",   false);
        return m;
    }

    public static List<Map<String, Object>> enrichFileList(List<String> fileNames) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : fileNames) result.add(getMetadata(name));
        return result;
    }

    public static boolean isDangerous(String name) {
        Map<String, Object> meta = METADATA.get(name);
        if (meta != null) return Boolean.TRUE.equals(meta.get("dangerous"));
        return name.startsWith("mail_") || name.startsWith("desktop_organize")
            || name.startsWith("office_") || name.startsWith("media_");
    }

    public static String dangerMessage(String name) {
        return switch (name) {
            case "desktop_organize.sh" -> "即将重新整理您的 ~/Desktop，文件将被移入子文件夹。";
            case "mail_draft.scpt"     -> "即将通过 Mail.app 起草新邮件。";
            case "mail_read.scpt"      -> "即将读取 Mail.app 中的邮件列表。";
            case "office_create.scpt"  -> "即将通过 Pages/Keynote 创建新文档。";
            case "media_cut.sh"        -> "即将使用 FFmpeg 处理媒体文件（原文件不受影响，输出为新文件）。";
            default -> "即将执行 Stinger：" + name + "，此操作可能修改系统状态。";
        };
    }

    // ── 默认脚本内容 ─────────────────────────────────────────

    private Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("desktop_organize.sh", """
            #!/bin/bash
            # DesktopStinger: 将 ~/Desktop 文件按扩展名整理到子目录
            DESKTOP="$HOME/Desktop"
            declare -A EXT_MAP=(
              ["pdf"]="Documents/PDF"
              ["doc"]="Documents/Word" ["docx"]="Documents/Word"
              ["xls"]="Documents/Excel" ["xlsx"]="Documents/Excel"
              ["ppt"]="Documents/PPT" ["pptx"]="Documents/PPT"
              ["jpg"]="Images" ["jpeg"]="Images" ["png"]="Images" ["gif"]="Images" ["heic"]="Images"
              ["mp4"]="Videos" ["mov"]="Videos" ["avi"]="Videos"
              ["mp3"]="Audio" ["wav"]="Audio" ["m4a"]="Audio"
              ["zip"]="Archives" ["tar"]="Archives" ["gz"]="Archives"
            )
            for FILE in "$DESKTOP"/*; do
              [ -f "$FILE" ] || continue
              EXT="${FILE##*.}"
              EXT_LOWER=$(echo "$EXT" | tr '[:upper:]' '[:lower:]')
              DEST="${EXT_MAP[$EXT_LOWER]}"
              [ -z "$DEST" ] && DEST="Others"
              mkdir -p "$DESKTOP/$DEST"
              mv "$FILE" "$DESKTOP/$DEST/"
            done
            echo "Desktop organized."
            """);

        m.put("notify.sh", """
            #!/bin/bash
            # NotificationStinger: 触发 macOS 原生通知
            TITLE="${1:-OpenBe 通知}"
            MSG="${2:-任务已完成}"
            osascript -e "display notification \\"$MSG\\" with title \\"$TITLE\\""
            echo "Notification sent: [$TITLE] $MSG"
            """);

        m.put("mail_read.scpt", """
            -- MailStinger: 读取 Mail.app 最新 5 封邮件主题
            tell application "Mail"
                set inbox to mailbox "INBOX" of first account
                set msgs to messages of inbox
                set result to ""
                repeat with i from 1 to (count of msgs)
                    if i > 5 then exit repeat
                    set result to result & (subject of item i of msgs) & "\\n"
                end repeat
                return result
            end tell
            """);

        m.put("mail_draft.scpt", """
            -- MailStinger: 在 Mail.app 创建草稿
            on run argv
                set toAddr to item 1 of argv
                set subj   to item 2 of argv
                set body   to item 3 of argv
                tell application "Mail"
                    set newMsg to make new outgoing message with properties ¬
                        {subject:subj, content:body, visible:true}
                    tell newMsg
                        make new to recipient with properties {address:toAddr}
                    end tell
                end tell
            end run
            """);

        m.put("office_create.scpt", """
            -- OfficeStinger: 用 Pages 创建新文档
            on run argv
                set docTitle to "OpenBe Document"
                if (count of argv) > 0 then set docTitle to item 1 of argv
                tell application "Pages"
                    activate
                    set newDoc to make new document
                    tell newDoc
                        tell body text
                            set its content to docTitle
                        end tell
                    end tell
                end tell
                return "Document created: " & docTitle
            end run
            """);

        m.put("media_cut.sh", """
            #!/bin/bash
            # MediaStinger: 用 FFmpeg 截取视频片段
            INPUT="$1"
            START="$2"
            DURATION="$3"
            OUTPUT="${INPUT%.*}_cut.mp4"
            if ! command -v ffmpeg &> /dev/null; then
                echo "Error: ffmpeg not installed. Run: brew install ffmpeg"
                exit 1
            fi
            ffmpeg -i "$INPUT" -ss "$START" -t "$DURATION" -c copy "$OUTPUT"
            echo "Cut saved: $OUTPUT"
            """);

        m.put("vision_analyze.sh", """
            #!/bin/bash
            # VisionStinger: 提取图像基本信息（macOS sips）
            INPUT="$1"
            if [ -z "$INPUT" ]; then
                echo "Usage: vision_analyze.sh <image_path>"
                exit 1
            fi
            sips -g all "$INPUT" 2>/dev/null | grep -E "pixelWidth|pixelHeight|dpiWidth|format|fileSize"
            echo "Analysis complete for: $INPUT"
            """);

        return m;
    }
}
