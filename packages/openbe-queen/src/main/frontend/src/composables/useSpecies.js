/**
 * useSpecies.js
 * Shared composable for bee-species metadata, emoji, color helpers.
 * Consolidates SPECIES_META / SPECIES_EMOJI / AVATAR_MAP definitions
 * that were previously duplicated across BeesPanel, WillPanel, ChatPanel
 * and SkillsPanel.
 */

// ── Unified species registry ──────────────────────────────────────────────────
// Keys are always UPPER_CASE to match the backend beeType/displaySpecies field.
// Colors follow 1.md spec: Queen=#FFD700, Painter=#F8BBD0, Medic=#B2DFDB, Scout=#B3E5FC
export const SPECIES_META = {
  QUEEN:      { emoji: '👑',  color: 'rgba(255,215,0,0.15)',  glow: '#FFD700', label: '蜂王',   desc: '调度与决策核心，全局统筹·意图路由·按需孵化。' },
  SCOUT:      { emoji: '🔭',  color: 'rgba(179,229,252,0.35)',glow: '#03A9F4', label: '侦查蜂',  desc: '全网搜索·网页攀爬·热点追踪·论文搜寻。' },
  WORKER:     { emoji: '🐝',  color: 'rgba(245,158,11,0.15)', glow: '#F59E0B', label: '工蜂',   desc: '文件读写·脚本执行·源码克隆·测试生成。' },
  NURSE:      { emoji: '🍯',  color: 'rgba(168,85,247,0.12)', glow: '#A855F7', label: '护蜂',   desc: '向量检索·语义切片·SQL查询·全域索引。' },
  SCRIBE:     { emoji: '📝',  color: 'rgba(234,179,8,0.12)',  glow: '#EAB308', label: '文书蜂',  desc: '格式转换·万语互译·OCR识别·函件拟定。' },
  SOLDIER:    { emoji: '🪖',  color: 'rgba(239,68,68,0.12)',  glow: '#EF4444', label: '兵蜂',   desc: '密钥清查·Prompt过滤·漏洞审计·隐私脱敏。' },
  MEDIC:      { emoji: '🏥',  color: 'rgba(178,223,219,0.40)',glow: '#009688', label: '医护蜂',  desc: '负载监视·存活预检·堆栈诊断·进程清理。' },
  PAINTER:    { emoji: '🎨',  color: 'rgba(248,187,208,0.40)',glow: '#E91E8C', label: '画师蜂',  desc: '意向绘图·智能抠图·SVG绘制·图表渲染。' },
  EDITOR:     { emoji: '🎬',  color: 'rgba(59,130,246,0.12)', glow: '#60A5FA', label: '剪辑蜂',  desc: 'ASR转写·TTS合成·视频剪裁·字幕压制。' },
  INFLUENCER: { emoji: '📱',  color: 'rgba(251,113,133,0.12)',glow: '#F472B6', label: '博主蜂',  desc: '平台推送·SEO分析·爆款标题·网页生成。' },
  SENTINEL:   { emoji: '🔔',  color: 'rgba(245,158,11,0.12)', glow: '#F97316', label: '哨兵蜂',  desc: 'Cron计划·文件哨探·Webhook监听·事件广播。' },
  MECHANIC:   { emoji: '🔩',  color: 'rgba(99,102,241,0.12)', glow: '#3B82F6', label: '机械蜂',  desc: 'Shell执行·进程管理·环境配置·系统自动化。' },
}

// ── Standalone helper functions ───────────────────────────────────────────────

/** Return the emoji for a species type string (upper-case). Falls back to 🐝. */
export function speciesEmoji(type) {
  return SPECIES_META[(type || '').toUpperCase()]?.emoji || '🐝'
}

/** Return the background/card color for a species type string. */
export function speciesColor(type) {
  return SPECIES_META[(type || '').toUpperCase()]?.color || 'transparent'
}

/** Return the glow/accent color for a species type string. */
export function speciesGlow(type) {
  return SPECIES_META[(type || '').toUpperCase()]?.glow || '#F59E0B'
}

/**
 * Infer the species label (upper-case key) from a bee runtime object.
 * Priority: displaySpecies > beeId prefix > beeType/type fallback.
 */
export function speciesLabel(bee) {
  const ds = (bee.displaySpecies || '').toUpperCase()
  if (ds && SPECIES_META[ds]) return ds

  const beeId = (bee.beeId || '').trim()
  if (beeId && beeId.includes('-')) {
    const prefix = beeId.split('-')[0].toUpperCase()
    if (SPECIES_META[prefix]) return prefix
  }

  return (bee.beeType || bee.type || 'WORKER').toUpperCase()
}

// ── Composable ────────────────────────────────────────────────────────────────

/** Return the Chinese label for a species type string. */
export function speciesChineseLabel(type) {
  return SPECIES_META[(type || '').toUpperCase()]?.label || type || '蜜蜂'
}

export function useSpecies() {
  return {
    SPECIES_META,
    speciesEmoji,
    speciesColor,
    speciesGlow,
    speciesLabel,
    speciesChineseLabel,
  }
}
