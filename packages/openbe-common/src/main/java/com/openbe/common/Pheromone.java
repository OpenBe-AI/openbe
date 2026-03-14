package com.openbe.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 信息素 — OpenBe 蜂群系统的核心通信载体。
 * 所有智能体间的消息传递均通过 Pheromone 封装，经由信息素总线路由。
 */
public class Pheromone {

    /** 任务唯一标识 */
    private String taskId;

    /** 发送方蜜蜂类型 */
    private BeeType sourceBee;

    /** 接收方蜜蜂类型 */
    private BeeType targetBee;

    /** 消息车道颜色（决定路由策略与优先级） */
    private LaneColor laneColor;

    /**
     * 消息载荷。
     * 强制要求传输 JSON 格式数据，接收方需自行反序列化为目标对象。
     */
    private String payload;

    /** 信息素创建时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public Pheromone() {}

    private Pheromone(Builder builder) {
        this.taskId    = builder.taskId;
        this.sourceBee = builder.sourceBee;
        this.targetBee = builder.targetBee;
        this.laneColor = builder.laneColor;
        this.payload   = builder.payload;
        this.createdAt = LocalDateTime.now();
    }

    // ── Builder ──────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String    taskId    = UUID.randomUUID().toString();
        private BeeType   sourceBee;
        private BeeType   targetBee;
        private LaneColor laneColor = LaneColor.GREEN;
        private String    payload   = "{}";

        public Builder taskId(String taskId)       { this.taskId = taskId;       return this; }
        public Builder sourceBee(BeeType source)   { this.sourceBee = source;    return this; }
        public Builder targetBee(BeeType target)   { this.targetBee = target;    return this; }
        public Builder laneColor(LaneColor lane)   { this.laneColor = lane;      return this; }
        public Builder payload(String payload)     { this.payload = payload;     return this; }
        public Pheromone build()                   { return new Pheromone(this); }
    }

    // ── Getters & Setters ─────────────────────────────────────
    public String getTaskId()          { return taskId; }
    public void setTaskId(String v)    { this.taskId = v; }

    public BeeType getSourceBee()         { return sourceBee; }
    public void setSourceBee(BeeType v)   { this.sourceBee = v; }

    public BeeType getTargetBee()         { return targetBee; }
    public void setTargetBee(BeeType v)   { this.targetBee = v; }

    public LaneColor getLaneColor()          { return laneColor; }
    public void setLaneColor(LaneColor v)    { this.laneColor = v; }

    public String getPayload()         { return payload; }
    public void setPayload(String v)   { this.payload = v; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }

    @Override
    public String toString() {
        return "Pheromone{taskId='" + taskId + "', source=" + sourceBee +
               ", target=" + targetBee + ", lane=" + laneColor +
               ", payload=" + payload + "}";
    }
}
