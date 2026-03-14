<template>
  <div class="panel" :class="{ active: store.activePanel === 'will' }" id="panel-will">

    <!-- ── Header ── -->
    <div class="will-header">
      <div class="will-header-left">
        <span class="will-glyph">💉</span>
        <div>
          <div class="will-title">{{ t('will_title') }}</div>
          <div class="will-subtitle">{{ store.currentHive?.name || t('will_subtitle_default') }} — {{ t('will_subtitle_suffix') }}</div>
        </div>
      </div>
      <button class="will-refresh-btn" @click="reload">↻ {{ t('refresh') }}</button>
    </div>

    <!-- ── Body ── -->
    <div class="will-body">

      <div v-if="!store.currentHive" class="will-empty">
        <div class="will-empty-icon">🫙</div>
        <div>{{ t('select_hive_hint') }}</div>
      </div>

      <template v-else>

        <!-- LEFT: 蜜蜂选择器 -->
        <div class="will-bee-list">
          <div class="will-bl-hd">
            <span class="will-bl-dot"></span>
            <span class="will-bl-title">{{ t('select_bee_label') }}</span>
          </div>

          <!-- 蜂王 -->
          <div class="will-bee-item" :class="{ active: store.activeEditingBeeKey === '__queen__' }" @click="selectBee('__queen__')">
            <span class="will-bee-icon">👑</span>
            <div class="will-bee-meta">
              <span class="will-bee-name">{{ store.currentHive.bee?.name || '蜂王' }}</span>
              <span class="will-bee-type">QUEEN</span>
            </div>
            <span class="will-bee-status online"></span>
          </div>

          <div class="will-bl-sep" v-if="nonQueenBees.length">{{ t('running_bees') }}</div>
          <div class="will-bl-empty" v-else>{{ t('no_active_bees_short') }}</div>
          <div
            v-for="bee in nonQueenBees" :key="bee._key"
            class="will-bee-item"
            :class="{ active: store.activeEditingBeeKey === bee._key }"
            @click="selectBee(bee._key, bee)"
          >
            <span class="will-bee-icon">{{ beeEmoji(bee) }}</span>
            <div class="will-bee-meta">
              <span class="will-bee-name">{{ bee.beeName || speciesLabel(bee) }}</span>
              <span class="will-bee-type">{{ speciesLabel(bee) }}</span>
            </div>
            <span class="will-bee-status" :class="bee.statusLower"></span>
          </div>
        </div>

        <!-- RIGHT: 标签页内容 -->
        <div class="will-content">

          <!-- 标签栏 -->
          <div class="will-tabs">
            <button class="will-tab" :class="{ active: tab === 'engine' }" @click="tab = 'engine'">
              {{ t('tab_engine') }}
            </button>
            <button class="will-tab" :class="{ active: tab === 'memory' }" @click="tab = 'memory'">
              {{ t('tab_memory') }}
            </button>
            <button v-if="store.activeEditingBeeKey === '__queen__'" class="will-tab" :class="{ active: tab === 'stream' }" @click="tab = 'stream'">
              {{ t('tab_stream') }}
            </button>
            <div class="will-tab-spacer"></div>
            <span v-if="saving" class="will-saving">{{ t('saving') }}</span>
          </div>

          <!-- ── Tab A: 思维引擎 ── -->
          <div class="will-tab-body" v-show="tab === 'engine'">
            <div class="will-card">
              <div class="will-card-hd">
                <span class="will-card-title">{{ currentLabel }} {{ t('engine_title_suffix') }}</span>
                <span class="will-model-badge">{{ engineForm.model || t('not_set') }}</span>
              </div>
              <div class="will-form-group">
                <label class="will-label">{{ t('bee_name') }}</label>
                <input class="will-input" v-model="engineForm.name" type="text" :placeholder="t('bee_name')">
              </div>
              <div class="will-form-group">
                <label class="will-label">Provider</label>
                <select class="will-input" v-model="engineForm.provider">
                  <option v-for="p in providerOptions" :key="p.value" :value="p.value">{{ p.label }}</option>
                </select>
              </div>
              <div class="will-form-group">
                <label class="will-label">{{ t('model') }}</label>
                <input class="will-input" v-model="engineForm.model" type="text" placeholder="e.g. gpt-4o / llama3 / deepseek-chat">
              </div>
              <div class="will-form-group" v-if="engineForm.provider !== 'ollama'">
                <label class="will-label">API Key</label>
                <input class="will-input" v-model="engineForm.apiKey" type="password"
                  :placeholder="maskedKey || '••••••••'" autocomplete="new-password">
                <div v-if="maskedKey" class="will-hint">{{ maskedKey }}</div>
              </div>
              <div class="will-form-group" v-if="engineForm.provider === 'custom'">
                <label class="will-label">Base URL</label>
                <input class="will-input" v-model="engineForm.baseUrl" type="text" placeholder="https://...">
              </div>
              <div class="will-form-group">
                <label class="will-label">{{ t('temperature') }}</label>
                <div class="will-temp-row">
                  <input class="will-input will-temp-input" v-model.number="engineForm.temperature" type="number" step="0.1" min="0" max="2">
                  <span class="will-temp-hint">{{ t('temp_hint') }}</span>
                </div>
              </div>
              <div class="will-form-group">
                <label class="will-label">{{ t('system_prompt') }}</label>
                <textarea class="will-input will-sys-prompt" v-model="engineForm.systemPrompt" placeholder="You are a helpful assistant…"></textarea>
              </div>
              <div class="will-actions">
                <button class="will-btn-ghost" @click="clearEngine">{{ t('clear') }}</button>
                <button class="will-btn-primary" @click="saveEngine" :disabled="saving">{{ t('save_engine') }}</button>
              </div>
            </div>
          </div>

          <!-- ── Tab B: 记忆刻录 ── -->
          <div class="will-tab-body will-mem-tab-body" v-show="tab === 'memory'">
            <div class="will-mem-layout">

              <!-- 文件侧栏 -->
              <div class="will-mem-sidebar">
                <div class="will-mem-sb-hd">
                  <span class="will-mem-sb-title">{{ currentLabel }}</span>
                  <span v-if="memLoading" class="will-mem-spin"></span>
                </div>
                <div v-if="!currentHiveId" class="will-mem-no-hive">{{ t('not_bound') }}</div>
                <template v-else>
                  <div
                    v-for="f in WS_FILES" :key="f"
                    class="will-mem-file"
                    :class="{ active: memFile === f }"
                    @click="selectMemFile(f)"
                  >
                    <span class="will-mem-file-icon">{{ FILE_ICONS[f] || '📄' }}</span>
                    <span class="will-mem-file-name">{{ f.replace('.md','') }}</span>
                    <span class="will-mem-file-dot" :class="fileStatus[f] || 'missing'">
                      <svg width="6" height="6" viewBox="0 0 6 6"><circle cx="3" cy="3" r="3"/></svg>
                    </span>
                  </div>
                </template>
              </div>

              <!-- 编辑区 -->
              <div class="will-mem-editor" v-if="memFile && currentHiveId">
                <div class="will-mem-editor-bar">
                  <span class="will-mem-editor-name">{{ memFile }}</span>
                  <span v-if="memFile === 'HEARTBEAT.md'" class="will-mem-live"><span class="will-mem-live-dot"></span>LIVE</span>
                  <span v-if="fileStatus[memFile] === 'missing'" class="will-mem-missing">{{ t('not_created') }}</span>
                  <button v-if="fileStatus[memFile] === 'missing'" class="will-btn-ghost will-btn-sm" @click="createDefault(memFile)">{{ t('create_default') }}</button>
                  <button v-if="memFile !== 'HEARTBEAT.md'" class="will-btn-primary will-btn-sm" @click="saveMemFile" :disabled="memSaving">
                    <span v-if="memSaving">···</span><span v-else>{{ t('save_file') }} <kbd>⌘S</kbd></span>
                  </button>
                </div>
                <textarea
                  class="will-mem-textarea"
                  v-model="memContent"
                  :readonly="memFile === 'HEARTBEAT.md'"
                  :placeholder="memFile === 'HEARTBEAT.md' ? t('waiting_heartbeat') : t('edit_file_hint') + memFile"
                  @keydown="onMemKeydown"
                ></textarea>
              </div>
              <div class="will-mem-placeholder" v-else-if="currentHiveId">
                <div class="will-ph-icon">📜</div>
                <div class="will-ph-text">{{ t('pick_file_hint') }}</div>
              </div>

            </div>
          </div>

          <!-- ── Tab C: 意识流（仅蜂王）── -->
          <div class="will-tab-body will-stream-body" v-show="tab === 'stream'">
            <div class="will-stream-panel">
              <div class="will-stream-hd">
                <span class="will-stream-dot"></span>
                <span class="will-stream-title">{{ t('stream_title') }}</span>
                <span class="will-stream-sub">NEURAL STREAM</span>
              </div>
              <div class="will-stream" ref="streamEl">
                <div v-for="(line, idx) in logs" :key="idx" class="will-log-line" :class="line.cssClass">
                  <span class="will-log-ts">{{ logTs(idx) }}</span>
                  <span class="will-log-text">{{ line.text }}</span>
                </div>
                <div v-if="!logs.length" class="will-log-idle">
                  <span class="will-log-cursor">▋</span> {{ t('waiting_signal') }}
                </div>
              </div>
            </div>
          </div>

        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { useWebSocket } from '../../composables/useWebSocket.js'
import { useSpecies } from '../../composables/useSpecies.js'
import { useActiveBees } from '../../composables/useActiveBees.js'

const store  = useAppStore()
const { t }  = useI18n()
const api    = useApi()
const { logs } = useWebSocket()
const emit = defineEmits(['toast', 'refresh-hives'])

const { speciesLabel, speciesEmoji: _speciesEmoji } = useSpecies()
const { activeBees } = useActiveBees()

const tab     = ref('engine')
const saving  = ref(false)
const maskedKey = ref('')
const streamEl  = ref(null)

const providerOptions = [
  { value: 'ollama',     label: 'Local Ollama'  },
  { value: 'openai',    label: 'OpenAI API'    },
  { value: 'anthropic', label: 'Anthropic API' },
  { value: 'deepseek',  label: 'DeepSeek'      },
  { value: 'qwen',      label: 'Qwen'          },
  { value: 'custom',    label: 'Custom API'    },
]

const WS_FILES = [
  'IDENTITY.md','SOUL.md','TOOLS.md','USER.md','MEMORY.md',
  'HEARTBEAT.md','SKILLS.md','SENSORS.md','GLOSSARY.md','AGENTS.md',
]
const FILE_ICONS = {
  'IDENTITY.md':'🪪','SOUL.md':'✨','TOOLS.md':'🔧','USER.md':'👤',
  'MEMORY.md':'🧠','HEARTBEAT.md':'💓','SKILLS.md':'⚡',
  'SENSORS.md':'📡','GLOSSARY.md':'📖','AGENTS.md':'🤝',
}

// ── 蜜蜂列表 ─────────────────────────────────────────────
// Enrich activeBees with statusLower needed by this panel's template
const nonQueenBees = computed(() =>
  activeBees.value.map(b => ({ ...b, statusLower: (b.status || 'offline').toLowerCase() }))
)

function beeEmoji(bee) { return _speciesEmoji(speciesLabel(bee)) }

const currentLabel = computed(() => {
  if (store.activeEditingBeeKey === '__queen__') return store.currentHive?.bee?.name || '蜂王'
  const bee = nonQueenBees.value.find(b => b._key === store.activeEditingBeeKey)
  return bee ? (bee.beeName || speciesLabel(bee)) : '—'
})

// 当前蜜蜂的 hiveId
const currentHiveId = computed(() => {
  if (store.activeEditingBeeKey === '__queen__') return store.currentHive?.hiveId || null
  const bee = nonQueenBees.value.find(b => b._key === store.activeEditingBeeKey)
  return bee?.hiveId || store.activeHiveId || null
})

// 当前工作蜂的 beeId（null 表示蜂王）
const currentBeeId = computed(() => {
  if (store.activeEditingBeeKey === '__queen__') return null
  const bee = nonQueenBees.value.find(b => b._key === store.activeEditingBeeKey)
  return bee?.beeId || null
})

// ── 思维引擎表单 ──────────────────────────────────────────
const engineForm = reactive({
  name: '', provider: 'ollama', model: '', apiKey: '',
  baseUrl: '', temperature: 0.7, systemPrompt: '',
})

// ── 记忆刻录（工作区文件）─────────────────────────────────
const fileStatus  = ref({})
const memFile     = ref(null)
const memContent  = ref('')
const memSaving   = ref(false)
const memLoading  = ref(false)
let   heartbeatTimer = null

async function loadWorkspace() {
  const hiveId = currentHiveId.value
  const beeId  = currentBeeId.value
  if (!hiveId) { fileStatus.value = {}; return }
  memLoading.value = true
  try {
    const data = beeId
      ? await api.listBeeWorkspace(hiveId, beeId)
      : await api.listWorkspace(hiveId)
    fileStatus.value = data.files || {}
  } catch {
    fileStatus.value = {}
  } finally {
    memLoading.value = false
  }
}

async function selectMemFile(name) {
  clearInterval(heartbeatTimer)
  memFile.value    = name
  memContent.value = ''
  const hiveId = currentHiveId.value
  const beeId  = currentBeeId.value
  if (!hiveId) return
  memContent.value = beeId
    ? await api.readBeeWorkspaceFile(hiveId, beeId, name)
    : await api.readWorkspaceFile(hiveId, name)
  if (name === 'HEARTBEAT.md') {
    heartbeatTimer = setInterval(async () => {
      const h = currentHiveId.value
      const b = currentBeeId.value
      if (h) memContent.value = b
        ? await api.readBeeWorkspaceFile(h, b, name)
        : await api.readWorkspaceFile(h, name)
    }, 3000)
  }
}

async function saveMemFile() {
  const hiveId = currentHiveId.value
  const beeId  = currentBeeId.value
  if (!hiveId || !memFile.value || memFile.value === 'HEARTBEAT.md') return
  memSaving.value = true
  try {
    if (beeId) {
      await api.writeBeeWorkspaceFile(hiveId, beeId, memFile.value, memContent.value)
    } else {
      await api.writeWorkspaceFile(hiveId, memFile.value, memContent.value)
    }
    fileStatus.value = { ...fileStatus.value, [memFile.value]: 'exists' }
    emit('toast', { message: `${memFile.value} 已保存`, type: 'success' })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  } finally {
    memSaving.value = false
  }
}

async function createDefault(name) {
  const hiveId = currentHiveId.value
  const beeId  = currentBeeId.value
  if (!hiveId) return
  try {
    if (beeId) {
      fileStatus.value = (await api.listBeeWorkspace(hiveId, beeId)).files || {}
      memContent.value = await api.readBeeWorkspaceFile(hiveId, beeId, name)
    } else {
      fileStatus.value = (await api.listWorkspace(hiveId)).files || {}
      memContent.value = await api.readWorkspaceFile(hiveId, name)
    }
    emit('toast', { message: `${name} 已创建`, type: 'success' })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  }
}

function onMemKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); saveMemFile() }
}

// ── 选择蜜蜂 ─────────────────────────────────────────────
async function selectBee(key, bee = null) {
  store.activeEditingBeeKey = key
  maskedKey.value = ''
  clearInterval(heartbeatTimer)
  memFile.value = null
  memContent.value = ''
  Object.assign(engineForm, { name:'', provider:'ollama', model:'', apiKey:'', baseUrl:'', temperature:0.7, systemPrompt:'' })

  if (key === '__queen__') {
    const b = store.currentHive?.bee || {}
    engineForm.name        = b.name         || ''
    engineForm.provider    = b.provider     || 'ollama'
    engineForm.model       = b.model        || ''
    engineForm.baseUrl     = b.baseUrl      || ''
    engineForm.temperature = b.temperature  ?? 0.7
    engineForm.systemPrompt = b.systemPrompt || ''
    try { const ak = await api.getBeeApiKey('queen'); maskedKey.value = ak.apiKeyMasked || '' } catch {}
  } else if (bee) {
    const species = speciesLabel(bee).toLowerCase()
    try {
      const cfg = await api.getConfig(species)
      engineForm.name         = cfg.name         || bee.beeName || ''
      engineForm.provider     = cfg.provider     || 'ollama'
      engineForm.model        = cfg.model        || ''
      engineForm.baseUrl      = cfg.baseUrl      || ''
      engineForm.temperature  = parseFloat(cfg.temperature) || 0.7
      engineForm.systemPrompt = cfg.systemPrompt || ''
    } catch {}
    try {
      const ak = await api.getBeeApiKey(species)
      maskedKey.value = ak.apiKeyMasked || ''
      if (!engineForm.model)    engineForm.model    = ak.model    || ''
      if (engineForm.provider === 'ollama') engineForm.provider = ak.provider || 'ollama'
    } catch {}
  }

  await loadWorkspace()
}

// ── 保存思维引擎 ──────────────────────────────────────────
async function saveEngine() {
  saving.value = true
  try {
    if (store.activeEditingBeeKey === '__queen__') {
      const hive = store.currentHive
      const beeData = {
        name: engineForm.name.trim(), provider: engineForm.provider,
        model: engineForm.model.trim(), baseUrl: engineForm.baseUrl.trim(),
        temperature: engineForm.temperature, systemPrompt: engineForm.systemPrompt.trim(),
      }
      if (engineForm.apiKey.trim()) beeData.apiKey = engineForm.apiKey.trim()
      await api.setHiveBee(hive.hiveId, beeData)
      emit('refresh-hives')
    } else {
      const bee = nonQueenBees.value.find(b => b._key === store.activeEditingBeeKey)
      if (!bee) return
      const species = speciesLabel(bee).toLowerCase()
      await api.putConfig(species, {
        name: engineForm.name.trim(), provider: engineForm.provider,
        model: engineForm.model.trim(), baseUrl: engineForm.baseUrl.trim(),
        temperature: String(engineForm.temperature), systemPrompt: engineForm.systemPrompt.trim(),
      })
      if (engineForm.apiKey.trim() || engineForm.provider !== 'ollama') {
        await api.putBeeApiKey(species, {
          provider: engineForm.provider, model: engineForm.model.trim(),
          apiKey: engineForm.apiKey.trim(), baseUrl: engineForm.baseUrl.trim(),
        })
      }
    }
    emit('toast', { message: '思维引擎已保存', type: 'success' })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  } finally {
    saving.value = false
  }
}

function clearEngine() {
  Object.assign(engineForm, { model:'', temperature:0.7, apiKey:'', systemPrompt:'' })
}

function reload() {
  const key = store.activeEditingBeeKey
  const bee = nonQueenBees.value.find(b => b._key === key) || null
  selectBee(key, bee)
}

// ── 神经流 ────────────────────────────────────────────────
watch(logs, async () => {
  await nextTick()
  if (streamEl.value) streamEl.value.scrollTop = streamEl.value.scrollHeight
}, { deep: true })

function logTs(idx) {
  const base = Date.now() - (logs.value.length - idx) * 1200
  const d = new Date(base)
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
}

// ── 监听外部跳转（BeesPanel 点击蜂蛹卡片）────────────────
watch(() => store.activeEditingBeeKey, (key) => {
  if (store.activePanel !== 'will') return
  const bee = nonQueenBees.value.find(b => b._key === key) || null
  selectBee(key, bee)
})

// ── 切换蜂巢时重置 ────────────────────────────────────────
watch(() => store.activeHiveId, () => {
  store.activeEditingBeeKey = '__queen__'
  selectBee('__queen__')
}, { immediate: true })

watch(() => store.activePanel, (panel) => {
  if (panel === 'will') reload()
})

defineExpose({ load: reload })
</script>

<style scoped>
#panel-will {
  background:
    radial-gradient(ellipse at 10% 20%, rgba(245,166,35,.08) 0%, transparent 50%),
    radial-gradient(ellipse at 90% 80%, rgba(255,200,80,.05) 0%, transparent 50%),
    linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
  color: #2C2724;
}

/* ── Header ── */
.will-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid rgba(245,166,35,.18);
  background: rgba(255,253,248,.85); backdrop-filter: blur(12px);
  flex-shrink: 0;
}
.will-header-left { display: flex; align-items: center; gap: 12px; }
.will-glyph { font-size: 1.5rem; }
.will-title { font-size: .92rem; font-weight: 800; letter-spacing: .05em; color: #2C2724; }
.will-subtitle { font-size: .7rem; color: #B5A898; margin-top: 2px; }
.will-refresh-btn {
  font-size: .72rem; font-weight: 700; color: #D97706;
  background: rgba(245,166,35,.08); border: 1px solid rgba(245,166,35,.25);
  border-radius: 8px; padding: 5px 12px; cursor: pointer; transition: background .15s;
}
.will-refresh-btn:hover { background: rgba(245,166,35,.16); }

/* ── Body ── */
.will-body { flex: 1; display: flex; overflow: hidden; min-height: 0; }
.will-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; gap: 12px; color: #B5A898; font-size: .85rem;
}
.will-empty-icon { font-size: 2.5rem; opacity: .3; }

/* ── Bee list ── */
.will-bee-list {
  width: 160px; flex-shrink: 0;
  border-right: 1px solid rgba(245,166,35,.15);
  background: rgba(255,253,248,.70);
  display: flex; flex-direction: column; overflow-y: auto;
}
.will-bee-list::-webkit-scrollbar { width: 3px; }
.will-bee-list::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); }

.will-bl-hd {
  display: flex; align-items: center; gap: 7px;
  padding: 12px 14px 8px; flex-shrink: 0;
  border-bottom: 1px solid rgba(245,166,35,.10);
}
.will-bl-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: #F59E0B; box-shadow: 0 0 6px #F59E0B;
  animation: will-pulse 2s ease-in-out infinite;
}
@keyframes will-pulse {
  0%,100% { opacity: 1; transform: scale(1); }
  50%      { opacity: .4; transform: scale(.75); }
}
.will-bl-title { font-size: .72rem; font-weight: 800; color: #2C2724; }
.will-bl-sep {
  font-size: .58rem; font-weight: 700; color: #B5A898; letter-spacing: .08em;
  text-transform: uppercase; padding: 8px 14px 3px;
}
.will-bl-empty { font-size: .68rem; color: #C5B5A0; padding: 8px 14px; font-style: italic; }

.will-bee-item {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 12px; cursor: pointer;
  border-left: 2px solid transparent; transition: background .12s; flex-shrink: 0;
}
.will-bee-item:hover { background: rgba(245,166,35,.08); }
.will-bee-item.active {
  background: rgba(245,166,35,.14); border-left-color: #D97706;
  box-shadow: inset 3px 0 8px rgba(245,166,35,.10);
}
.will-bee-icon { font-size: 1.1rem; flex-shrink: 0; }
.will-bee-meta { flex: 1; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.will-bee-name { font-size: .74rem; font-weight: 700; color: #2C2724; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.will-bee-item.active .will-bee-name { color: #D97706; }
.will-bee-type { font-size: .58rem; color: #B5A898; font-family: monospace; }
.will-bee-status { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.will-bee-status.online  { background: #16a34a; box-shadow: 0 0 5px #16a34a; animation: will-pulse 2s ease-in-out infinite; }
.will-bee-status.busy    { background: #F59E0B; box-shadow: 0 0 5px #F59E0B; animation: will-pulse 1s ease-in-out infinite; }
.will-bee-status.offline { background: #9ca3af; }

/* ── Content ── */
.will-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }

/* ── Tabs ── */
.will-tabs {
  display: flex; align-items: center; gap: 2px;
  padding: 10px 20px 0;
  border-bottom: 1px solid rgba(245,166,35,.15);
  flex-shrink: 0; background: rgba(255,253,248,.60);
}
.will-tab {
  display: flex; align-items: center; gap: 5px;
  padding: 7px 14px; font-size: .74rem; font-weight: 700; color: #B5A898;
  background: transparent; border: none; border-bottom: 2px solid transparent;
  cursor: pointer; transition: all .15s; margin-bottom: -1px;
}
.will-tab:hover { color: #7A6A5A; }
.will-tab.active { color: #D97706; border-bottom-color: #D97706; }
.will-tab-spacer { flex: 1; }
.will-saving { font-size: .68rem; color: #B5A898; font-style: italic; padding-bottom: 7px; }

/* ── Tab body ── */
.will-tab-body { flex: 1; overflow-y: auto; padding: 18px 20px; }
.will-tab-body::-webkit-scrollbar { width: 4px; }
.will-tab-body::-webkit-scrollbar-thumb { background: rgba(245,166,35,.25); border-radius: 4px; }

/* ── Engine card ── */
.will-card {
  background: rgba(255,255,255,.78); border: 1px solid rgba(245,166,35,.20);
  border-radius: 14px; padding: 18px 20px;
  box-shadow: 0 2px 12px rgba(180,130,50,.08); backdrop-filter: blur(12px);
  display: flex; flex-direction: column; gap: 14px;
}
.will-card-hd {
  display: flex; align-items: center; gap: 8px;
  padding-bottom: 10px; border-bottom: 1px solid rgba(245,166,35,.12);
}
.will-card-title { font-size: .85rem; font-weight: 800; color: #2C2724; flex: 1; }
.will-model-badge {
  font-size: .65rem; padding: 2px 8px;
  background: rgba(245,166,35,.12); color: #D97706;
  border-radius: 99px; font-weight: 700; font-family: monospace;
  border: 1px solid rgba(245,166,35,.25);
}
.will-form-group { display: flex; flex-direction: column; gap: 5px; }
.will-label { font-size: .72rem; font-weight: 700; color: #7A6A5A; }
.will-input {
  background: rgba(255,255,255,.70); border: 1px solid rgba(245,166,35,.25);
  border-radius: 8px; padding: 7px 11px;
  font-size: .78rem; color: #2C2724; outline: none; transition: border-color .15s;
  font-family: inherit;
}
.will-input:focus { border-color: rgba(245,166,35,.55); }
.will-sys-prompt { min-height: 100px; resize: vertical; line-height: 1.6; }
.will-hint { font-size: .62rem; color: #B5A898; font-family: monospace; }
.will-temp-row { display: flex; align-items: center; gap: 10px; }
.will-temp-input { width: 90px; }
.will-temp-hint { font-size: .72rem; color: #B5A898; }

/* ── Actions ── */
.will-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }
.will-btn-ghost {
  padding: 7px 16px; font-size: .76rem; font-weight: 700;
  background: transparent; border: 1px solid rgba(245,166,35,.25);
  border-radius: 8px; color: #7A6A5A; cursor: pointer; transition: background .15s;
}
.will-btn-ghost:hover { background: rgba(245,166,35,.08); }
.will-btn-primary {
  padding: 7px 18px; font-size: .76rem; font-weight: 800;
  background: linear-gradient(135deg, #D97706, #F59E0B);
  border: none; border-radius: 8px; color: #1a0c00;
  cursor: pointer; transition: all .15s; box-shadow: 0 2px 8px rgba(245,158,11,.30);
}
.will-btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(245,158,11,.45); }
.will-btn-primary:disabled { opacity: .5; cursor: not-allowed; }
.will-btn-sm { padding: 4px 10px; font-size: .68rem; }

/* ── 记忆刻录 tab ── */
.will-mem-tab-body { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
.will-mem-layout { display: flex; height: 100%; overflow: hidden; }

.will-mem-sidebar {
  width: 160px; flex-shrink: 0;
  border-right: 1px solid rgba(245,166,35,.15);
  background: rgba(255,249,235,.55);
  display: flex; flex-direction: column; overflow-y: auto;
}
.will-mem-sidebar::-webkit-scrollbar { width: 2px; }
.will-mem-sidebar::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); }

.will-mem-sb-hd {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 12px 8px; flex-shrink: 0;
  border-bottom: 1px solid rgba(245,166,35,.12);
  background: rgba(255,249,235,.80);
}
.will-mem-sb-title { font-size: .7rem; font-weight: 800; color: #2C2724; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.will-mem-spin {
  width: 6px; height: 6px; border-radius: 50%;
  background: #F59E0B; animation: will-pulse 1s ease-in-out infinite; flex-shrink: 0;
}
.will-mem-no-hive { font-size: .68rem; color: #C5B5A0; padding: 10px 12px; font-style: italic; }

.will-mem-file {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 12px; cursor: pointer; user-select: none;
  border-left: 2px solid transparent; transition: background .1s;
  flex-shrink: 0;
}
.will-mem-file:hover { background: rgba(245,166,35,.08); }
.will-mem-file.active { background: rgba(245,166,35,.14); border-left-color: #D97706; }
.will-mem-file-icon { font-size: .8rem; flex-shrink: 0; }
.will-mem-file-name {
  flex: 1; font-size: .72rem; font-family: monospace; color: #7A6A5A;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.will-mem-file.active .will-mem-file-name { color: #D97706; font-weight: 700; }
.will-mem-file-dot { flex-shrink: 0; }
.will-mem-file-dot.exists svg { fill: #16a34a; }
.will-mem-file-dot.missing svg { fill: rgba(245,166,35,.30); }

.will-mem-editor { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
.will-mem-editor-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px;
  background: rgba(255,249,235,.85); border-bottom: 1px solid rgba(245,166,35,.12);
  flex-shrink: 0; backdrop-filter: blur(8px);
}
.will-mem-editor-name {
  flex: 1; font-size: .72rem; font-family: monospace; color: #7A6A5A;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.will-mem-live { display: flex; align-items: center; gap: 5px; font-size: .6rem; font-weight: 700; color: #16a34a; letter-spacing: .08em; }
.will-mem-live-dot { width: 6px; height: 6px; border-radius: 50%; background: #16a34a; animation: will-pulse 1.2s ease-in-out infinite; }
.will-mem-missing {
  font-size: .6rem; font-weight: 600; padding: 1px 7px;
  background: rgba(245,166,35,.10); color: #B5A898;
  border: 1px solid rgba(245,166,35,.20); border-radius: 99px;
}
.will-mem-textarea {
  flex: 1; resize: none; border: none; outline: none;
  font-family: monospace; font-size: .78rem; line-height: 1.7;
  padding: 16px 18px;
  background: rgba(255,255,255,.70); color: #2C2724;
}
.will-mem-placeholder {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; gap: 10px; background: rgba(255,249,235,.50);
}
.will-ph-icon { font-size: 2rem; opacity: .25; }
.will-ph-text { font-size: .78rem; color: #B5A898; }

kbd { font-size: .6rem; font-family: monospace; opacity: .7; background: rgba(255,255,255,.25); border-radius: 3px; padding: 0 4px; }

/* ── Neural stream ── */
.will-stream-body { padding: 0; display: flex; flex-direction: column; }
.will-stream-panel {
  flex: 1; display: flex; flex-direction: column;
  background: rgba(255,255,255,.65); border: 1px solid rgba(245,166,35,.15);
  border-radius: 14px; margin: 16px 20px; overflow: hidden;
}
.will-stream-hd {
  display: flex; align-items: center; gap: 8px; padding: 10px 16px;
  border-bottom: 1px solid rgba(245,166,35,.12);
  background: rgba(255,249,235,.80); flex-shrink: 0;
}
.will-stream-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #F59E0B; box-shadow: 0 0 6px #F59E0B;
  animation: will-pulse 1.5s ease-in-out infinite;
}
.will-stream-title { font-size: .78rem; font-weight: 800; color: #2C2724; }
.will-stream-sub { font-size: .6rem; color: #B5A898; letter-spacing: .1em; }
.will-stream { flex: 1; overflow-y: auto; padding: 10px 16px; display: flex; flex-direction: column; gap: 3px; }
.will-stream::-webkit-scrollbar { width: 3px; }
.will-stream::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); }
.will-log-line { display: flex; gap: 8px; font-size: .72rem; line-height: 1.5; }
.will-log-ts { color: #C5B5A0; font-family: monospace; flex-shrink: 0; font-size: .65rem; }
.will-log-text { color: #7A6A5A; word-break: break-all; }
.will-log-line.sys  .will-log-text { color: #B5A898; font-style: italic; }
.will-log-line.red  .will-log-text { color: #DC2626; }
.will-log-line.green .will-log-text { color: #16a34a; }
.will-log-idle { color: #C5B5A0; font-size: .72rem; font-style: italic; padding: 12px 0; }
.will-log-cursor { animation: will-pulse 1s ease-in-out infinite; }
</style>
