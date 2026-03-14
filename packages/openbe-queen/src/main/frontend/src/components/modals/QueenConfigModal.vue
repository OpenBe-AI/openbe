<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box" style="max-width:500px">
      <div class="modal-title">{{ t('queen_config_title') }}</div>

      <div style="background:rgba(245,158,11,.1);border:1px solid var(--warning);border-radius:var(--radius-xs);
           padding:10px 12px;font-size:.78rem;color:var(--warning);margin-bottom:16px;line-height:1.5">
        ⚠️ 蜂王是 OpenBe 的核心调度器，拥有最高权限。为保障系统安全，强烈建议仅使用本地 Ollama 模型。
      </div>

      <div class="form-group">
        <label class="form-label">Provider</label>
        <div v-if="!riskAcked"
          style="font-size:.85rem;font-weight:700;color:var(--success);
                 padding:8px 10px;background:rgba(34,197,94,.06);border-radius:var(--radius-xs);
                 border:var(--border-w) solid rgba(34,197,94,.2)">
          🖥 Local Ollama &nbsp;<span style="font-size:.7rem;font-weight:400;color:var(--text-muted)">(本地模型，安全)</span>
        </div>
        <select v-if="riskAcked" class="form-control" v-model="form.provider" style="margin-top:6px">
          <option value="ollama">🖥 Local Ollama</option>
          <option value="openai">🤖 OpenAI API</option>
          <option value="anthropic">🧠 Anthropic API</option>
          <option value="deepseek">🔵 DeepSeek</option>
          <option value="qwen">🟠 Qwen</option>
          <option value="custom">🔧 Custom API</option>
        </select>
      </div>

      <div class="form-group">
        <label class="form-label">{{ t('model_label') }}</label>
        <input class="form-control" v-model="form.model" type="text" placeholder="e.g. llama3 / qwen2.5">
      </div>

      <div v-if="riskAcked && form.provider !== 'ollama'" class="form-group">
        <label class="form-label">🔑 API Key</label>
        <input class="form-control" v-model="form.apiKey" type="password" placeholder="sk-..." autocomplete="new-password">
        <div v-if="apiKeyMasked" style="font-size:.65rem;color:var(--text-muted);margin-top:3px;font-family:var(--font-mono)">
          {{ apiKeyMasked }}
        </div>
      </div>

      <div v-if="riskAcked && (form.provider === 'custom' || form.provider === 'ollama')" class="form-group">
        <label class="form-label">🔗 Base URL</label>
        <input class="form-control" v-model="form.baseUrl" type="text" placeholder="https://...">
      </div>

      <!-- Risk toggle -->
      <div class="risk-warning-box">
        <div style="display:flex;align-items:center;gap:8px;cursor:pointer;user-select:none" @click="riskExpanded = !riskExpanded">
          <span style="font-weight:700">{{ t('risk_enable_external_queen') }}</span>
          <span style="font-size:.75rem">{{ riskExpanded ? '▼' : '▶' }}</span>
        </div>
        <div v-if="riskExpanded" style="margin-top:10px">
          <div style="line-height:1.6;margin-bottom:10px">
            为蜂王启用外部 API 意味着系统调度指令将经过第三方服务器。由于蜂王权限最高，可能导致：
            <br>• 系统配置与蜂巢数据泄露给第三方
            <br>• 被恶意提示词劫持，执行未授权操作
            <br>• 无法审计 API 供应商对数据的使用
          </div>
          <label style="display:flex;align-items:flex-start;gap:8px;cursor:pointer">
            <input type="checkbox" v-model="riskAcked" style="margin-top:3px;flex-shrink:0">
            <span>{{ t('risk_ack_queen_text') }}</span>
          </label>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-ghost" @click="close">{{ t('cancel') }}</button>
        <button class="btn btn-primary" @click="confirm">{{ t('save_btn') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'

const { t } = useI18n()

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close', 'confirm', 'toast'])

const api = useApi()
const riskExpanded = ref(false)
const riskAcked = ref(false)
const apiKeyMasked = ref('')

const form = reactive({
  provider: 'ollama',
  model: '',
  apiKey: '',
  baseUrl: '',
})

watch(() => props.visible, async (v) => {
  if (v) await loadCurrentConfig()
})

async function loadCurrentConfig() {
  let apiCfg = { provider: 'ollama', model: '', baseUrl: '', apiKeyMasked: '' }
  let config = {}
  try { apiCfg = await api.getBeeApiKey('queen') || apiCfg } catch {}
  try { config = await api.getConfig('queen') || {} } catch {}

  form.provider = apiCfg.provider || 'ollama'
  form.model = apiCfg.model || config.model || ''
  form.apiKey = ''
  form.baseUrl = apiCfg.baseUrl || ''
  apiKeyMasked.value = apiCfg.apiKeyMasked || ''

  const isExternal = form.provider && form.provider !== 'ollama'
  riskAcked.value = isExternal
  riskExpanded.value = isExternal
}

function close() { emit('close') }

async function confirm() {
  const provider = riskAcked.value ? form.provider : 'ollama'
  if (provider !== 'ollama' && !riskAcked.value) {
    emit('toast', { message: '请先确认安全风险', type: 'error' })
    return
  }
  try {
    await api.putConfig('queen', { model: form.model, temperature: 0.7 })
    const apiBody = { provider, model: form.model, baseUrl: form.baseUrl }
    if (form.apiKey.trim()) apiBody.apiKey = form.apiKey.trim()
    await api.putBeeApiKey('queen', apiBody)
    await api.saveQueenSecurity({ allowExternal: provider !== 'ollama', provider })
    emit('confirm')
    emit('toast', {
      message: provider === 'ollama' ? '蜂王配置已保存（本地模式）' : '⚠️ 蜂王配置已保存（外部 API 模式）',
      type: 'success'
    })
  } catch (e) {
    emit('toast', { message: e.message, type: 'error' })
  }
}
</script>
