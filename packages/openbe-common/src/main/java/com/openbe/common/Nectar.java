package com.openbe.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 花蜜 — 用于存储 Agent 刚输出的原始长文本（未经提炼的原始记忆）。
 * 由工蜂/侦察蜂在任务完成后产生，交由护士蜂进行提炼加工。
 */
public class Nectar {

    /** 记录唯一标识 */
    private String id;

    /** 关联的任务 ID */
    private String taskId;

    /** Agent 输出的原始长文本 */
    private String rawText;

    /** 产生花蜜的蜜蜂类型 */
    private BeeType producedBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public Nectar() {}

    public Nectar(String taskId, String rawText, BeeType producedBy) {
        this.id         = UUID.randomUUID().toString();
        this.taskId     = taskId;
        this.rawText    = rawText;
        this.producedBy = producedBy;
        this.createdAt  = LocalDateTime.now();
    }

    public String getId()               { return id; }
    public void setId(String v)         { this.id = v; }

    public String getTaskId()           { return taskId; }
    public void setTaskId(String v)     { this.taskId = v; }

    public String getRawText()          { return rawText; }
    public void setRawText(String v)    { this.rawText = v; }

    public BeeType getProducedBy()         { return producedBy; }
    public void setProducedBy(BeeType v)   { this.producedBy = v; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
}
