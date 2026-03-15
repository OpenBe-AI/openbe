<template>
  <div class="panel" :class="{ active: store.activePanel === 'tuning' }" id="panel-tuning">
    <div class="tp-header">
      <div class="tp-header-left">
        <span class="tp-glyph">⚙️</span>
        <div>
          <div class="tp-title">{{ t('tuning_panel_title') }}</div>
          <div class="tp-subtitle">{{ t('tuning_panel_subtitle') }}</div>
        </div>
      </div>
    </div>

    <div class="panel-body">
      <div v-if="!store.currentHive" class="tuning-empty">
        {{ t('select_hive_hint') }}
      </div>

      <div v-else class="tp-layout">

        <!-- ── 左侧：蜜蜂选择器 ── -->
        <div class="tp-bee-list">
          <div class="tp-bl-hd">
            <span class="tp-bl-dot"></span>
            <span class="tp-bl-title">{{ t('select_bee_label') }}</span>
          </div>

          <!-- 蜂王 -->
          <div
            class="tp-bee-item"
            :class="{ active: selectedKey === '__queen__' }"
            @click="selectBee('__queen__')"
          >
            <span class="tp-bee-icon">👑</span>
            <div class="tp-bee-meta">
              <span class="tp-bee-name">{{ store.currentHive.bee?.name || '蜂王' }}</span>
              <span class="tp-bee-type">QUEEN</span>
            </div>
            <span class="tp-bee-status online"></span>
          </div>

          <!-- 运行中蜂蛹 -->
          <div v-if="nonQueenBees.length === 0" class="tp-bl-empty">{{ t('no_active_bees_short') }}</div>
          <div
            v-for="bee in nonQueenBees"
            :key="bee._key"
            class="tp-bee-item"
            :class="{ active: selectedKey === bee._key }"
            @click="selectBee(bee._key, bee)"
          >
            <span class="tp-bee-icon">{{ beeEmoji(bee) }}</span>
            <div class="tp-bee-meta">
              <span class="tp-bee-name">{{ bee.beeName || speciesLabel(bee) }}</span>
              <span class="tp-bee-type">{{ speciesLabel(bee) }}</span>
            </div>
            <span class="tp-bee-status" :class="bee.statusLower"></span>
          </div>
        </div>

        <!-- ── 右侧：配置表单 ── -->
        <div class="tp-form-wrap">
          <div class="tuning-card" v-if="selectedKey">
            <div class="tuning-card-hd">
              <span class="tuning-card-title">{{ selectedLabel }}</span>
              <span class="model-badge">{{ form.model || t('not_set') }}</span>
              <span v-if="saving" class="tp-saving">{{ t('saving') }}</span>
            </div>

            <div class="form-group">
              <label class="form-label">{{ t('bee_name') }}</label>
              <input class="form-control" v-model="form.name" type="text" :placeholder="t('bee_name')">
            </div>
            <div class="form-group">
              <label class="form-label">Provider</label>
              <select class="form-control" v-model="form.provider">
                <option v-for="p in providerOptions" :key="p.value" :value="p.value">{{ p.label }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('model') }}</label>
              <div v-if="form.provider === 'ollama'" style="display:flex;gap:6px;align-items:center">
                <select v-if="ollamaModels.length" class="form-control" v-model="form.model">
                  <option v-for="m in ollamaModels" :key="m" :value="m">{{ m }}</option>
                </select>
                <input v-else class="form-control" v-model="form.model" type="text" :placeholder="loadingModels ? '加载中…' : 'e.g. qwen2.5:7b'">
                <button class="btn btn-ghost" style="padding:4px 10px;font-size:.85rem;flex-shrink:0" :disabled="loadingModels" @click="fetchOllamaModels" title="刷新本地模型">↻</button>
              </div>
              <input v-else class="form-control" v-model="form.model" type="text" placeholder="e.g. gpt-4o">
            </div>
            <div v-if="form.provider !== 'ollama'" class="form-group">
              <label class="form-label">API Key</label>
              <input class="form-control" v-model="form.apiKey" type="password"
                :placeholder="maskedKey || '••••••••'"
                autocomplete="new-password">
              <div v-if="maskedKey" class="form-hint">{{ maskedKey }}</div>
            </div>
            <div v-if="form.provider === 'custom'" class="form-group">
              <label class="form-label">Base URL</label>
              <input class="form-control" v-model="form.baseUrl" type="text" placeholder="https://...">
            </div>
            <div class="form-group">
              <label class="form-label">温度 ({{ t('temperature') }})</label>
              <div class="temp-row">
                <input class="form-control temp-input" v-model.number="form.temperature" type="number" step="0.1" min="0" max="2">
                <span class="temp-hint">{{ t('temp_hint') }}</span>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('system_prompt') }}</label>
              <textarea
                class="form-control tuning-prompt"
                v-model="form.systemPrompt"
                placeholder="You are a helpful assistant…"
              ></textarea>
            </div>
            <div class="tuning-actions">
              <button class="btn btn-ghost" @click="clearForm">{{ t('clear') }}</button>
              <button class="btn btn-primary" @click="save" :disabled="saving">{{ t('save_config') }}</button>
            </div>
          </div>

          <div v-else class="tp-pick-hint">
            <div class="tp-pick-icon">⚙️</div>
            <div class="tp-pick-text">{{ t('pick_bee_hint') }}</div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'

const store = useAppStore()
const { t } = useI18n()
const api = useApi()
const emit = defineEmits(['toast', 'refresh-hives'])

const providerOptions = [
  { value: 'ollama',     label: 'Local Ollama'  },
  { value: 'openai',    label: 'OpenAI API'    },
  { value: 'anthropic', label: 'Anthropic API' },
  { value: 'deepseek',  label: 'DeepSeek'      },
  { value: 'qwen',      label: 'Qwen'          },
  { value: 'custom',    label: 'Custom API'    },
]

const SPECIES_META = {
  WORKER: '🐝', SOLDIER: '🛡', NURSE: '🏥', SCOUT: '🔭',
  MECHANIC: '🔩', MEDIC: '🏥', SCRIBE: '📝', PAINTER: '🎨',
  EDITOR: '🎬', INFLUENCER: '📱', SENTINEL: '🔔',
}

// ── 运行中蜂蛹列表（排除蜂王和离线）──────────────────────────
const nonQueenBees = computed(() =>
  Object.entries(store.bees)
    .filter(([, b]) => {
      const type = (b.beeType || b.type || '').toLowerCase()
      return type !== 'queen' && (b.status || '').toLowerCase() !== 'offline'
    })
    .map(([key, b]) => ({
      _key: key, ...b,
      statusLower: (b.status || 'offline').toLowerCase(),
    }))
)

function speciesLabel(bee) {
  const ds = (bee.displaySpecies || '').toUpperCase()
  if (ds && SPECIES_META[ds]) return ds
  const beeId = (bee.beeId || '')
  if (beeId.includes('-')) {
    const prefix = beeId.split('-')[0].toUpperCase()
    if (SPECIES_META[prefix]) return prefix
  }
  return (bee.beeType || bee.type || 'WORKER').toUpperCase()
}

function beeEmoji(bee) {
  return SPECIES_META[speciesLabel(bee)] || '🐝'
}

// ── 选中状态 ─────────────────────────────────────────────────
const selectedKey   = ref(null)
const selectedBee   = ref(null)   // null = queen
const selectedLabel = computed(() =>
  selectedKey.value === '__queen__'
    ? (store.currentHive?.bee?.name || '蜂王') + ' · QUEEN'
    : (selectedBee.value?.beeName || speciesLabel(selectedBee.value || {}))
)

const saving       = ref(false)
const maskedKey    = ref('')
const ollamaModels = ref([])
const loadingModels = ref(false)
const form = reactive({
  name: '', provider: 'ollama', model: '',
  apiKey: '', baseUrl: '', temperature: 0.7, systemPrompt: '',
})

async function fetchOllamaModels() {
  loadingModels.value = true
  try {
    ollamaModels.value = await api.getOllamaModels()
    if (ollamaModels.value.length && !ollamaModels.value.includes(form.model))
      form.model = ollamaModels.value[0]
  } catch { ollamaModels.value = [] }
  finally { loadingModels.value = false }
}

watch(() => form.provider, (p) => {
  if (p === 'ollama') fetchOllamaModels()
})

// 模型列表异步加载完后，若 model 仍为空则自动选第一个
watch(ollamaModels, (list) => {
  if (form.provider === 'ollama' && list.length && !form.model)
    form.model = list[0]
})

async function selectBee(key, bee = null) {
  selectedKey.value  = key
  selectedBee.value  = bee
  maskedKey.value    = ''
  Object.assign(form, { name: '', provider: 'ollama', model: '', apiKey: '', baseUrl: '', temperature: 0.7, systemPrompt: '' })
  if (ollamaModels.value.length === 0) fetchOllamaModels()

  if (key === '__queen__') {
    // 蜂王：从 hive.bee 读取
    const b = store.currentHive?.bee || {}
    form.name         = b.name        || ''
    form.provider     = b.provider    || 'ollama'
    form.model        = b.model       || ''
    form.baseUrl      = b.baseUrl     || ''
    form.temperature  = b.temperature ?? 0.7
    form.systemPrompt = b.systemPrompt || ''
    // 获取掩码 key
    try {
      const ak = await api.getBeeApiKey('queen')
      maskedKey.value = ak.apiKeyMasked || ''
      if (!form.provider || form.provider === 'ollama') form.provider = ak.provider || 'ollama'
      if (!form.model) form.model = ak.model || ''
    } catch {}
  } else {
    // 工蜂：从 beeType 读取配置
    const species = speciesLabel(bee).toLowerCase()
    try {
      const cfg = await api.getConfig(species)
      form.name         = cfg.name        || bee.beeName || ''
      form.provider     = cfg.provider    || 'ollama'
      form.model        = cfg.model       || ''
      form.baseUrl      = cfg.baseUrl     || ''
      form.temperature  = parseFloat(cfg.temperature) || 0.7
      form.systemPrompt = cfg.systemPrompt || ''
    } catch {}
    try {
      const ak = await api.getBeeApiKey(species)
      maskedKey.value = ak.apiKeyMasked || ''
      if (!form.provider || form.provider === 'ollama') form.provider = ak.provider || 'ollama'
      if (!form.model) form.model = ak.model || ''
    } catch {}
  }
  // 加载完配置后，若 provider=ollama 且 model 仍为空，自动选中第一个本地模型
  if (form.provider === 'ollama' && !form.model && ollamaModels.value.length)
    form.model = ollamaModels.value[0]
}

async function save() {
  if (!selectedKey.value) return
  saving.value = true
  try {
    if (selectedKey.value === '__queen__') {
      const hive = store.currentHive
      const beeData = {
        name: form.name.trim(), provider: form.provider,
        model: form.model.trim(), baseUrl: form.baseUrl.trim(),
        systemPrompt: form.systemPrompt.trim(), temperature: form.temperature,
      }
      if (form.apiKey.trim()) beeData.apiKey = form.apiKey.trim()
      await api.setHiveBee(hive.hiveId, beeData)
      emit('refresh-hives')
    } else {
      const species = speciesLabel(selectedBee.value).toLowerCase()
      const cfg = {
        name: form.name.trim(), provider: form.provider,
        model: form.model.trim(), baseUrl: form.baseUrl.trim(),
        systemPrompt: form.systemPrompt.trim(), temperature: String(form.temperature),
      }
      await api.putConfig(species, cfg)
      await api.putBeeApiKey(species, {
        provider: form.provider, model: form.model.trim(),
        apiKey: form.apiKey.trim(), baseUrl: form.baseUrl.trim(),
      })
    }
    emit('toast', { message: '配置已保存', type: 'success' })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  } finally {
    saving.value = false
  }
}

function clearForm() {
  Object.assign(form, { model: '', temperature: 0.7, systemPrompt: '', apiKey: '' })
}

// 切换蜂巢时重置，并默认选中蜂王
watch(() => store.activeHiveId, () => {
  selectedKey.value = null
  selectedBee.value = null
  if (store.currentHive) selectBee('__queen__')
}, { immediate: true })

watch(() => store.activePanel, (panel) => {
  if (panel === 'tuning' && store.currentHive && !selectedKey.value) selectBee('__queen__')
})

defineExpose({ loadCard: () => selectBee('__queen__') })
</script>

<style scoped>
#panel-tuning {
  background:
    radial-gradient(ellipse at 25% 40%, rgba(245,166,35,.07) 0%, transparent 55%),
    radial-gradient(ellipse at 75% 60%, rgba(255,160,0,.05)  0%, transparent 55%),
    linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
  color: #2C2724;
}

/* ── Header ───────────────────────────────── */
.tp-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid rgba(245,166,35,.18);
  background: rgba(255,253,248,.85);
  backdrop-filter: blur(12px);
  flex-shrink: 0;
}
.tp-header-left { display: flex; align-items: center; gap: 12px; }
.tp-glyph { font-size: 1.5rem; }
.tp-title { font-size: .92rem; font-weight: 800; letter-spacing: .05em; color: #2C2724; }
.tp-subtitle { font-size: .7rem; color: #B5A898; margin-top: 2px; }

/* ── Layout ───────────────────────────────── */
.tp-layout {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
}

/* ── Bee list (left) ──────────────────────── */
.tp-bee-list {
  width: 180px;
  flex-shrink: 0;
  background: rgba(255,255,255,.65);
  border: 1px solid rgba(245,166,35,.18);
  border-radius: 14px;
  backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(180,130,50,.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tp-bl-hd {
  display: flex; align-items: center; gap: 7px;
  padding: 10px 14px 8px;
  border-bottom: 1px solid rgba(245,166,35,.12);
  flex-shrink: 0;
}
.tp-bl-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: #F59E0B; box-shadow: 0 0 6px #F59E0B;
  animation: led-pulse 2s ease-in-out infinite;
}
@keyframes led-pulse {
  0%,100% { opacity: 1; transform: scale(1); }
  50%      { opacity: .45; transform: scale(.75); }
}
.tp-bl-title { font-size: .72rem; font-weight: 800; color: #2C2724; letter-spacing: .03em; }
.tp-bl-empty { font-size: .68rem; color: #B5A898; padding: 10px 14px; font-style: italic; }

.tp-bee-item {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  border-left: 2px solid transparent;
  transition: background .12s;
}
.tp-bee-item:hover { background: rgba(245,166,35,.08); }
.tp-bee-item.active {
  background: rgba(245,166,35,.14);
  border-left-color: #D97706;
}
.tp-bee-icon { font-size: 1.1rem; flex-shrink: 0; }
.tp-bee-meta { flex: 1; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.tp-bee-name {
  font-size: .74rem; font-weight: 700; color: #2C2724;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.tp-bee-item.active .tp-bee-name { color: #D97706; }
.tp-bee-type { font-size: .58rem; color: #B5A898; font-family: monospace; letter-spacing: .04em; }
.tp-bee-status {
  width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0;
}
.tp-bee-status.online  { background: #16a34a; box-shadow: 0 0 5px #16a34a; }
.tp-bee-status.busy    { background: #F59E0B; box-shadow: 0 0 5px #F59E0B; }
.tp-bee-status.offline { background: #9ca3af; }

/* ── Form wrap (right) ────────────────────── */
.tp-form-wrap {
  flex: 1;
  overflow-y: auto;
  min-width: 0;
}
.tp-form-wrap::-webkit-scrollbar { width: 4px; }
.tp-form-wrap::-webkit-scrollbar-thumb { background: rgba(245,166,35,.25); border-radius: 4px; }

.tp-pick-hint {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 200px; gap: 10px;
}
.tp-pick-icon { font-size: 2rem; opacity: .25; }
.tp-pick-text { font-size: .78rem; color: #B5A898; }

/* ── Card ─────────────────────────────────── */
.tuning-card {
  background: rgba(255,255,255,.72);
  border: 1px solid rgba(245,166,35,.20);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(180,130,50,.08);
  backdrop-filter: blur(12px);
  display: flex; flex-direction: column; gap: 14px;
}
.tuning-card-hd {
  display: flex; align-items: center; gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(245,166,35,.12);
}
.tuning-card-title { font-size: .85rem; font-weight: 800; color: #2C2724; flex: 1; }
.model-badge {
  font-size: .68rem; padding: 2px 9px;
  background: rgba(245,166,35,.12); color: #D97706;
  border-radius: 99px; font-weight: 600; font-family: monospace;
  border: 1px solid rgba(245,166,35,.25);
}
.tp-saving { font-size: .68rem; color: #B5A898; font-style: italic; }

.form-hint { font-size: .63rem; color: #B5A898; margin-top: 3px; font-family: monospace; }

.temp-row { display: flex; align-items: center; gap: 10px; }
.temp-input { width: 80px; }
.temp-hint { font-size: .72rem; color: #B5A898; }

.tuning-prompt {
  min-height: 160px; resize: vertical;
  font-family: monospace; font-size: .82rem; line-height: 1.6;
}

.tuning-actions {
  display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px;
}

.tuning-empty {
  color: #B5A898; text-align: center; padding: 48px; font-size: .85rem;
}

/* panel-body needs flex for the layout */
.panel-body { display: flex; flex-direction: column; }
.tp-layout { flex: 1; }
</style>
