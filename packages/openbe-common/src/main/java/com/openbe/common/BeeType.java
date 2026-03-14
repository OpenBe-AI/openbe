package com.openbe.common;

/**
 * 蜜蜂角色类型枚举
 */
public enum BeeType {
    /** 蜂后：系统核心调度者 */
    QUEEN,
    /** 工蜂：执行具体 AI 任务 */
    WORKER,
    /** 守卫蜂：安全审计与过滤 */
    GUARDIAN,
    /** 护士蜂：记忆提炼与管理 */
    NURSE,
    /** 兵蜂：异常处理与熔断 */
    SOLDIER,
    /** 侦察蜂：信息探索与采集 */
    SCOUT
}
