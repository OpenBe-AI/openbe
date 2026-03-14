"""
bee_factory.py — OpenBe 蜂刺技能注册表与蜜蜂工厂
Bee Stinger Skill Registry and Bee Factory for OpenBe.

BEE_SKILLS_REGISTRY: 100 built-in stinger skills, keyed by integer ID.
Bee class: initialise a bee with a name, type, and list of skill IDs.
           Prints an oath message on init.
"""

# ══════════════════════════════════════════════════════════════════════════════
# BEE_SKILLS_REGISTRY — 100 Built-in Stinger Skills
# ══════════════════════════════════════════════════════════════════════════════

BEE_SKILLS_REGISTRY: dict[int, dict] = {

    # ── QUEEN (1-8): 调度决策 / 系统管控 ─────────────────────────────────────
    1: {
        "name": "需求拆解",
        "desc": "将模糊的自然语言需求粉碎为可执行的JSON步骤任务链",
        "tool": "task_decomposer",
        "species": "QUEEN",
        "dangerous": False,
    },
    2: {
        "name": "意图路由",
        "desc": "精准识别用户潜台词，将任务派发给最合适的蜜蜂种群",
        "tool": "agent_router",
        "species": "QUEEN",
        "dangerous": False,
    },
    3: {
        "name": "共识决策",
        "desc": "当多个智能体意见分歧时，发起加权投票选出最优解",
        "tool": "consensus_voting",
        "species": "QUEEN",
        "dangerous": False,
    },
    4: {
        "name": "权重排序",
        "desc": "动态扫描待办队列，根据紧急程度实时调整任务优先级",
        "tool": "goal_prioritizer",
        "species": "QUEEN",
        "dangerous": False,
    },
    5: {
        "name": "进度评估",
        "desc": "实时监控工作流，百分比量化当前任务的达成深度",
        "tool": "state_evaluator",
        "species": "QUEEN",
        "dangerous": False,
    },
    6: {
        "name": "人工干预",
        "desc": "在关键节点自动暂停并请求主人决策，确保航向不偏航",
        "tool": "hitl_trigger",
        "species": "QUEEN",
        "dangerous": False,
    },
    7: {
        "name": "长文归纳",
        "desc": "将海量上下文信息脱水，仅保留核心逻辑节点供后续引用",
        "tool": "context_summarizer",
        "species": "QUEEN",
        "dangerous": False,
    },
    8: {
        "name": "按需孵化",
        "desc": "根据突发任务，临时生成一个具备特定窄领域能力的子智能体",
        "tool": "sub_agent_spawner",
        "species": "QUEEN",
        "dangerous": True,
    },

    # ── SCOUT (9-18): 实时情报 / 深度搜索 ────────────────────────────────────
    9: {
        "name": "全网搜索",
        "desc": "调用搜索引擎对全网进行实时信息检索，返回最新的相关内容摘要",
        "tool": "web_search",
        "species": "SCOUT",
        "dangerous": False,
    },
    10: {
        "name": "网页攀爬",
        "desc": "深度爬取目标网站的结构化内容，提取正文、链接与元数据",
        "tool": "web_scraper",
        "species": "SCOUT",
        "dangerous": False,
    },
    11: {
        "name": "快照抓取",
        "desc": "对指定URL进行截图或内容快照，保存页面瞬时状态供后续分析",
        "tool": "url_reader",
        "species": "SCOUT",
        "dangerous": False,
    },
    12: {
        "name": "百科检索",
        "desc": "从维基百科等知识百科中精准提取词条内容与关联知识图谱",
        "tool": "wiki_fetcher",
        "species": "SCOUT",
        "dangerous": False,
    },
    13: {
        "name": "开源猎取",
        "desc": "在GitHub上搜索开源项目、代码片段与Issue讨论，挖掘技术宝藏",
        "tool": "github_search",
        "species": "SCOUT",
        "dangerous": False,
    },
    14: {
        "name": "热点追踪",
        "desc": "实时监测各平台热搜榜单与趋势话题，捕捉舆论风向的瞬间变化",
        "tool": "trend_fetcher",
        "species": "SCOUT",
        "dangerous": False,
    },
    15: {
        "name": "论文搜寻",
        "desc": "在arXiv学术数据库中检索最新研究论文，获取摘要与引用信息",
        "tool": "arxiv_search",
        "species": "SCOUT",
        "dangerous": False,
    },
    16: {
        "name": "资讯订阅",
        "desc": "订阅并解析RSS/Atom资讯源，将信息流聚合为结构化内容推送",
        "tool": "rss_reader",
        "species": "SCOUT",
        "dangerous": False,
    },
    17: {
        "name": "视频听录",
        "desc": "自动转录YouTube视频的字幕与音频内容，生成带时间轴的文本",
        "tool": "youtube_transcribe",
        "species": "SCOUT",
        "dangerous": False,
    },
    18: {
        "name": "气象监测",
        "desc": "调用气象API获取全球任意城市的实时天气与未来天气预报数据",
        "tool": "weather_api",
        "species": "SCOUT",
        "dangerous": False,
    },

    # ── WORKER (19-30): 文件操作 / 代码执行 / 工程工具 ───────────────────────
    19: {
        "name": "文件读取",
        "desc": "安全读取本地文件系统中指定路径的文本或二进制文件内容",
        "tool": "read_file",
        "species": "WORKER",
        "dangerous": False,
    },
    20: {
        "name": "文件写入",
        "desc": "将内容精确写入指定文件路径，支持覆盖或新建模式",
        "tool": "write_file",
        "species": "WORKER",
        "dangerous": False,
    },
    21: {
        "name": "末尾增补",
        "desc": "在不覆盖原有内容的前提下，向文件末尾追加新的数据行",
        "tool": "append_file",
        "species": "WORKER",
        "dangerous": False,
    },
    22: {
        "name": "目录盘点",
        "desc": "递归列举指定目录下的所有文件与子目录，生成树状结构清单",
        "tool": "ls_directory",
        "species": "WORKER",
        "dangerous": False,
    },
    23: {
        "name": "脚本执行",
        "desc": "在沙箱环境中运行Python脚本并捕获标准输出与错误信息",
        "tool": "run_python",
        "species": "WORKER",
        "dangerous": False,
    },
    24: {
        "name": "环境运行",
        "desc": "在Node.js运行时中执行JavaScript代码，支持异步任务处理",
        "tool": "run_nodejs",
        "species": "WORKER",
        "dangerous": False,
    },
    25: {
        "name": "指令穿透",
        "desc": "直接执行原生Shell指令，穿透抽象层操控底层操作系统资源",
        "tool": "run_shell",
        "species": "WORKER",
        "dangerous": True,
    },
    26: {
        "name": "源码克隆",
        "desc": "通过Git协议将远程代码仓库完整克隆至本地工作目录",
        "tool": "git_clone",
        "species": "WORKER",
        "dangerous": False,
    },
    27: {
        "name": "版本归档",
        "desc": "执行Git提交、推送等版本控制操作，将代码变更同步至远端",
        "tool": "git_sync",
        "species": "WORKER",
        "dangerous": False,
    },
    28: {
        "name": "结构解析",
        "desc": "将源代码解析为抽象语法树(AST)，支持代码静态分析与重构",
        "tool": "ast_parser",
        "species": "WORKER",
        "dangerous": False,
    },
    29: {
        "name": "正则校验",
        "desc": "编译并测试正则表达式，批量提取文本中符合模式的所有匹配项",
        "tool": "regex_tester",
        "species": "WORKER",
        "dangerous": False,
    },
    30: {
        "name": "测试生成",
        "desc": "自动分析代码逻辑并生成覆盖边界条件的单元测试用例集合",
        "tool": "test_generator",
        "species": "WORKER",
        "dangerous": False,
    },

    # ── NURSE (31-39): 记忆存储 / 数据检索 ───────────────────────────────────
    31: {
        "name": "记忆刻录",
        "desc": "将知识向量化并刻录至向量库，供蜂群进行长周期记忆检索",
        "tool": "vector_insert",
        "species": "NURSE",
        "dangerous": False,
    },
    32: {
        "name": "语义检索",
        "desc": "不依赖关键词匹配，而是通过语义相似度找回遗忘的记忆碎片",
        "tool": "vector_search",
        "species": "NURSE",
        "dangerous": False,
    },
    33: {
        "name": "长文切片",
        "desc": "将海量文档智能切分为原子知识块，避免大模型处理信息过载",
        "tool": "doc_chunker",
        "species": "NURSE",
        "dangerous": False,
    },
    34: {
        "name": "库表查询",
        "desc": "生成并执行安全的SQL语句，从结构化数据库中精准提数",
        "tool": "sql_query",
        "species": "NURSE",
        "dangerous": False,
    },
    35: {
        "name": "关系溯源",
        "desc": "在知识图谱中追踪实体间的复杂脉络，揭示隐藏的逻辑关联",
        "tool": "graph_query",
        "species": "NURSE",
        "dangerous": False,
    },
    36: {
        "name": "极速读取",
        "desc": "从Redis等缓存中快速提取高频状态，实现亚秒级响应",
        "tool": "cache_read",
        "species": "NURSE",
        "dangerous": False,
    },
    37: {
        "name": "临时存证",
        "desc": "将任务中间态存入高速缓存，防止因系统意外中断导致丢失",
        "tool": "cache_write",
        "species": "NURSE",
        "dangerous": False,
    },
    38: {
        "name": "意图导流",
        "desc": "根据语义直接命中对应的知识库分支，提升检索命中率",
        "tool": "semantic_router",
        "species": "NURSE",
        "dangerous": False,
    },
    39: {
        "name": "全域索引",
        "desc": "对本地所有文件建立关键词倒排索引，实现毫秒级全文搜索",
        "tool": "local_indexer",
        "species": "NURSE",
        "dangerous": False,
    },

    # ── SCRIBE (40-48): 文档生成 / 语言处理 ──────────────────────────────────
    40: {
        "name": "排版转存",
        "desc": "将Markdown格式的草稿渲染为排版专业的PDF正式公文",
        "tool": "md_to_pdf",
        "species": "SCRIBE",
        "dangerous": False,
    },
    41: {
        "name": "表格提取",
        "desc": "解析混乱的CSV或表格数据，统一转化为标准化的JSON数组",
        "tool": "csv_parser",
        "species": "SCRIBE",
        "dangerous": False,
    },
    42: {
        "name": "数据导出",
        "desc": "将内存中的结构化数据快速导出为本地Excel报表",
        "tool": "excel_writer",
        "species": "SCRIBE",
        "dangerous": False,
    },
    43: {
        "name": "同步云端",
        "desc": "将本地成果一键同步至Notion等云端协同工具",
        "tool": "notion_sync",
        "species": "SCRIBE",
        "dangerous": False,
    },
    44: {
        "name": "万语互译",
        "desc": "利用长文本翻译引擎，实现全语种的专业级公文互译",
        "tool": "translator",
        "species": "SCRIBE",
        "dangerous": False,
    },
    45: {
        "name": "纠错润色",
        "desc": "深度扫描文本中的语法与逻辑缺陷，进行自动化风格优化",
        "tool": "grammar_check",
        "species": "SCRIBE",
        "dangerous": False,
    },
    46: {
        "name": "格式转换",
        "desc": "在JSON、YAML、XML等多种配置语言间进行无损互转",
        "tool": "format_converter",
        "species": "SCRIBE",
        "dangerous": False,
    },
    47: {
        "name": "影像识文",
        "desc": "通过OCR技术提取图片、扫描件中的文字，让纸面数据数字化",
        "tool": "ocr_tool",
        "species": "SCRIBE",
        "dangerous": False,
    },
    48: {
        "name": "函件拟定",
        "desc": "基于当前业务上下文，自动拟定得体、专业的商务邮件正文",
        "tool": "email_gen",
        "species": "SCRIBE",
        "dangerous": False,
    },

    # ── SOLDIER (49-56): 安全扫描 / 防御拦截 ─────────────────────────────────
    49: {
        "name": "密钥清查",
        "desc": "全量扫描代码库，防止API Key、密码等敏感信息误传泄露",
        "tool": "secret_scanner",
        "species": "SOLDIER",
        "dangerous": False,
    },
    50: {
        "name": "恶意过滤",
        "desc": "自动拦截注入攻击和越狱指令，守卫蜂巢的中枢逻辑安全",
        "tool": "prompt_filter",
        "species": "SOLDIER",
        "dangerous": False,
    },
    51: {
        "name": "漏洞审计",
        "desc": "对项目依赖包进行安全普查，识别并预警已知的CVE漏洞",
        "tool": "dep_audit",
        "species": "SOLDIER",
        "dangerous": False,
    },
    52: {
        "name": "权限鉴别",
        "desc": "严格验证当前操作人的权限等级，严禁任何越权指令执行",
        "tool": "auth_checker",
        "species": "SOLDIER",
        "dangerous": False,
    },
    53: {
        "name": "频率管控",
        "desc": "对外部API调用实施令牌桶限流，防止因超额访问被封禁账号",
        "tool": "rate_limiter",
        "species": "SOLDIER",
        "dangerous": False,
    },
    54: {
        "name": "日志查杀",
        "desc": "深度扫描系统日志，利用模式识别发现潜藏的异常入侵迹象",
        "tool": "log_scanner",
        "species": "SOLDIER",
        "dangerous": False,
    },
    55: {
        "name": "注入拦截",
        "desc": "在SQL执行前进行语法预审，彻底根除SQL注入风险",
        "tool": "sql_safe_tester",
        "species": "SOLDIER",
        "dangerous": False,
    },
    56: {
        "name": "隐私脱敏",
        "desc": "对姓名、电话等个人隐私数据进行自动遮蔽，符合合规要求",
        "tool": "pii_anonymizer",
        "species": "SOLDIER",
        "dangerous": False,
    },

    # ── MEDIC (57-64): 系统监控 / 健康检测 ───────────────────────────────────
    57: {
        "name": "负载监视",
        "desc": "实时上报蜂巢底层的CPU、内存占用情况，预警硬件瓶颈",
        "tool": "resource_monitor",
        "species": "MEDIC",
        "dangerous": False,
    },
    58: {
        "name": "时延测试",
        "desc": "测量与核心服务器间的网络往返时间，优化数据传输路径",
        "tool": "latency_check",
        "species": "MEDIC",
        "dangerous": False,
    },
    59: {
        "name": "存活预检",
        "desc": "定期探测第三方API接口状态，确保外部工具链链路畅通",
        "tool": "api_health",
        "species": "MEDIC",
        "dangerous": False,
    },
    60: {
        "name": "堆栈诊断",
        "desc": "在系统崩溃时自动捕获异常堆栈，输出人性化的故障分析",
        "tool": "error_analyzer",
        "species": "MEDIC",
        "dangerous": False,
    },
    61: {
        "name": "容量清点",
        "desc": "监控本地磁盘余量，防止因空间不足导致的任务写入失败",
        "tool": "disk_checker",
        "species": "MEDIC",
        "dangerous": False,
    },
    62: {
        "name": "链接校检",
        "desc": "全量扫描文档中的URL，标记并清理失效的\"死链\"",
        "tool": "link_checker",
        "species": "MEDIC",
        "dangerous": False,
    },
    63: {
        "name": "进程清理",
        "desc": "强制终止执行超时的僵尸进程，释放系统被占用的资源",
        "tool": "process_killer",
        "species": "MEDIC",
        "dangerous": True,
    },
    64: {
        "name": "工具核查",
        "desc": "校验Node、Python等底层运行环境的版本，确保环境一致性",
        "tool": "env_validator",
        "species": "MEDIC",
        "dangerous": False,
    },

    # ── PAINTER (65-72): 视觉创作 / 图像工具 ─────────────────────────────────
    65: {
        "name": "意向绘图",
        "desc": "将文字描述转化为高质量插画或海报，赋予思想以视觉形态",
        "tool": "sd_dalle_api",
        "species": "PAINTER",
        "dangerous": False,
    },
    66: {
        "name": "智能背景",
        "desc": "利用AI模型精准识别并移除图片背景，实现全自动抠图",
        "tool": "bg_remover",
        "species": "PAINTER",
        "dangerous": False,
    },
    67: {
        "name": "画幅裁切",
        "desc": "根据目标平台需求，智能调整图片尺寸并保持核心构图",
        "tool": "img_resizer",
        "species": "PAINTER",
        "dangerous": False,
    },
    68: {
        "name": "色调配比",
        "desc": "根据品牌调性提取并生成UI配色方案，提供十六进制色码",
        "tool": "palette_gen",
        "species": "PAINTER",
        "dangerous": False,
    },
    69: {
        "name": "矢量绘制",
        "desc": "通过生成代码来绘制无限缩放的SVG矢量图形",
        "tool": "svg_renderer",
        "species": "PAINTER",
        "dangerous": False,
    },
    70: {
        "name": "视觉描述",
        "desc": "充当蜂巢的\"眼睛\"，通过大语言模型解读图片中的深层意蕴",
        "tool": "vision_cap",
        "species": "PAINTER",
        "dangerous": False,
    },
    71: {
        "name": "码图生成",
        "desc": "将文本或链接编码为高辨识度的二维码图片",
        "tool": "qr_generator",
        "species": "PAINTER",
        "dangerous": False,
    },
    72: {
        "name": "图表渲染",
        "desc": "将枯燥的统计数据转化为精美的饼图、趋势图或雷达图",
        "tool": "chart_renderer",
        "species": "PAINTER",
        "dangerous": False,
    },

    # ── EDITOR (73-79): 音频处理 / 视频剪辑 ──────────────────────────────────
    73: {
        "name": "语音转文",
        "desc": "采用高精度Whisper模型，将长段音频转化为带时间轴的文本",
        "tool": "audio_to_text",
        "species": "EDITOR",
        "dangerous": False,
    },
    74: {
        "name": "声语合成",
        "desc": "将文本文稿合成多种情绪、多种音色的自然人声语音",
        "tool": "tts_engine",
        "species": "EDITOR",
        "dangerous": False,
    },
    75: {
        "name": "音轨转码",
        "desc": "在无损前提下进行音频格式批量转换，适配不同播放场景",
        "tool": "audio_converter",
        "species": "EDITOR",
        "dangerous": False,
    },
    76: {
        "name": "视频剪裁",
        "desc": "通过时间戳指令精准截取视频高光片段，并自动重新封装",
        "tool": "video_trimmer",
        "species": "EDITOR",
        "dangerous": False,
    },
    77: {
        "name": "逐帧提取",
        "desc": "每隔固定秒数抽取视频关键帧，用于视觉分析或封面生成",
        "tool": "frame_extractor",
        "species": "EDITOR",
        "dangerous": False,
    },
    78: {
        "name": "轴表压制",
        "desc": "自动生成符合标准的SRT字幕文件，实现音画同步对齐",
        "tool": "subtitle_gen",
        "species": "EDITOR",
        "dangerous": False,
    },
    79: {
        "name": "媒体属性",
        "desc": "提取音视频文件的比特率、帧率、编码格式等底层元数据",
        "tool": "media_meta",
        "species": "EDITOR",
        "dangerous": False,
    },

    # ── INFLUENCER (80-88): 内容分发 / 营销优化 ──────────────────────────────
    80: {
        "name": "电邮投递",
        "desc": "通过SMTP协议向指定目标发送带有附件的真实电子邮件",
        "tool": "smtp_sender",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    81: {
        "name": "频道播报",
        "desc": "向Slack指定频道发送格式化的富文本通知或报警消息",
        "tool": "slack_notifier",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    82: {
        "name": "钩子推报",
        "desc": "利用Webhook向Discord推送美观的内容展示卡片",
        "tool": "discord_hook",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    83: {
        "name": "动态发布",
        "desc": "调用社交平台API自动同步推文或动态，进行全网同步",
        "tool": "sns_publisher",
        "species": "INFLUENCER",
        "dangerous": True,
    },
    84: {
        "name": "词频调优",
        "desc": "分析文稿的SEO得分与关键词密度，优化搜索引擎自然排名",
        "tool": "seo_optimizer",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    85: {
        "name": "标签聚合",
        "desc": "根据文章语义自动关联最热门的标签，增加曝光维度",
        "tool": "hashtag_gen",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    86: {
        "name": "爆款标题",
        "desc": "利用心理学模型润色标题，生成多版本高点击率的标题候选",
        "tool": "headline_gen",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    87: {
        "name": "网页生成",
        "desc": "将MD成果一键转化为符合主流博客框架的HTML静态页面",
        "tool": "md_to_html",
        "species": "INFLUENCER",
        "dangerous": False,
    },
    88: {
        "name": "链接压缩",
        "desc": "生成极简短链接，方便在受限环境下传播并统计点击量",
        "tool": "url_shortener",
        "species": "INFLUENCER",
        "dangerous": False,
    },

    # ── SENTINEL (89-96): 定时调度 / 事件监控 ────────────────────────────────
    89: {
        "name": "周期计划",
        "desc": "注册Cron定时任务，实现蜂巢内部的自动化日常例行巡检",
        "tool": "cron_scheduler",
        "species": "SENTINEL",
        "dangerous": False,
    },
    90: {
        "name": "延时待命",
        "desc": "让当前执行逻辑进入休眠状态，在指定时间后自动唤醒续作",
        "tool": "wait_timer",
        "species": "SENTINEL",
        "dangerous": False,
    },
    91: {
        "name": "文件哨探",
        "desc": "实时监听特定文件夹，一旦有新文件进入立即触发后续流",
        "tool": "file_watcher",
        "species": "SENTINEL",
        "dangerous": False,
    },
    92: {
        "name": "回执监听",
        "desc": "开启本地端口，被动接收外部系统的HTTP回调通知",
        "tool": "webhook_receiver",
        "species": "SENTINEL",
        "dangerous": False,
    },
    93: {
        "name": "时区对齐",
        "desc": "处理跨国跨区域的任务执行时间，自动换算夏令时偏差",
        "tool": "tz_converter",
        "species": "SENTINEL",
        "dangerous": False,
    },
    94: {
        "name": "价格预警",
        "desc": "定时嗅探特定商品或云服务价格，达到阈值即刻触发提醒",
        "tool": "price_watcher",
        "species": "SENTINEL",
        "dangerous": False,
    },
    95: {
        "name": "运行统计",
        "desc": "汇总蜂巢自启动以来的在线时长与任务吞吐量数据",
        "tool": "uptime_stats",
        "species": "SENTINEL",
        "dangerous": False,
    },
    96: {
        "name": "事件广播",
        "desc": "在系统内部发布状态信号，协调多个蜜蜂协同完成跨步任务",
        "tool": "event_emitter",
        "species": "SENTINEL",
        "dangerous": False,
    },

    # ── UTILITY (97-100): 通用工具 ────────────────────────────────────────────
    97: {
        "name": "唯一确权",
        "desc": "生成符合标准的UUID/ULID，为蜂巢内的每个对象打上唯一标识",
        "tool": "uuid_gen",
        "species": "UTILITY",
        "dangerous": False,
    },
    98: {
        "name": "指纹校验",
        "desc": "计算字符串或文件的MD5/SHA256哈希值，确保数据未被篡改",
        "tool": "hash_calc",
        "species": "UTILITY",
        "dangerous": False,
    },
    99: {
        "name": "编码解密",
        "desc": "处理Base64、URL、Unicode等多种二进制数据与文本间的互转",
        "tool": "codec_tool",
        "species": "UTILITY",
        "dangerous": False,
    },
    100: {
        "name": "精准算力",
        "desc": "接管模型脆弱的口算逻辑，执行极其复杂的数学表达式运算",
        "tool": "math_engine",
        "species": "UTILITY",
        "dangerous": False,
    },
}


# ══════════════════════════════════════════════════════════════════════════════
# Bee Class
# ══════════════════════════════════════════════════════════════════════════════

class Bee:
    SPECIES_EMOJI: dict[str, str] = {
        "QUEEN":      "👑",
        "SCOUT":      "🔭",
        "WORKER":     "🐝",
        "NURSE":      "🏥",
        "SCRIBE":     "📝",
        "SOLDIER":    "🛡",
        "MEDIC":      "💊",
        "PAINTER":    "🎨",
        "EDITOR":     "🎬",
        "INFLUENCER": "📱",
        "SENTINEL":   "🔔",
        "UTILITY":    "🛠",
    }

    def __init__(self, name: str, bee_type: str, skill_ids: list[int]) -> None:
        self.name      = name
        self.bee_type  = bee_type.upper()
        self.skill_ids = [sid for sid in skill_ids if sid in BEE_SKILLS_REGISTRY]

        emoji = self.SPECIES_EMOJI.get(self.bee_type, "🐝")
        border = "╔══════════════════════════════════════════╗"
        mid    = "╠══════════════════════════════════════════╣"
        end    = "╚══════════════════════════════════════════╝"

        print(border)
        header_text = f"  {emoji} {self.name} 蜂蛹初始化完成"
        print(f"║{header_text:<42}║")
        print(mid)
        print(f"║  {'主人，我已就绪！以下是我的蜂刺神兵：':<34}  ║")
        for sid in self.skill_ids:
            skill = BEE_SKILLS_REGISTRY[sid]
            line = f"  → {skill['name']} ({skill['tool']})"
            print(f"║{line:<42}║")
        print(f"║  {'誓为蜂巢效力，不负主人所托！🫡':<35} ║")
        print(end)

    def activate_skill(self, skill_id: int) -> "dict | None":
        """Activate a skill by ID. Returns the skill entry if equipped and valid, else None."""
        if skill_id not in self.skill_ids:
            return None
        return BEE_SKILLS_REGISTRY.get(skill_id)

    def get_manifest(self) -> dict:
        """Return a structured manifest of this bee and its equipped skills."""
        return {
            "name":      self.name,
            "bee_type":  self.bee_type,
            "emoji":     self.SPECIES_EMOJI.get(self.bee_type, "🐝"),
            "skill_ids": self.skill_ids,
            "skills": [
                {
                    "id":   sid,
                    **BEE_SKILLS_REGISTRY[sid],
                }
                for sid in self.skill_ids
            ],
        }


# ══════════════════════════════════════════════════════════════════════════════
# __main__ demo
# ══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("\n" + "=" * 46)
    print("  OpenBe 蜂刺技能注册表 — 蜂蛹孵化演示")
    print("=" * 46 + "\n")

    # WORKER bee with skills: 文件读取, 指令穿透, 脚本执行, 版本归档
    worker = Bee(
        name="勤劳小蜂",
        bee_type="WORKER",
        skill_ids=[19, 23, 25, 27],
    )

    print()

    # QUEEN bee with skills: 需求拆解, 意图路由, 共识决策
    queen = Bee(
        name="蜂后陛下",
        bee_type="QUEEN",
        skill_ids=[1, 2, 3],
    )

    print()
    print("── WORKER 能力清单 ──")
    import json
    print(json.dumps(worker.get_manifest(), ensure_ascii=False, indent=2))

    print()
    print("── activate_skill 演示 ──")
    result = worker.activate_skill(25)
    if result:
        print(f"激活成功: 【{result['name']}】→ {result['tool']} (高危: {result['dangerous']})")

    invalid = worker.activate_skill(1)
    print(f"尝试激活未装备技能 #1: {'成功' if invalid else '拒绝 ✗'}")

    print()
    print(f"注册表总技能数: {len(BEE_SKILLS_REGISTRY)} 枚蜂刺")
