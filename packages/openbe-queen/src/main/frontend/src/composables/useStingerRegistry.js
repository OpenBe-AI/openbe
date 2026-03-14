/**
 * useStingerRegistry.js — 蜂刺技能注册表
 * Single source of truth for 100 built-in bee stinger skills.
 *
 * @typedef {Object} Stinger
 * @property {number}   id       — Unique skill ID (1-100)
 * @property {string}   species  — Owner species key (QUEEN, SCOUT, WORKER, …, UTILITY)
 * @property {string}   name     — 4-char Chinese skill name
 * @property {string}   tool     — Backend tool identifier
 * @property {string}   desc     — Full description
 * @property {string}   icon     — Emoji icon
 * @property {'blue'|'purple'|'gold'} rarity
 * @property {boolean}  [dangerous] — High-risk flag
 */

// ══════════════════════════════════════════════════════════════════════════════
// STINGER_REGISTRY — 100 Built-in Stinger Skills
// ══════════════════════════════════════════════════════════════════════════════

export const STINGER_REGISTRY = [

  // ── QUEEN (1-8): 调度决策 / 系统管控 ────────────────────────────────────
  {
    id: 1, species: 'QUEEN', name: '需求拆解', tool: 'task_decomposer',
    desc: '将模糊的自然语言需求粉碎为可执行的JSON步骤任务链',
    icon: '🎯', rarity: 'gold',
  },
  {
    id: 2, species: 'QUEEN', name: '意图路由', tool: 'agent_router',
    desc: '精准识别用户潜台词，将任务派发给最合适的蜜蜂种群',
    icon: '🗺️', rarity: 'gold',
  },
  {
    id: 3, species: 'QUEEN', name: '共识决策', tool: 'consensus_voting',
    desc: '当多个智能体意见分歧时，发起加权投票选出最优解',
    icon: '⚖️', rarity: 'gold',
  },
  {
    id: 4, species: 'QUEEN', name: '权重排序', tool: 'goal_prioritizer',
    desc: '动态扫描待办队列，根据紧急程度实时调整任务优先级',
    icon: '📊', rarity: 'purple',
  },
  {
    id: 5, species: 'QUEEN', name: '进度评估', tool: 'state_evaluator',
    desc: '实时监控工作流，百分比量化当前任务的达成深度',
    icon: '📈', rarity: 'purple',
  },
  {
    id: 6, species: 'QUEEN', name: '人工干预', tool: 'hitl_trigger',
    desc: '在关键节点自动暂停并请求主人决策，确保航向不偏航',
    icon: '🛑', rarity: 'purple',
  },
  {
    id: 7, species: 'QUEEN', name: '长文归纳', tool: 'context_summarizer',
    desc: '将海量上下文信息脱水，仅保留核心逻辑节点供后续引用',
    icon: '📋', rarity: 'blue',
  },
  {
    id: 8, species: 'QUEEN', name: '按需孵化', tool: 'sub_agent_spawner',
    desc: '根据突发任务，临时生成一个具备特定窄领域能力的子智能体',
    icon: '🌱', rarity: 'gold', dangerous: true,
  },

  // ── SCOUT (9-18): 实时情报 / 深度搜索 ──────────────────────────────────
  {
    id: 9, species: 'SCOUT', name: '全网搜索', tool: 'web_search',
    desc: '调用搜索引擎对全网进行实时信息检索，返回最新的相关内容摘要',
    icon: '🌐', rarity: 'blue',
  },
  {
    id: 10, species: 'SCOUT', name: '网页攀爬', tool: 'web_scraper',
    desc: '深度爬取目标网站的结构化内容，提取正文、链接与元数据',
    icon: '🕷️', rarity: 'blue',
  },
  {
    id: 11, species: 'SCOUT', name: '快照抓取', tool: 'url_reader',
    desc: '对指定URL进行截图或内容快照，保存页面瞬时状态供后续分析',
    icon: '📷', rarity: 'blue',
  },
  {
    id: 12, species: 'SCOUT', name: '百科检索', tool: 'wiki_fetcher',
    desc: '从维基百科等知识百科中精准提取词条内容与关联知识图谱',
    icon: '📚', rarity: 'blue',
  },
  {
    id: 13, species: 'SCOUT', name: '开源猎取', tool: 'github_search',
    desc: '在GitHub上搜索开源项目、代码片段与Issue讨论，挖掘技术宝藏',
    icon: '🐙', rarity: 'blue',
  },
  {
    id: 14, species: 'SCOUT', name: '热点追踪', tool: 'trend_fetcher',
    desc: '实时监测各平台热搜榜单与趋势话题，捕捉舆论风向的瞬间变化',
    icon: '📰', rarity: 'blue',
  },
  {
    id: 15, species: 'SCOUT', name: '论文搜寻', tool: 'arxiv_search',
    desc: '在arXiv学术数据库中检索最新研究论文，获取摘要与引用信息',
    icon: '🎓', rarity: 'purple',
  },
  {
    id: 16, species: 'SCOUT', name: '资讯订阅', tool: 'rss_reader',
    desc: '订阅并解析RSS/Atom资讯源，将信息流聚合为结构化内容推送',
    icon: '📡', rarity: 'blue',
  },
  {
    id: 17, species: 'SCOUT', name: '视频听录', tool: 'youtube_transcribe',
    desc: '自动转录YouTube视频的字幕与音频内容，生成带时间轴的文本',
    icon: '🎥', rarity: 'purple',
  },
  {
    id: 18, species: 'SCOUT', name: '气象监测', tool: 'weather_api',
    desc: '调用气象API获取全球任意城市的实时天气与未来天气预报数据',
    icon: '🌤️', rarity: 'blue',
  },

  // ── WORKER (19-30): 文件操作 / 代码执行 / 工程工具 ─────────────────────
  {
    id: 19, species: 'WORKER', name: '文件读取', tool: 'read_file',
    desc: '安全读取本地文件系统中指定路径的文本或二进制文件内容',
    icon: '📖', rarity: 'blue',
  },
  {
    id: 20, species: 'WORKER', name: '文件写入', tool: 'write_file',
    desc: '将内容精确写入指定文件路径，支持覆盖或新建模式',
    icon: '✏️', rarity: 'blue',
  },
  {
    id: 21, species: 'WORKER', name: '末尾增补', tool: 'append_file',
    desc: '在不覆盖原有内容的前提下，向文件末尾追加新的数据行',
    icon: '➕', rarity: 'blue',
  },
  {
    id: 22, species: 'WORKER', name: '目录盘点', tool: 'ls_directory',
    desc: '递归列举指定目录下的所有文件与子目录，生成树状结构清单',
    icon: '📁', rarity: 'blue',
  },
  {
    id: 23, species: 'WORKER', name: '脚本执行', tool: 'run_python',
    desc: '在沙箱环境中运行Python脚本并捕获标准输出与错误信息',
    icon: '🐍', rarity: 'purple',
  },
  {
    id: 24, species: 'WORKER', name: '环境运行', tool: 'run_nodejs',
    desc: '在Node.js运行时中执行JavaScript代码，支持异步任务处理',
    icon: '⬡', rarity: 'purple',
  },
  {
    id: 25, species: 'WORKER', name: '指令穿透', tool: 'run_shell',
    desc: '直接执行原生Shell指令，穿透抽象层操控底层操作系统资源',
    icon: '💻', rarity: 'gold', dangerous: true,
  },
  {
    id: 26, species: 'WORKER', name: '源码克隆', tool: 'git_clone',
    desc: '通过Git协议将远程代码仓库完整克隆至本地工作目录',
    icon: '🌿', rarity: 'blue',
  },
  {
    id: 27, species: 'WORKER', name: '版本归档', tool: 'git_sync',
    desc: '执行Git提交、推送等版本控制操作，将代码变更同步至远端',
    icon: '🔀', rarity: 'blue',
  },
  {
    id: 28, species: 'WORKER', name: '结构解析', tool: 'ast_parser',
    desc: '将源代码解析为抽象语法树(AST)，支持代码静态分析与重构',
    icon: '🌳', rarity: 'purple',
  },
  {
    id: 29, species: 'WORKER', name: '正则校验', tool: 'regex_tester',
    desc: '编译并测试正则表达式，批量提取文本中符合模式的所有匹配项',
    icon: '⚡', rarity: 'blue',
  },
  {
    id: 30, species: 'WORKER', name: '测试生成', tool: 'test_generator',
    desc: '自动分析代码逻辑并生成覆盖边界条件的单元测试用例集合',
    icon: '🧪', rarity: 'purple',
  },

  // ── NURSE (31-39): 记忆存储 / 数据检索 ─────────────────────────────────
  {
    id: 31, species: 'NURSE', name: '记忆刻录', tool: 'vector_insert',
    desc: '将知识向量化并刻录至向量库，供蜂群进行长周期记忆检索',
    icon: '💾', rarity: 'purple',
  },
  {
    id: 32, species: 'NURSE', name: '语义检索', tool: 'vector_search',
    desc: '不依赖关键词匹配，而是通过语义相似度找回遗忘的记忆碎片',
    icon: '🔍', rarity: 'purple',
  },
  {
    id: 33, species: 'NURSE', name: '长文切片', tool: 'doc_chunker',
    desc: '将海量文档智能切分为原子知识块，避免大模型处理信息过载',
    icon: '✂️', rarity: 'blue',
  },
  {
    id: 34, species: 'NURSE', name: '库表查询', tool: 'sql_query',
    desc: '生成并执行安全的SQL语句，从结构化数据库中精准提数',
    icon: '🗄️', rarity: 'blue',
  },
  {
    id: 35, species: 'NURSE', name: '关系溯源', tool: 'graph_query',
    desc: '在知识图谱中追踪实体间的复杂脉络，揭示隐藏的逻辑关联',
    icon: '🕸️', rarity: 'purple',
  },
  {
    id: 36, species: 'NURSE', name: '极速读取', tool: 'cache_read',
    desc: '从Redis等缓存中快速提取高频状态，实现亚秒级响应',
    icon: '⚡', rarity: 'blue',
  },
  {
    id: 37, species: 'NURSE', name: '临时存证', tool: 'cache_write',
    desc: '将任务中间态存入高速缓存，防止因系统意外中断导致丢失',
    icon: '📌', rarity: 'blue',
  },
  {
    id: 38, species: 'NURSE', name: '意图导流', tool: 'semantic_router',
    desc: '根据语义直接命中对应的知识库分支，提升检索命中率',
    icon: '🎯', rarity: 'purple',
  },
  {
    id: 39, species: 'NURSE', name: '全域索引', tool: 'local_indexer',
    desc: '对本地所有文件建立关键词倒排索引，实现毫秒级全文搜索',
    icon: '🗂️', rarity: 'blue',
  },

  // ── SCRIBE (40-48): 文档生成 / 语言处理 ────────────────────────────────
  {
    id: 40, species: 'SCRIBE', name: '排版转存', tool: 'md_to_pdf',
    desc: '将Markdown格式的草稿渲染为排版专业的PDF正式公文',
    icon: '📄', rarity: 'blue',
  },
  {
    id: 41, species: 'SCRIBE', name: '表格提取', tool: 'csv_parser',
    desc: '解析混乱的CSV或表格数据，统一转化为标准化的JSON数组',
    icon: '📊', rarity: 'blue',
  },
  {
    id: 42, species: 'SCRIBE', name: '数据导出', tool: 'excel_writer',
    desc: '将内存中的结构化数据快速导出为本地Excel报表',
    icon: '📈', rarity: 'blue',
  },
  {
    id: 43, species: 'SCRIBE', name: '同步云端', tool: 'notion_sync',
    desc: '将本地成果一键同步至Notion等云端协同工具',
    icon: '☁️', rarity: 'purple',
  },
  {
    id: 44, species: 'SCRIBE', name: '万语互译', tool: 'translator',
    desc: '利用长文本翻译引擎，实现全语种的专业级公文互译',
    icon: '🌏', rarity: 'purple',
  },
  {
    id: 45, species: 'SCRIBE', name: '纠错润色', tool: 'grammar_check',
    desc: '深度扫描文本中的语法与逻辑缺陷，进行自动化风格优化',
    icon: '✅', rarity: 'blue',
  },
  {
    id: 46, species: 'SCRIBE', name: '格式转换', tool: 'format_converter',
    desc: '在JSON、YAML、XML等多种配置语言间进行无损互转',
    icon: '🔄', rarity: 'blue',
  },
  {
    id: 47, species: 'SCRIBE', name: '影像识文', tool: 'ocr_tool',
    desc: '通过OCR技术提取图片、扫描件中的文字，让纸面数据数字化',
    icon: '🖼️', rarity: 'purple',
  },
  {
    id: 48, species: 'SCRIBE', name: '函件拟定', tool: 'email_gen',
    desc: '基于当前业务上下文，自动拟定得体、专业的商务邮件正文',
    icon: '✉️', rarity: 'blue',
  },

  // ── SOLDIER (49-56): 安全扫描 / 防御拦截 ───────────────────────────────
  {
    id: 49, species: 'SOLDIER', name: '密钥清查', tool: 'secret_scanner',
    desc: '全量扫描代码库，防止API Key、密码等敏感信息误传泄露',
    icon: '🔑', rarity: 'purple',
  },
  {
    id: 50, species: 'SOLDIER', name: '恶意过滤', tool: 'prompt_filter',
    desc: '自动拦截注入攻击和越狱指令，守卫蜂巢的中枢逻辑安全',
    icon: '🛡️', rarity: 'gold',
  },
  {
    id: 51, species: 'SOLDIER', name: '漏洞审计', tool: 'dep_audit',
    desc: '对项目依赖包进行安全普查，识别并预警已知的CVE漏洞',
    icon: '🔬', rarity: 'purple',
  },
  {
    id: 52, species: 'SOLDIER', name: '权限鉴别', tool: 'auth_checker',
    desc: '严格验证当前操作人的权限等级，严禁任何越权指令执行',
    icon: '🔐', rarity: 'purple',
  },
  {
    id: 53, species: 'SOLDIER', name: '频率管控', tool: 'rate_limiter',
    desc: '对外部API调用实施令牌桶限流，防止因超额访问被封禁账号',
    icon: '🚦', rarity: 'blue',
  },
  {
    id: 54, species: 'SOLDIER', name: '日志查杀', tool: 'log_scanner',
    desc: '深度扫描系统日志，利用模式识别发现潜藏的异常入侵迹象',
    icon: '🔍', rarity: 'purple',
  },
  {
    id: 55, species: 'SOLDIER', name: '注入拦截', tool: 'sql_safe_tester',
    desc: '在SQL执行前进行语法预审，彻底根除SQL注入风险',
    icon: '💉', rarity: 'gold',
  },
  {
    id: 56, species: 'SOLDIER', name: '隐私脱敏', tool: 'pii_anonymizer',
    desc: '对姓名、电话等个人隐私数据进行自动遮蔽，符合合规要求',
    icon: '🙈', rarity: 'purple',
  },

  // ── MEDIC (57-64): 系统监控 / 健康检测 ─────────────────────────────────
  {
    id: 57, species: 'MEDIC', name: '负载监视', tool: 'resource_monitor',
    desc: '实时上报蜂巢底层的CPU、内存占用情况，预警硬件瓶颈',
    icon: '💻', rarity: 'blue',
  },
  {
    id: 58, species: 'MEDIC', name: '时延测试', tool: 'latency_check',
    desc: '测量与核心服务器间的网络往返时间，优化数据传输路径',
    icon: '📡', rarity: 'blue',
  },
  {
    id: 59, species: 'MEDIC', name: '存活预检', tool: 'api_health',
    desc: '定期探测第三方API接口状态，确保外部工具链链路畅通',
    icon: '🩺', rarity: 'blue',
  },
  {
    id: 60, species: 'MEDIC', name: '堆栈诊断', tool: 'error_analyzer',
    desc: '在系统崩溃时自动捕获异常堆栈，输出人性化的故障分析',
    icon: '💥', rarity: 'purple',
  },
  {
    id: 61, species: 'MEDIC', name: '容量清点', tool: 'disk_checker',
    desc: '监控本地磁盘余量，防止因空间不足导致的任务写入失败',
    icon: '💾', rarity: 'blue',
  },
  {
    id: 62, species: 'MEDIC', name: '链接校检', tool: 'link_checker',
    desc: '全量扫描文档中的URL，标记并清理失效的"死链"',
    icon: '🔗', rarity: 'blue',
  },
  {
    id: 63, species: 'MEDIC', name: '进程清理', tool: 'process_killer',
    desc: '强制终止执行超时的僵尸进程，释放系统被占用的资源',
    icon: '☠️', rarity: 'gold', dangerous: true,
  },
  {
    id: 64, species: 'MEDIC', name: '工具核查', tool: 'env_validator',
    desc: '校验Node、Python等底层运行环境的版本，确保环境一致性',
    icon: '✅', rarity: 'blue',
  },

  // ── PAINTER (65-72): 视觉创作 / 图像工具 ───────────────────────────────
  {
    id: 65, species: 'PAINTER', name: '意向绘图', tool: 'sd_dalle_api',
    desc: '将文字描述转化为高质量插画或海报，赋予思想以视觉形态',
    icon: '🎨', rarity: 'gold',
  },
  {
    id: 66, species: 'PAINTER', name: '智能背景', tool: 'bg_remover',
    desc: '利用AI模型精准识别并移除图片背景，实现全自动抠图',
    icon: '✂️', rarity: 'purple',
  },
  {
    id: 67, species: 'PAINTER', name: '画幅裁切', tool: 'img_resizer',
    desc: '根据目标平台需求，智能调整图片尺寸并保持核心构图',
    icon: '📐', rarity: 'blue',
  },
  {
    id: 68, species: 'PAINTER', name: '色调配比', tool: 'palette_gen',
    desc: '根据品牌调性提取并生成UI配色方案，提供十六进制色码',
    icon: '🎨', rarity: 'purple',
  },
  {
    id: 69, species: 'PAINTER', name: '矢量绘制', tool: 'svg_renderer',
    desc: '通过生成代码来绘制无限缩放的SVG矢量图形',
    icon: '✏️', rarity: 'purple',
  },
  {
    id: 70, species: 'PAINTER', name: '视觉描述', tool: 'vision_cap',
    desc: '充当蜂巢的"眼睛"，通过大语言模型解读图片中的深层意蕴',
    icon: '👁️', rarity: 'purple',
  },
  {
    id: 71, species: 'PAINTER', name: '码图生成', tool: 'qr_generator',
    desc: '将文本或链接编码为高辨识度的二维码图片',
    icon: '◼️', rarity: 'blue',
  },
  {
    id: 72, species: 'PAINTER', name: '图表渲染', tool: 'chart_renderer',
    desc: '将枯燥的统计数据转化为精美的饼图、趋势图或雷达图',
    icon: '📊', rarity: 'purple',
  },

  // ── EDITOR (73-79): 音频处理 / 视频剪辑 ────────────────────────────────
  {
    id: 73, species: 'EDITOR', name: '语音转文', tool: 'audio_to_text',
    desc: '采用高精度Whisper模型，将长段音频转化为带时间轴的文本',
    icon: '🎙️', rarity: 'purple',
  },
  {
    id: 74, species: 'EDITOR', name: '声语合成', tool: 'tts_engine',
    desc: '将文本文稿合成多种情绪、多种音色的自然人声语音',
    icon: '🗣️', rarity: 'purple',
  },
  {
    id: 75, species: 'EDITOR', name: '音轨转码', tool: 'audio_converter',
    desc: '在无损前提下进行音频格式批量转换，适配不同播放场景',
    icon: '🔊', rarity: 'blue',
  },
  {
    id: 76, species: 'EDITOR', name: '视频剪裁', tool: 'video_trimmer',
    desc: '通过时间戳指令精准截取视频高光片段，并自动重新封装',
    icon: '✂️', rarity: 'purple',
  },
  {
    id: 77, species: 'EDITOR', name: '逐帧提取', tool: 'frame_extractor',
    desc: '每隔固定秒数抽取视频关键帧，用于视觉分析或封面生成',
    icon: '🎞️', rarity: 'blue',
  },
  {
    id: 78, species: 'EDITOR', name: '轴表压制', tool: 'subtitle_gen',
    desc: '自动生成符合标准的SRT字幕文件，实现音画同步对齐',
    icon: '📝', rarity: 'purple',
  },
  {
    id: 79, species: 'EDITOR', name: '媒体属性', tool: 'media_meta',
    desc: '提取音视频文件的比特率、帧率、编码格式等底层元数据',
    icon: '📊', rarity: 'blue',
  },

  // ── INFLUENCER (80-88): 内容分发 / 营销优化 ─────────────────────────────
  {
    id: 80, species: 'INFLUENCER', name: '电邮投递', tool: 'smtp_sender',
    desc: '通过SMTP协议向指定目标发送带有附件的真实电子邮件',
    icon: '📧', rarity: 'blue',
  },
  {
    id: 81, species: 'INFLUENCER', name: '频道播报', tool: 'slack_notifier',
    desc: '向Slack指定频道发送格式化的富文本通知或报警消息',
    icon: '💬', rarity: 'blue',
  },
  {
    id: 82, species: 'INFLUENCER', name: '钩子推报', tool: 'discord_hook',
    desc: '利用Webhook向Discord推送美观的内容展示卡片',
    icon: '🎮', rarity: 'blue',
  },
  {
    id: 83, species: 'INFLUENCER', name: '动态发布', tool: 'sns_publisher',
    desc: '调用社交平台API自动同步推文或动态，进行全网同步',
    icon: '📱', rarity: 'purple', dangerous: true,
  },
  {
    id: 84, species: 'INFLUENCER', name: '词频调优', tool: 'seo_optimizer',
    desc: '分析文稿的SEO得分与关键词密度，优化搜索引擎自然排名',
    icon: '🔍', rarity: 'purple',
  },
  {
    id: 85, species: 'INFLUENCER', name: '标签聚合', tool: 'hashtag_gen',
    desc: '根据文章语义自动关联最热门的标签，增加曝光维度',
    icon: '#️⃣', rarity: 'blue',
  },
  {
    id: 86, species: 'INFLUENCER', name: '爆款标题', tool: 'headline_gen',
    desc: '利用心理学模型润色标题，生成多版本高点击率的标题候选',
    icon: '📰', rarity: 'purple',
  },
  {
    id: 87, species: 'INFLUENCER', name: '网页生成', tool: 'md_to_html',
    desc: '将MD成果一键转化为符合主流博客框架的HTML静态页面',
    icon: '🌐', rarity: 'blue',
  },
  {
    id: 88, species: 'INFLUENCER', name: '链接压缩', tool: 'url_shortener',
    desc: '生成极简短链接，方便在受限环境下传播并统计点击量',
    icon: '🔗', rarity: 'blue',
  },

  // ── SENTINEL (89-96): 定时调度 / 事件监控 ──────────────────────────────
  {
    id: 89, species: 'SENTINEL', name: '周期计划', tool: 'cron_scheduler',
    desc: '注册Cron定时任务，实现蜂巢内部的自动化日常例行巡检',
    icon: '⏰', rarity: 'purple',
  },
  {
    id: 90, species: 'SENTINEL', name: '延时待命', tool: 'wait_timer',
    desc: '让当前执行逻辑进入休眠状态，在指定时间后自动唤醒续作',
    icon: '⏳', rarity: 'blue',
  },
  {
    id: 91, species: 'SENTINEL', name: '文件哨探', tool: 'file_watcher',
    desc: '实时监听特定文件夹，一旦有新文件进入立即触发后续流',
    icon: '👀', rarity: 'blue',
  },
  {
    id: 92, species: 'SENTINEL', name: '回执监听', tool: 'webhook_receiver',
    desc: '开启本地端口，被动接收外部系统的HTTP回调通知',
    icon: '🔔', rarity: 'blue',
  },
  {
    id: 93, species: 'SENTINEL', name: '时区对齐', tool: 'tz_converter',
    desc: '处理跨国跨区域的任务执行时间，自动换算夏令时偏差',
    icon: '🕐', rarity: 'blue',
  },
  {
    id: 94, species: 'SENTINEL', name: '价格预警', tool: 'price_watcher',
    desc: '定时嗅探特定商品或云服务价格，达到阈值即刻触发提醒',
    icon: '💰', rarity: 'purple',
  },
  {
    id: 95, species: 'SENTINEL', name: '运行统计', tool: 'uptime_stats',
    desc: '汇总蜂巢自启动以来的在线时长与任务吞吐量数据',
    icon: '📊', rarity: 'blue',
  },
  {
    id: 96, species: 'SENTINEL', name: '事件广播', tool: 'event_emitter',
    desc: '在系统内部发布状态信号，协调多个蜜蜂协同完成跨步任务',
    icon: '📢', rarity: 'purple',
  },

  // ── UTILITY (97-100): 通用工具 ──────────────────────────────────────────
  {
    id: 97, species: 'UTILITY', name: '唯一确权', tool: 'uuid_gen',
    desc: '生成符合标准的UUID/ULID，为蜂巢内的每个对象打上唯一标识',
    icon: '🆔', rarity: 'blue',
  },
  {
    id: 98, species: 'UTILITY', name: '指纹校验', tool: 'hash_calc',
    desc: '计算字符串或文件的MD5/SHA256哈希值，确保数据未被篡改',
    icon: '🔑', rarity: 'blue',
  },
  {
    id: 99, species: 'UTILITY', name: '编码解密', tool: 'codec_tool',
    desc: '处理Base64、URL、Unicode等多种二进制数据与文本间的互转',
    icon: '🔤', rarity: 'blue',
  },
  {
    id: 100, species: 'UTILITY', name: '精准算力', tool: 'math_engine',
    desc: '接管模型脆弱的口算逻辑，执行极其复杂的数学表达式运算',
    icon: '➗', rarity: 'purple',
  },
]

// ══════════════════════════════════════════════════════════════════════════════
// Slot layout config per species
// ══════════════════════════════════════════════════════════════════════════════

export const SPECIES_STINGER_GROUPS = {
  QUEEN: [
    { group: '调度决策', ids: [1, 2, 3, 4] },
    { group: '系统管控', ids: [5, 6, 7, 8] },
  ],
  SCOUT: [
    { group: '实时情报', ids: [9, 10, 11, 12, 13] },
    { group: '深度搜索', ids: [14, 15, 16, 17, 18] },
  ],
  WORKER: [
    { group: '文件操作', ids: [19, 20, 21, 22] },
    { group: '代码执行', ids: [23, 24, 25, 28] },
    { group: '工程工具', ids: [26, 27, 29, 30] },
  ],
  NURSE: [
    { group: '记忆存储', ids: [31, 32, 33, 37, 38] },
    { group: '数据检索', ids: [34, 35, 36, 39] },
  ],
  SCRIBE: [
    { group: '文档生成', ids: [40, 41, 42, 43, 47] },
    { group: '语言处理', ids: [44, 45, 46, 48] },
  ],
  SOLDIER: [
    { group: '安全扫描', ids: [49, 50, 51, 52] },
    { group: '防御拦截', ids: [53, 54, 55, 56] },
  ],
  MEDIC: [
    { group: '系统监控', ids: [57, 58, 59, 60] },
    { group: '健康检测', ids: [61, 62, 63, 64] },
  ],
  PAINTER: [
    { group: '视觉创作', ids: [65, 66, 67, 68] },
    { group: '图像工具', ids: [69, 70, 71, 72] },
  ],
  EDITOR: [
    { group: '音频处理', ids: [73, 74, 75] },
    { group: '视频剪辑', ids: [76, 77, 78, 79] },
  ],
  INFLUENCER: [
    { group: '内容分发', ids: [80, 81, 82, 83] },
    { group: '营销优化', ids: [84, 85, 86, 87, 88] },
  ],
  SENTINEL: [
    { group: '定时调度', ids: [89, 90, 91, 92] },
    { group: '事件监控', ids: [93, 94, 95, 96] },
  ],
}

// ══════════════════════════════════════════════════════════════════════════════
// Lookup helpers
// ══════════════════════════════════════════════════════════════════════════════

/** Build an id → stinger index for O(1) lookups. */
const _indexById = new Map(STINGER_REGISTRY.map(s => [s.id, s]))

/**
 * Get a stinger by its numeric ID.
 * @param {number} id
 * @returns {Stinger|undefined}
 */
export function getById(id) {
  return _indexById.get(id)
}

/**
 * Return stingers available to a given species.
 * - QUEEN  → all 100 stingers (full access)
 * - UTILITY → only UTILITY stingers
 * - others  → own species stingers + UTILITY stingers
 *
 * @param {string} speciesKey
 * @returns {Stinger[]}
 */
export function getBySpecies(speciesKey) {
  const key = speciesKey.toUpperCase()
  if (key === 'QUEEN') return [...STINGER_REGISTRY]
  if (key === 'ALL')   return [...STINGER_REGISTRY]
  if (key === 'UTILITY') return STINGER_REGISTRY.filter(s => s.species === 'UTILITY')
  return STINGER_REGISTRY.filter(s => s.species === key || s.species === 'UTILITY')
}

/**
 * Search stingers by a query string across name, desc, and tool fields.
 * Optionally filter by species (pass 'ALL' to skip species filtering).
 *
 * @param {string} query
 * @param {string} [speciesKey='ALL']
 * @returns {Stinger[]}
 */
export function searchStingers(query, speciesKey = 'ALL') {
  const q = (query || '').trim().toLowerCase()
  let list = speciesKey === 'ALL' ? [...STINGER_REGISTRY] : getBySpecies(speciesKey)
  if (!q) return list
  return list.filter(s =>
    s.name.toLowerCase().includes(q) ||
    s.desc.toLowerCase().includes(q) ||
    s.tool.toLowerCase().includes(q)
  )
}
