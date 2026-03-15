<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box" style="max-width:500px">
      <div class="modal-title">{{ title }}</div>

      <div class="form-group">
        <label class="form-label">{{ t('name_label') }}</label>
        <input class="form-control" v-model="form.name" type="text" :placeholder="t('queen')">
      </div>
      <div class="form-group">
        <label class="form-label">{{ t('provider_label') }}</label>
        <div v-if="!riskAcked"
          style="font-size:.85rem;font-weight:700;color:var(--success);
                 padding:8px 10px;background:rgba(34,197,94,.06);border-radius:var(--radius-xs);
                 border:var(--border-w) solid rgba(34,197,94,.2)">
          🖥 本地 Ollama &nbsp;<span style="font-size:.7rem;font-weight:400;color:var(--text-muted)">（推荐，数据不出本机）</span>
        </div>
        <select v-if="riskAcked" class="form-control" v-model="form.provider" style="margin-top:6px" @change="onProviderChange">
          <option value="ollama">🖥 本地 Ollama</option>
          <option value="openai">🤖 OpenAI</option>
          <option value="anthropic">🧠 Anthropic</option>
          <option value="deepseek">🔵 DeepSeek</option>
          <option value="qwen">🟠 Qwen</option>
          <option value="custom">🔧 Custom</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">{{ t('model_label') }}</label>
        <div v-if="form.provider === 'ollama'" style="display:flex;gap:6px;align-items:center">
          <select v-if="ollamaModels.length" class="form-control" v-model="form.model">
            <option v-for="m in ollamaModels" :key="m" :value="m">{{ m }}</option>
          </select>
          <input v-else class="form-control" v-model="form.model" type="text" :placeholder="loadingModels ? '加载中…' : 'e.g. qwen2.5:7b'">
          <button class="btn btn-ghost" style="padding:4px 10px;font-size:.85rem;flex-shrink:0" :disabled="loadingModels" @click="fetchOllamaModels" title="刷新本地模型">↻</button>
        </div>
        <input v-else class="form-control" v-model="form.model" type="text" placeholder="e.g. gpt-4o">
      </div>
      <div v-if="riskAcked && form.provider !== 'ollama'" class="form-group">
        <label class="form-label">🔑 API Key</label>
        <input class="form-control" v-model="form.apiKey" type="password" placeholder="sk-..." autocomplete="new-password">
      </div>
      <div v-if="riskAcked && form.provider === 'custom'" class="form-group">
        <label class="form-label">🔗 Base URL</label>
        <input class="form-control" v-model="form.baseUrl" type="text" placeholder="https://...">
      </div>
      <div class="form-group">
        <label class="form-label">{{ t('temp_label') }}</label>
        <input class="form-control" v-model.number="form.temperature" type="number" step="0.1" min="0" max="2" placeholder="0.7">
      </div>

      <!-- Risk warning -->
      <div class="risk-warning-box">
        <div style="display:flex;align-items:center;gap:8px;cursor:pointer;user-select:none" @click="riskExpanded = !riskExpanded">
          <span style="font-weight:700">{{ t('risk_enable_external') }}</span>
          <span style="font-size:.75rem">{{ riskExpanded ? '▼' : '▶' }}</span>
        </div>
        <div v-if="riskExpanded" style="margin-top:10px">
          <div style="line-height:1.6;margin-bottom:10px">
            蜂王将处理大量本地敏感数据。启用外部 API 意味着这些数据将发送至第三方服务器，可能导致：
            <br>• 本地文件、笔记、对话记录泄露
            <br>• 无法审计第三方对数据的实际用途
            <br>• 网络中断时蜂王完全不可用
          </div>
          <label style="display:flex;align-items:flex-start;gap:8px;cursor:pointer">
            <input type="checkbox" v-model="riskAcked" style="margin-top:3px;flex-shrink:0">
            <span>{{ t('risk_ack_text') }}</span>
          </label>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost" @click="close">{{ t('cancel') }}</button>
        <button class="btn btn-primary" @click="confirm">{{ t('confirm_save_btn') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'

const { t } = useI18n()
const api = useApi()

const ollamaModels  = ref([])
const loadingModels = ref(false)

async function fetchOllamaModels() {
  loadingModels.value = true
  try {
    ollamaModels.value = await api.getOllamaModels()
    if (ollamaModels.value.length && !ollamaModels.value.includes(form.model))
      form.model = ollamaModels.value[0]
  } catch { ollamaModels.value = [] }
  finally { loadingModels.value = false }
}

const props = defineProps({
  visible: Boolean,
  hiveId: { type: String, default: null },
  bee: { type: Object, default: () => ({}) },
  title: { type: String, default: '配置蜂王' },
})
const emit = defineEmits(['close', 'confirm'])

const riskExpanded = ref(false)
const riskAcked = ref(false)

const form = reactive({
  name: '',
  provider: 'ollama',
  model: '',
  apiKey: '',
  baseUrl: '',
  temperature: 0.7,
})

watch(() => props.visible, (v) => {
  if (v) { resetForm(); fetchOllamaModels() }
})

watch(() => form.provider, (p) => {
  if (p === 'ollama' && ollamaModels.value.length === 0) fetchOllamaModels()
})

watch(ollamaModels, (list) => {
  if (form.provider === 'ollama' && list.length && !form.model)
    form.model = list[0]
})

function resetForm() {
  const b = props.bee || {}
  form.name = b.name || ''
  form.provider = b.provider || 'ollama'
  form.model = b.model || ''
  form.apiKey = ''
  form.baseUrl = b.baseUrl || ''
  form.temperature = b.temperature ?? 0.7
  riskExpanded.value = false

  const isExternal = b.provider && b.provider !== 'ollama'
  riskAcked.value = isExternal
  if (isExternal) riskExpanded.value = true
}

function onProviderChange() {
  if (!riskAcked.value) {
    form.provider = 'ollama'
  }
}

function close() { emit('close') }

function confirm() {
  if (form.provider !== 'ollama' && !riskAcked.value) {
    alert('请先阅读并确认数据安全风险')
    return
  }
  const bee = {
    name: form.name.trim() || '蜂王',
    provider: riskAcked.value ? form.provider : 'ollama',
    model: form.model.trim(),
    apiKey: form.apiKey.trim(),
    baseUrl: form.baseUrl.trim(),
  }
  if (!isNaN(form.temperature)) bee.temperature = form.temperature
  emit('confirm', { hiveId: props.hiveId, bee })
}
</script>
