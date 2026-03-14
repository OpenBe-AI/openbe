package com.openbe.queen.hive;

/**
 * 蜂巢配置，映射 ~/.openbe/hives/{hiveId}/config.yaml 顶层结构。
 * 一个蜂巢只养一只蜜蜂。
 */
public class HiveConfig {

    private String        hiveId;
    private String        name;
    private String        description = "";
    private BeeDefinition bee;

    public String getHiveId()          { return hiveId; }
    public void   setHiveId(String v)  { this.hiveId = v; }

    public String getName()            { return name; }
    public void   setName(String v)    { this.name = v; }

    public String getDescription()     { return description; }
    public void   setDescription(String v){ this.description = v; }

    public BeeDefinition getBee()              { return bee; }
    public void          setBee(BeeDefinition v){ this.bee = v; }
}
