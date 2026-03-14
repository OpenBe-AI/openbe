package com.openbe.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 蜂蜜 — 用于存储护士蜂提炼后的极简 JSON 事实（结构化长期记忆）。
 * 由护士蜂将 Nectar 原始文本压缩提炼而成，供蜂群高效检索与复用。
 */
public class Honey {

    /** 记录唯一标识 */
    private String id;

    /** 关联的任务 ID */
    private String taskId;

    /** 护士蜂提炼后的极简 JSON 事实，保持最小化结构 */
    private String factsJson;

    /** 来源花蜜 ID */
    private String sourceNectarId;

    /** 提炼时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refinedAt;

    public Honey() {}

    public Honey(String taskId, String factsJson, String sourceNectarId) {
        this.id             = UUID.randomUUID().toString();
        this.taskId         = taskId;
        this.factsJson      = factsJson;
        this.sourceNectarId = sourceNectarId;
        this.refinedAt      = LocalDateTime.now();
    }

    public String getId()                  { return id; }
    public void setId(String v)            { this.id = v; }

    public String getTaskId()              { return taskId; }
    public void setTaskId(String v)        { this.taskId = v; }

    public String getFactsJson()           { return factsJson; }
    public void setFactsJson(String v)     { this.factsJson = v; }

    public String getSourceNectarId()           { return sourceNectarId; }
    public void setSourceNectarId(String v)     { this.sourceNectarId = v; }

    public LocalDateTime getRefinedAt()        { return refinedAt; }
    public void setRefinedAt(LocalDateTime v)  { this.refinedAt = v; }
}
