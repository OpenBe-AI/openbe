package com.openbe.common;

/**
 * 消息车道颜色枚举 — 表示消息的优先级与安全隔离级别
 */
public enum LaneColor {
    /** 红色车道：敏感隔离，用于高优先级或安全敏感消息 */
    RED,
    /** 绿色车道：标准协作，用于常规智能体间通信 */
    GREEN,
    /** 黄色车道：后台维护，用于低优先级的内部维护任务 */
    YELLOW
}
