package com.openbe.queen.hive;

/**
 * 单个 Bee（智能体）的配置定义。
 *
 * beeId  — 蜂巢内唯一 ID，Queen 固定为 "workspace"（路径向后兼容）。
 * isQueen — 蜂巢内第一只蜂自动成为 Queen。
 * species — QUEEN / WORKER / SOLDIER / NURSE / SCOUT / MECHANIC。
 */
public class BeeDefinition {

    private String  name;         // 显示名称
    private String  provider;     // openai / anthropic / ollama / custom
    private String  model;
    private String  apiKey;
    private String  baseUrl;
    private String  systemPrompt;
    private Double  temperature;
    private String  beeId;        // 工作区子目录名，Queen = "workspace"
    private boolean isQueen;
    private String  species;      // QUEEN / WORKER / SOLDIER / NURSE / SCOUT / MECHANIC

    public String  getName()         { return name; }
    public void    setName(String v) { this.name = v; }

    public String  getProvider()          { return provider; }
    public void    setProvider(String v)  { this.provider = v; }

    public String  getModel()             { return model; }
    public void    setModel(String v)     { this.model = v; }

    public String  getApiKey()            { return apiKey; }
    public void    setApiKey(String v)    { this.apiKey = v; }

    public String  getBaseUrl()           { return baseUrl; }
    public void    setBaseUrl(String v)   { this.baseUrl = v; }

    public String  getSystemPrompt()           { return systemPrompt; }
    public void    setSystemPrompt(String v)   { this.systemPrompt = v; }

    public Double  getTemperature()            { return temperature; }
    public void    setTemperature(Double v)    { this.temperature = v; }

    public String  getBeeId()              { return beeId != null ? beeId : "workspace"; }
    public void    setBeeId(String v)      { this.beeId = v; }

    public boolean isQueen()               { return isQueen; }
    public void    setQueen(boolean v)     { this.isQueen = v; }

    public String  getSpecies()            { return species != null ? species : "QUEEN"; }
    public void    setSpecies(String v)    { this.species = v; }
}
