<template>
  <div class="panel" :class="{ active: store.activePanel === 'soul' }" id="panel-soul">

    <!-- Header -->
    <div class="soul-header">
      <div class="soul-header-left">
        <span class="soul-glyph">🧬</span>
        <div>
          <div class="soul-title">{{ t('soul_title') }}</div>
          <div class="soul-subtitle">{{ store.currentHive?.name || t('soul_subtitle_default') }} — {{ t('soul_subtitle_suffix') }}</div>
        </div>
      </div>
      <button class="soul-refresh-btn" @click="load">↻ {{ t('refresh') }}</button>
    </div>

    <div class="soul-body">

      <!-- LEFT: 三层意识区 -->
      <div class="soul-left">
        <div v-if="!store.currentHive" class="soul-no-hive">
          <div class="no-hive-icon">🫙</div>
          <div>{{ t('select_hive_soul') }}</div>
        </div>

        <template v-else>

          <!-- 核心本能 -->
          <div class="soul-block block-directives">
            <div class="soul-block-hd">
              <span class="block-led led-gold"></span>
              <span class="block-label">{{ t('core_directives') }}</span>
              <span class="block-sublabel">Core Directives · 高层约束与价值观</span>
            </div>
            <textarea
              v-model="directives"
              class="soul-textarea"
              placeholder="例：永远诚实 · 以用户利益为先 · 保持幽默感…"
            ></textarea>
          </div>

          <!-- 长期记忆 -->
          <div class="soul-block block-memory">
            <div class="soul-block-hd">
              <span class="block-led led-teal"></span>
              <span class="block-label">{{ t('long_term_memory') }}</span>
              <span class="block-sublabel">Long-term Memory · RAG 上下文与持久知识</span>
            </div>
            <textarea
              v-model="memory"
              class="soul-textarea"
              placeholder="例：用户偏好记录 · 历史项目背景 · 关键事实…"
            ></textarea>
          </div>

          <!-- 身份叙事 -->
          <div class="soul-block block-persona">
            <div class="soul-block-hd">
              <span class="block-led led-amber"></span>
              <span class="block-label">{{ t('persona_narrative') }}</span>
              <span class="block-sublabel">Persona Narrative · 角色背景与人格特质</span>
            </div>
            <textarea
              v-model="persona"
              class="soul-textarea"
              placeholder="例：你是一位来自未来的蜂巢守护者，性格沉稳却充满好奇…"
            ></textarea>
          </div>

          <!-- 保存按钮 -->
          <div class="soul-save-row">
            <button class="soul-save-btn" @click="save">
              <span>💾</span> {{ t('save_soul') }}
            </button>
          </div>

        </template>
      </div>

      <!-- RIGHT: 神经流日志 -->
      <div class="soul-log-panel">
        <div class="log-panel-hd">
          <span class="log-hd-dot"></span>
          <span class="log-hd-title">{{ t('neural_stream') }}</span>
          <span class="log-hd-sub">NEURAL STREAM</span>
        </div>
        <div class="log-stream" ref="streamEl">
          <div
            v-for="(line, idx) in logs"
            :key="idx"
            class="log-line"
            :class="line.cssClass"
          >
            <span class="log-ts">{{ logTimestamp(idx) }}</span>
            <span class="log-text">{{ line.text }}</span>
          </div>
          <div v-if="!logs.length" class="log-idle">
            <span class="log-idle-cursor">▋</span> {{ t('waiting_consciousness') }}
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { useWebSocket } from '../../composables/useWebSocket.js'

const store = useAppStore()
const { t } = useI18n()
const api = useApi()
const { logs } = useWebSocket()

const emit = defineEmits(['toast'])

// 三层意识
const directives = ref('')
const memory     = ref('')
const persona    = ref('')
const streamEl   = ref(null)

const SECTION_SEP = '\n\n---SECTION---\n\n'

watch(() => store.activeHiveId, load, { immediate: true })
watch(() => store.activePanel, (p) => { if (p === 'soul') load() })

watch(logs, async () => {
  await nextTick()
  if (streamEl.value) streamEl.value.scrollTop = streamEl.value.scrollHeight
}, { deep: true })

async function load() {
  const hive = store.currentHive
  if (!hive) { directives.value = ''; memory.value = ''; persona.value = ''; return }
  try {
    const raw = await api.loadHiveNotes(hive.hiveId)
    const parts = (raw || '').split(SECTION_SEP)
    directives.value = parts[0] || ''
    memory.value     = parts[1] || ''
    persona.value    = parts[2] || ''
  } catch {
    directives.value = ''; memory.value = ''; persona.value = ''
  }
}

async function save() {
  const hive = store.currentHive
  if (!hive) return
  try {
    const combined = [directives.value, memory.value, persona.value].join(SECTION_SEP)
    await api.saveHiveNotes(hive.hiveId, combined)
    emit('toast', { message: '意志基石已刻入', type: 'success' })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  }
}

// 给日志生成伪时间戳（增强仪式感）
function logTimestamp(idx) {
  const base = Date.now() - (logs.value.length - idx) * 1200
  const d = new Date(base)
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
}

defineExpose({ load })
</script>

<style scoped>
/* ═══════════════════════════════════════════
   面板根：暖象牙基底
═══════════════════════════════════════════ */
#panel-soul {
  background:
    radial-gradient(ellipse at 10% 20%, rgba(245,166,35,.09) 0%, transparent 50%),
    radial-gradient(ellipse at 90% 80%, rgba(255,200,80,.06) 0%, transparent 50%),
    linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
  color: #2C2724;
}

/* ═══════════════════════════════════════════
   Header
═══════════════════════════════════════════ */
.soul-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 22px;
  border-bottom: 1px solid rgba(245,166,35,0.18);
  background: rgba(255,253,248,0.85);
  backdrop-filter: blur(12px);
  flex-shrink: 0;
}
.soul-header-left { display: flex; align-items: center; gap: 12px; }
.soul-glyph { font-size: 1.6rem; }
.soul-title {
  font-size: .92rem;
  font-weight: 800;
  letter-spacing: .06em;
  color: #2C2724;
}
.soul-subtitle {
  font-size: .7rem;
  color: #B5A898;
  margin-top: 2px;
}
.soul-refresh-btn {
  background: rgba(245,166,35,0.10);
  border: 1px solid rgba(245,166,35,0.30);
  color: #D97706;
  font-size: .78rem;
  font-weight: 600;
  padding: 5px 14px;
  border-radius: 99px;
  cursor: pointer;
  transition: all .15s;
}
.soul-refresh-btn:hover {
  background: rgba(245,166,35,0.20);
}

/* ═══════════════════════════════════════════
   Body
═══════════════════════════════════════════ */
.soul-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

/* ═══════════════════════════════════════════
   LEFT — 三层意识区
═══════════════════════════════════════════ */
.soul-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 22px;
  overflow-y: auto;
  border-right: 1px solid rgba(245,166,35,0.15);
  max-width: 850px;
}

.soul-left::-webkit-scrollbar { width: 3px; }
.soul-left::-webkit-scrollbar-thumb { background: rgba(245,166,35,0.25); border-radius: 3px; }

.soul-no-hive {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #B5A898;
  font-size: .85rem;
}
.no-hive-icon { font-size: 2.5rem; opacity: .4; }

/* 意识块通用 */
.soul-block {
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  transition: box-shadow .2s;
}
.soul-block:hover { box-shadow: 0 4px 24px rgba(180,130,50,.12); }

/* 核心本能 — 金色 */
.block-directives {
  background: rgba(255, 248, 220, 0.70);
  border: 1px solid rgba(245, 166, 35, 0.35);
  box-shadow: 0 2px 16px rgba(245,166,35,.08), inset 0 1px 0 rgba(255,255,255,.7);
}
/* 长期记忆 — 青色 */
.block-memory {
  background: rgba(220, 252, 248, 0.65);
  border: 1px solid rgba(72, 209, 204, 0.35);
  box-shadow: 0 2px 16px rgba(72,209,204,.08), inset 0 1px 0 rgba(255,255,255,.7);
}
/* 身份叙事 — 紫金 */
.block-persona {
  background: rgba(250, 240, 255, 0.65);
  border: 1px solid rgba(168, 85, 247, 0.28);
  box-shadow: 0 2px 16px rgba(168,85,247,.08), inset 0 1px 0 rgba(255,255,255,.7);
}

.soul-block-hd {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* LED 指示灯 */
.block-led {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  animation: led-pulse 2s ease-in-out infinite;
}
.led-gold   { background: #F59E0B; box-shadow: 0 0 6px #F59E0B; }
.led-teal   { background: #14B8A6; box-shadow: 0 0 6px #14B8A6; }
.led-amber  { background: #A855F7; box-shadow: 0 0 6px #A855F7; }
@keyframes led-pulse {
  0%,100% { opacity: 1; transform: scale(1); }
  50%     { opacity: .5; transform: scale(.8); }
}

.block-label {
  font-size: .8rem;
  font-weight: 800;
  color: #2C2724;
  letter-spacing: .04em;
}
.block-sublabel {
  font-size: .65rem;
  color: #B5A898;
  flex: 1;
}

.soul-textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 88px;
  resize: vertical;
  background: rgba(255,255,255,0.60);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(245,166,35,0.30);
  border-radius: 8px;
  padding: 10px 12px;
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  font-size: .78rem;
  line-height: 1.65;
  color: #2C2724;
  outline: none;
  transition: border-color .15s, box-shadow .15s;
}
.soul-textarea:focus {
  border-color: rgba(245,166,35,.5);
  box-shadow: 0 0 0 3px rgba(245,166,35,.10);
}
.soul-textarea::placeholder { color: #C5B5A0; }

/* 保存按钮 */
.soul-save-row { display: flex; justify-content: flex-end; }
.soul-save-btn {
  background: linear-gradient(135deg, #D97706 0%, #F59E0B 50%, #FCD34D 100%);
  color: #1a0c00;
  font-weight: 800;
  font-size: .88rem;
  letter-spacing: .04em;
  border: none;
  border-radius: 99px;
  padding: 10px 32px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 18px rgba(245,158,11,.40);
  transition: all .2s;
}
.soul-save-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 28px rgba(245,158,11,.60);
}

/* ═══════════════════════════════════════════
   RIGHT — 内部独白（暖象牙羊皮纸风格）
═══════════════════════════════════════════ */
.soul-log-panel {
  width: 350px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(255, 252, 244, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-left: 1px solid rgba(245,166,35,0.20);
}

.log-panel-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(245,166,35,0.18);
  background: rgba(255, 249, 235, 0.80);
  flex-shrink: 0;
}
.log-hd-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #F59E0B;
  box-shadow: 0 0 6px rgba(245,166,35,0.6);
  animation: led-pulse 1.5s ease-in-out infinite;
}
.log-hd-title {
  font-size: .72rem;
  font-weight: 800;
  color: #92680A;
  letter-spacing: .04em;
}
.log-hd-sub {
  font-size: .55rem;
  color: rgba(180,140,60,0.50);
  letter-spacing: .12em;
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
}

.log-stream {
  flex: 1;
  overflow-y: auto;
  padding: 12px 18px;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}
.log-stream::-webkit-scrollbar { width: 3px; }
.log-stream::-webkit-scrollbar-thumb { background: rgba(245,166,35,0.25); border-radius: 3px; }

.log-line {
  display: flex;
  gap: 10px;
  padding: 3px 0;
  font-size: .67rem;
  line-height: 1.65;
  color: rgba(100, 75, 30, 0.60);
  transition: color .1s, background .1s;
  border-radius: 4px;
}
.log-line:hover { color: rgba(100, 75, 30, 0.90); background: rgba(245,166,35,0.06); }
.log-ts {
  flex-shrink: 0;
  color: rgba(180,140,60,0.40);
  font-size: .58rem;
  padding-top: 1px;
}
.log-text { flex: 1; word-break: break-word; }

/* 日志颜色分类 — 暖色系 */
.log-line.sys   { color: rgba(146, 104, 10, 0.70); }
.log-line.info  { color: rgba(20, 140, 100, 0.80); }
.log-line.warn  { color: rgba(200, 120, 10, 0.85); }
.log-line.error { color: rgba(200, 50, 50, 0.80); }

.log-idle {
  padding: 28px 4px;
  font-size: .68rem;
  color: rgba(180,140,60,0.35);
  font-family: 'JetBrains Mono', monospace;
  display: flex;
  align-items: center;
  gap: 6px;
  font-style: italic;
}
.log-idle-cursor {
  color: rgba(245,166,35,0.55);
  animation: blink .8s step-end infinite;
}
@keyframes blink {
  0%,100% { opacity: 1; }
  50%     { opacity: 0; }
}
</style>
