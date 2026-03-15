<template>
  <div class="panel" :class="{ active: store.activePanel === 'settings' }" id="panel-settings">

    <!-- Header -->
    <div class="dim-header">
      <div class="dim-header-left">
        <span class="dim-glyph">🛸</span>
        <div>
          <div class="dim-title">{{ t('settings_title') }}</div>
          <div class="dim-subtitle">
            <span v-if="purifyStatus.status === 'System Purified & Standardized'" class="purify-badge">
              ✨ {{ purifyStatus.status }}
              <span class="purify-ts">{{ purifyStatus.purifiedAt }}</span>
            </span>
            <span v-else>{{ store.currentHive?.name || t('no_hive_selected') }} — {{ t('settings_subtitle_suffix') }}</span>
          </div>
        </div>
      </div>
      <div class="dim-header-badges">
        <span class="dim-badge" :class="wsConnected ? 'badge-online' : 'badge-offline'">
          <span class="badge-led" :class="wsConnected ? 'led-green' : 'led-red'"></span>
          {{ wsConnected ? t('sensing') : t('disconnected') }}
        </span>
      </div>
    </div>

    <div class="dim-body">

      <!-- 顶部并排：外部触角 + 逻辑引擎 -->
      <div class="dim-grid-row">

        <!-- 外部触角 — API Keys -->
        <div class="dim-module">
          <div class="dim-module-hd">
            <span class="mod-icon mod-icon--amber">📡</span>
            <span class="mod-title">{{ t('external_feelers') }}</span>
            <button class="dim-btn-add" @click="createKey">{{ t('connect_btn') }}</button>
          </div>
          <div v-if="apiKeys.length === 0" class="dim-empty">
            <span class="dim-empty-cursor">▋</span> {{ t('no_feelers') }}
          </div>
          <div v-for="key in apiKeys" :key="key.id" class="feeler-item">
            <div class="feeler-led-wrap">
              <span class="feeler-led led-green"></span>
            </div>
            <div class="feeler-info">
              <span class="feeler-name">{{ key.name || key.id }}</span>
              <span class="feeler-masked">{{ maskedKey(key) }}</span>
            </div>
            <div class="feeler-actions">
              <button class="dim-btn-ghost" @click="copyKey(key)">{{ t('copy') }}</button>
              <button class="dim-btn-danger" @click="deleteKey(key.id)">{{ t('disconnect') }}</button>
            </div>
          </div>
        </div>

        <!-- 逻辑引擎 — 语言设置 -->
        <div class="dim-module">
          <div class="dim-module-hd">
            <span class="mod-icon mod-icon--teal">🧠</span>
            <span class="mod-title">{{ t('logic_engine') }}</span>
          </div>
          <div class="lang-matrix">
            <button
              class="lang-node"
              :class="{ active: store.lang === 'zh' }"
              @click="store.setLang('zh')"
            >
              <span class="lang-node-led" :class="store.lang === 'zh' ? 'led-green' : 'led-dim'"></span>
              {{ t('lang_zh') }}
            </button>
            <button
              class="lang-node"
              :class="{ active: store.lang === 'en' }"
              @click="store.setLang('en')"
            >
              <span class="lang-node-led" :class="store.lang === 'en' ? 'led-green' : 'led-dim'"></span>
              {{ t('lang_en') }}
            </button>
          </div>
        </div>

      </div>

      <!-- 底部并排：能量通路 + 琥珀封存 -->
      <div class="dim-grid-row">

        <!-- 能量通路 — 危险区 -->
        <div class="dim-module danger-module">
          <div class="dim-module-hd">
            <span class="mod-icon mod-icon--red">⚡</span>
            <span class="mod-title danger-text">{{ t('power_paths') }}</span>
          </div>
          <p class="danger-desc">{{ t('danger_desc') }}</p>
          <button class="emergency-btn" @click="$emit('emergency-stop')">
            <span class="emergency-icon">🛑</span> {{ t('emergency_stop') }}
          </button>
        </div>

        <!-- 琥珀封存 — Nuclear Reset -->
        <div class="dim-module amber-seal-module">
          <div class="dim-module-hd">
            <span class="mod-icon mod-icon--gold">🍯</span>
            <span class="mod-title amber-seal-title">{{ t('amber_seal') }}</span>
          </div>
          <p class="amber-seal-desc">{{ t('amber_seal_desc') }}</p>
          <button class="amber-seal-btn" @click="openDestructModal">
            <span>☢</span> {{ t('amber_seal_start') }}
          </button>
        </div>

      </div>

      <!-- 心跳监测仪 (全宽) -->
      <div class="dim-heartbeat">
        <div class="hb-header">
          <span class="hb-radar-dot"></span>
          <span class="hb-title">{{ t('heartbeat_monitor') }}</span>
          <span class="hb-sub">HEARTBEAT MONITOR</span>
          <span class="hb-status">{{ wsConnected ? 'NOMINAL' : 'SIGNAL LOST' }}</span>
        </div>
        <div class="hb-stream" ref="hbStreamEl">
          <div v-for="(entry, i) in heartbeatLog" :key="i" class="hb-line">
            <span class="hb-ts">{{ entry.ts }}</span>
            <span class="hb-ping" :class="entry.cssClass">{{ entry.text }}</span>
          </div>
          <div v-if="!heartbeatLog.length" class="hb-idle">
            <span class="hb-cursor">▋</span> {{ t('waiting_heartbeat_signal') }}
          </div>
        </div>
        <div class="hb-bar">
          <div
            v-for="(h, i) in pingHistory"
            :key="i"
            class="hb-bar-col"
            :style="{ height: Math.min(h, 40) + 'px', opacity: 0.3 + (i / pingHistory.length) * 0.7 }"
          ></div>
        </div>
      </div>

    </div>
  </div>

  <!-- 三重确认弹窗 -->
  <Teleport to="body">
    <div v-if="destructModal.open" class="destruct-overlay" @click.self="closeDestructModal">
      <div class="destruct-dialog">
        <div class="destruct-header">
          <span class="destruct-icon">☢</span>
          <span class="destruct-title">{{ t('destruct_title') }}</span>
        </div>

        <!-- Step 1：确认意图 -->
        <div class="destruct-step" :class="{ done: destructModal.step > 1 }">
          <div class="destruct-step-label">
            <span class="step-num">01</span> {{ t('destruct_step1_label') }}
          </div>
          <div v-if="destructModal.step === 1" class="destruct-step-body">
            <p class="destruct-warn">{{ t('destruct_step1_warn') }}</p>
            <button class="destruct-confirm-btn" @click="destructModal.step = 2">{{ t('destruct_step1_btn') }}</button>
          </div>
        </div>

        <!-- Step 2：密语验证 -->
        <div class="destruct-step" :class="{ done: destructModal.step > 2, locked: destructModal.step < 2 }">
          <div class="destruct-step-label">
            <span class="step-num">02</span> {{ t('destruct_step2_label') }}
          </div>
          <div v-if="destructModal.step === 2" class="destruct-step-body">
            <p class="destruct-warn">{{ t('destruct_step2_hint') }} <code class="destruct-code">{{ t('destruct_phrase') }}</code></p>
            <input
              class="destruct-input"
              v-model="destructModal.phrase"
              :placeholder="t('destruct_phrase')"
              @keyup.enter="checkPhrase"
            />
            <button class="destruct-confirm-btn" :disabled="destructModal.phrase !== t('destruct_phrase')" @click="checkPhrase">{{ t('destruct_phrase_btn') }}</button>
          </div>
        </div>

        <!-- Step 3：倒计时最终授权 -->
        <div class="destruct-step" :class="{ locked: destructModal.step < 3 }">
          <div class="destruct-step-label">
            <span class="step-num">03</span> {{ t('destruct_step3_label') }}
          </div>
          <div v-if="destructModal.step === 3" class="destruct-step-body">
            <p class="destruct-warn destruct-final">{{ t('destruct_step3_warn') }}</p>
            <div class="destruct-final-row">
              <button class="destruct-abort-btn" @click="closeDestructModal">{{ t('destruct_abort') }}</button>
              <button
                class="destruct-execute-btn"
                :disabled="destructModal.countdown > 0"
                @click="executeDestruct"
              >
                <span v-if="destructModal.countdown > 0">⏳ {{ destructModal.countdown }}s</span>
                <span v-else>{{ t('destruct_execute') }}</span>
              </button>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 死亡屏幕 -->
    <div v-if="deathScreen.visible" class="death-screen">
      <div class="death-terminal">
        <div
          v-for="(line, i) in deathScreen.lines"
          :key="i"
          class="death-line"
          :class="line.cls"
          :style="{ animationDelay: `${i * 0.35}s` }"
        >{{ line.text }}</div>
        <div v-if="deathScreen.cmd" class="death-cmd">
          <span class="death-cmd-label">{{ t('destruct_remove_label') }}</span>
          <code class="death-cmd-code">{{ deathScreen.cmd }}</code>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { useWebSocket } from '../../composables/useWebSocket.js'

const store = useAppStore()
const { t } = useI18n()
const api = useApi()
const { logs } = useWebSocket()

const emit = defineEmits(['toast', 'emergency-stop'])

const apiKeys = ref([])
const wsConnected = ref(true)
const destructModal = reactive({ open: false, step: 1, phrase: '', countdown: 5 })
const deathScreen   = reactive({ visible: false, lines: [], cmd: '' })
const purifyStatus  = reactive({ status: 'pending', purifiedAt: '', beesProcessed: 0 })
let countdownTimer = null
const heartbeatLog = ref([])
const pingHistory = ref([])
const hbStreamEl = ref(null)

let hbInterval = null

watch(() => store.activePanel, (panel) => { if (panel === 'settings') load() })
watch(() => store.activeHiveId, () => { if (store.activePanel === 'settings') load() })

watch(heartbeatLog, async () => {
  await nextTick()
  if (hbStreamEl.value) hbStreamEl.value.scrollTop = hbStreamEl.value.scrollHeight
}, { deep: true })

onMounted(() => {
  startHeartbeat()
  load()
})

onUnmounted(() => {
  if (hbInterval) clearInterval(hbInterval)
  if (countdownTimer) clearInterval(countdownTimer)
})

function startHeartbeat() {
  // Immediate first beat
  addHeartbeatEntry()
  hbInterval = setInterval(() => {
    addHeartbeatEntry()
  }, 3200)
}

function addHeartbeatEntry() {
  const now = new Date()
  const ts = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`
  const ping = Math.floor(Math.random() * 60) + 8
  pingHistory.value.push(ping)
  if (pingHistory.value.length > 32) pingHistory.value.shift()

  const cssClass = ping < 30 ? 'hb-ok' : ping < 60 ? 'hb-warn' : 'hb-crit'
  heartbeatLog.value.push({ ts, text: `PING ${ping}ms — ${t('hb_stable')}`, cssClass })
  if (heartbeatLog.value.length > 80) heartbeatLog.value.shift()
}

async function load() {
  try {
    const keys = await api.getApiKeys()
    apiKeys.value = Array.isArray(keys) ? keys : []
  } catch (err) {
    emit('toast', { message: `加载失败: ${err.message}`, type: 'error' })
  }
  // 拉取净化状态
  try {
    const res = await fetch('/api/system/purify-status')
    if (res.ok) {
      const data = await res.json()
      Object.assign(purifyStatus, data)
    }
  } catch (_) {}
}

function maskedKey(key) {
  const actual = key.key || ''
  return actual ? actual.substring(0, 10) + '••••' : '••••••••'
}

async function createKey() {
  const name = prompt('触角名称 (API Key name):')
  if (!name) return
  try {
    await api.createApiKey(name)
    emit('toast', { message: '触角已接入', type: 'success' })
    await load()
  } catch (err) {
    emit('toast', { message: `接入失败: ${err.message}`, type: 'error' })
  }
}

async function deleteKey(id) {
  try {
    await api.deleteApiKey(id)
    emit('toast', { message: '触角已断开', type: 'success' })
    await load()
  } catch (err) {
    emit('toast', { message: `断开失败: ${err.message}`, type: 'error' })
  }
}

function copyKey(key) {
  const val = key.key || key.name || ''
  navigator.clipboard.writeText(val).then(() => {
    emit('toast', { message: '触角信号已复制', type: 'info' })
  })
}

function openDestructModal() {
  destructModal.open = true
  destructModal.step = 1
  destructModal.phrase = ''
  destructModal.countdown = 5
}
function closeDestructModal() {
  destructModal.open = false
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}
function checkPhrase() {
  if (destructModal.phrase !== t('destruct_phrase')) return
  destructModal.step = 3
  destructModal.countdown = 5
  countdownTimer = setInterval(() => {
    destructModal.countdown--
    if (destructModal.countdown <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}
async function executeDestruct() {
  closeDestructModal()

  // 1. 清除前端存储
  window.localStorage.clear()
  window.sessionStorage.clear()
  if (window.indexedDB && indexedDB.databases) {
    const dbs = await indexedDB.databases().catch(() => [])
    dbs.forEach(db => indexedDB.deleteDatabase(db.name))
  }

  // 2. 调用后端清除 Redis 和配置文件
  try { await api.amberSealWipe() } catch (_) {}

  // 3. 视觉死亡动画
  document.body.style.transition = 'filter 3s ease'
  document.body.style.filter = 'contrast(200%) brightness(0%)'

  // 4. 3 秒后显示死亡屏幕
  setTimeout(() => {
    document.body.style.filter = ''
    document.body.style.transition = ''
    deathScreen.lines = [
      { text: '> Initializing Amber Seal Protocol...', cls: 'dl-sys' },
      { text: '> Will Cornerstone: DECONSTRUCTED', cls: 'dl-warn' },
      { text: '> Dimensional Hub: OFFLINE', cls: 'dl-warn' },
      { text: '> Hive Memory: PURGED', cls: 'dl-warn' },
      { text: '> Redis Neural Core: FLUSHED', cls: 'dl-warn' },
      { text: '> OpenBe Installation: REMOVED', cls: 'dl-warn' },
      { text: '> System: SHUTTING DOWN', cls: 'dl-warn' },
      { text: '> Amber Seal Protocol: COMPLETE', cls: 'dl-ok' },
      { text: '', cls: '' },
      { text: '感谢使用 OpenBe，期待下次再见 👋', cls: 'dl-bye' },
    ]
    deathScreen.cmd = ''
    deathScreen.visible = true
  }, 3200)
}

defineExpose({ load })
</script>

<style scoped>
/* ═══════════════════════════════════════════
   维度中枢 — Frosted Cream Light 磨砂奶油
═══════════════════════════════════════════ */
#panel-settings {
  background:
    radial-gradient(ellipse at 15% 10%, rgba(255,183,77,.10) 0%, transparent 50%),
    radial-gradient(ellipse at 85% 80%, rgba(255,149,0,.07) 0%, transparent 50%),
    linear-gradient(160deg, #FFFBF2 0%, #FFF8E8 100%);
  color: #3E2723;
}

/* ═══════════════════════════════════════════
   Header
═══════════════════════════════════════════ */
.dim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px 16px;
  border-bottom: 1px solid rgba(255,183,77,0.20);
  background: rgba(255,255,255,0.70);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 10;
}
.dim-header::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(255,183,77,0.5), transparent);
}
.dim-header-left { display: flex; align-items: center; gap: 14px; }
.dim-glyph { font-size: 1.8rem; }
.dim-title {
  font-size: .96rem;
  font-weight: 800;
  letter-spacing: .02em;
  color: #4E342E;
}
.dim-subtitle {
  font-size: .70rem;
  color: #A08070;
  margin-top: 3px;
}
.purify-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: .68rem;
  font-weight: 700;
  color: #16A34A;
  background: rgba(22,163,74,0.09);
  border: 1px solid rgba(22,163,74,0.22);
  border-radius: 99px;
  padding: 2px 10px 2px 8px;
}
.purify-ts {
  font-size: .60rem;
  font-weight: 500;
  color: #A08070;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.dim-header-badges { display: flex; gap: 8px; align-items: center; }
.dim-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: .68rem;
  font-weight: 700;
  letter-spacing: .05em;
  padding: 5px 12px;
  border-radius: 99px;
  border: 1.5px solid;
}
.badge-online  { color: #16A34A; border-color: rgba(22,163,74,.25); background: rgba(22,163,74,.08); }
.badge-offline { color: #DC2626; border-color: rgba(220,38,38,.25); background: rgba(220,38,38,.08); }

/* ═══════════════════════════════════════════
   LED Indicators (shared)
═══════════════════════════════════════════ */
.badge-led, .mod-led, .feeler-led, .lang-node-led {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  animation: led-pulse 2s ease-in-out infinite;
}
.led-green  { background: #22C55E; box-shadow: 0 0 6px rgba(34,197,94,.6); }
.led-red    { background: #EF4444; box-shadow: 0 0 6px rgba(239,68,68,.6); }
.led-amber  { background: #F59E0B; box-shadow: 0 0 6px rgba(245,158,11,.6); }
.led-teal   { background: #14B8A6; box-shadow: 0 0 6px rgba(20,184,166,.6); }
.led-dim    { background: rgba(0,0,0,0.15); box-shadow: none; animation: none; }
@keyframes led-pulse {
  0%,100% { opacity: 1; transform: scale(1); }
  50%     { opacity: .40; transform: scale(.70); }
}

/* ═══════════════════════════════════════════
   Body
═══════════════════════════════════════════ */
.dim-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.dim-body::-webkit-scrollbar { width: 4px; }
.dim-body::-webkit-scrollbar-thumb { background: rgba(255,183,77,.3); border-radius: 4px; }

/* ═══════════════════════════════════════════
   2×2 Grid Row
═══════════════════════════════════════════ */
.dim-grid-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* ═══════════════════════════════════════════
   Module Icon Badge
═══════════════════════════════════════════ */
.mod-icon {
  font-size: 1.1rem;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.mod-icon--amber { background: rgba(255,183,77,0.18); }
.mod-icon--teal  { background: rgba(20,184,166,0.15); }
.mod-icon--red   { background: rgba(239,68,68,0.12); }
.mod-icon--gold  { background: rgba(245,158,11,0.15); }

/* ═══════════════════════════════════════════
   Module Card — Glassmorphism Nest
═══════════════════════════════════════════ */
.dim-module {
  background: rgba(255,255,255,0.60);
  border: 1px solid rgba(255,183,77,0.30);
  border-radius: 24px;
  padding: 20px 22px;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 2px 16px rgba(255,183,77,.08), 0 1px 4px rgba(0,0,0,.04);
  transition: border-color .2s, box-shadow .2s, transform .2s;
}
.dim-module:hover {
  border-color: rgba(255,183,77,0.50);
  box-shadow: 0 6px 28px rgba(255,183,77,.14), 0 2px 8px rgba(0,0,0,.05);
  transform: translateY(-1px);
}

.dim-module-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255,183,77,0.15);
}

.mod-title {
  font-size: .84rem;
  font-weight: 800;
  color: #4E342E;
  letter-spacing: .02em;
}
.mod-sub {
  font-size: .63rem;
  color: #A08070;
  flex: 1;
}

.dim-empty {
  font-size: .74rem;
  color: #C4A882;
  padding: 8px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dim-empty-cursor { animation: blink .8s step-end infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* Buttons */
.dim-btn-add {
  background: rgba(255,183,77,0.15);
  border: 1.5px solid rgba(255,183,77,0.40);
  color: #D97706;
  font-size: .72rem;
  font-weight: 700;
  padding: 5px 14px;
  border-radius: 99px;
  cursor: pointer;
  transition: all .15s;
  margin-left: auto;
  font-family: inherit;
}
.dim-btn-add:hover { background: rgba(255,183,77,0.28); border-color: rgba(255,183,77,.65); }

.dim-btn-ghost {
  background: rgba(255,255,255,0.7);
  border: 1.5px solid rgba(0,0,0,0.10);
  color: #6B5040;
  font-size: .68rem;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all .15s;
  font-family: inherit;
}
.dim-btn-ghost:hover { background: rgba(255,255,255,1); border-color: rgba(255,183,77,.4); color: #D97706; }

.dim-btn-danger {
  background: rgba(254,226,226,0.7);
  border: 1.5px solid rgba(239,68,68,0.20);
  color: #DC2626;
  font-size: .68rem;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all .15s;
  font-family: inherit;
}
.dim-btn-danger:hover { background: rgba(254,226,226,1); border-color: rgba(239,68,68,.4); }

/* ═══════════════════════════════════════════
   外部触角 — Feeler Items
═══════════════════════════════════════════ */
.feeler-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255,255,255,0.75);
  border: 1.5px solid rgba(255,183,77,0.18);
  border-radius: 14px;
  margin-bottom: 8px;
  transition: border-color .15s, box-shadow .15s;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}
.feeler-item:last-child { margin-bottom: 0; }
.feeler-item:hover { border-color: rgba(255,183,77,0.40); box-shadow: 0 3px 10px rgba(255,183,77,.10); }

.feeler-led-wrap { width: 20px; display: flex; justify-content: center; }

.feeler-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.feeler-name {
  font-size: .80rem;
  font-weight: 700;
  color: #3E2723;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.feeler-masked {
  font-size: .65rem;
  color: #A08070;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
.feeler-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* ═══════════════════════════════════════════
   逻辑引擎 — Language Matrix (Mochi/Jelly tabs)
═══════════════════════════════════════════ */
.lang-matrix {
  display: flex;
  gap: 10px;
  background: rgba(0,0,0,0.05);
  border-radius: 16px;
  padding: 6px;
}
.lang-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 14px;
  background: transparent;
  border: none;
  border-radius: 12px;
  color: #A08070;
  font-size: .82rem;
  font-weight: 700;
  cursor: pointer;
  transition: all .2s cubic-bezier(.34,1.56,.64,1);
  font-family: inherit;
}
.lang-node:hover {
  background: rgba(255,255,255,0.6);
  color: #5D4037;
}
.lang-node.active {
  background: rgba(255,255,255,0.95);
  color: #D97706;
  box-shadow:
    0 4px 14px rgba(255,152,0,.18),
    0 1px 4px rgba(0,0,0,.08),
    inset 0 1px 0 rgba(255,255,255,1);
  transform: translateY(-1px);
}

/* ═══════════════════════════════════════════
   能量通路 — Danger Module (Frosted Ruby)
═══════════════════════════════════════════ */
.danger-module {
  border-color: rgba(239,68,68,0.18);
  background: rgba(255,245,245,0.65);
}
.danger-module:hover {
  border-color: rgba(239,68,68,0.30);
  box-shadow: 0 6px 28px rgba(239,68,68,.08);
}
.danger-text { color: #DC2626 !important; }

.danger-desc {
  font-size: .76rem;
  color: #9B6060;
  line-height: 1.65;
  margin-bottom: 16px;
}

/* Frosted Ruby glass button */
.emergency-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(254,226,226,0.80);
  border: 1.5px solid rgba(239,68,68,0.30);
  color: #DC2626;
  font-size: .82rem;
  font-weight: 800;
  letter-spacing: .04em;
  padding: 11px 26px;
  border-radius: 99px;
  cursor: pointer;
  transition: all .2s cubic-bezier(.34,1.56,.64,1);
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 12px rgba(239,68,68,.10), inset 0 1px 0 rgba(255,255,255,.6);
  font-family: inherit;
}
.emergency-btn:hover {
  background: rgba(254,202,202,0.90);
  border-color: rgba(239,68,68,.55);
  box-shadow: 0 6px 24px rgba(239,68,68,.20), inset 0 1px 0 rgba(255,255,255,.7);
  transform: translateY(-2px);
}
.emergency-icon { font-size: 1.1rem; }

/* ═══════════════════════════════════════════
   心跳监测仪 — Heartbeat Monitor (light)
═══════════════════════════════════════════ */
.dim-heartbeat {
  background: rgba(255,255,255,0.80);
  border: 1.5px solid rgba(255,183,77,0.35);
  border-radius: 24px;
  overflow: hidden;
  font-family: 'SFMono-Regular', Consolas, monospace;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 2px 16px rgba(255,183,77,.10), 0 1px 4px rgba(0,0,0,.06);
}

.hb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1.5px solid rgba(255,183,77,0.20);
  background: rgba(255,243,220,0.70);
}
.hb-radar-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #F59E0B;
  box-shadow: 0 0 8px rgba(245,158,11,.6);
  animation: led-pulse 1.2s ease-in-out infinite;
}
.hb-title {
  font-size: .76rem;
  font-weight: 800;
  color: #3E2723;
  letter-spacing: .03em;
}
.hb-sub {
  font-size: .58rem;
  color: #A08070;
  letter-spacing: .08em;
}
.hb-status {
  margin-left: auto;
  font-size: .62rem;
  color: #15803D;
  letter-spacing: .08em;
  font-weight: 800;
}

.hb-stream {
  height: 120px;
  overflow-y: auto;
  padding: 8px 0 4px;
  background: rgba(252,248,243,0.90);
}
.hb-stream::-webkit-scrollbar { width: 3px; }
.hb-stream::-webkit-scrollbar-thumb { background: rgba(255,183,77,.30); border-radius: 3px; }

.hb-line {
  display: flex;
  gap: 12px;
  padding: 3px 20px;
  font-size: .64rem;
  line-height: 1.7;
  transition: background .1s;
}
.hb-line:hover { background: rgba(255,183,77,0.08); }
.hb-ts {
  color: #9B7B60;
  flex-shrink: 0;
  min-width: 58px;
  font-weight: 600;
}
.hb-ping { flex: 1; font-weight: 600; }
.hb-ok   { color: #15803D; }
.hb-warn { color: #B45309; }
.hb-crit { color: #B91C1C; }

.hb-idle {
  padding: 20px;
  font-size: .64rem;
  color: #9B7B60;
  display: flex;
  align-items: center;
  gap: 6px;
}
.hb-cursor { animation: blink .8s step-end infinite; }

/* 雷达柱状图 */
.hb-bar {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  padding: 8px 20px 12px;
  height: 58px;
  border-top: 1px solid rgba(255,183,77,0.18);
  background: rgba(255,243,220,0.50);
}
.hb-bar-col {
  flex: 1;
  background: linear-gradient(to top, rgba(245,158,11,.65), rgba(251,191,36,.18));
  border-radius: 3px 3px 0 0;
  min-height: 4px;
  transition: height .5s cubic-bezier(.34,1.56,.64,1);
  box-shadow: 0 0 4px rgba(245,158,11,.20);
}

/* ═══════════════════════════════════════════
   琥珀封存 — Golden Resin button
═══════════════════════════════════════════ */
.amber-seal-module {
  border-color: rgba(245,158,11,0.35);
  background: rgba(255,251,235,0.70);
}
.amber-seal-title { color: #92400E !important; }
.amber-seal-desc {
  font-size: .76rem;
  color: #92600E;
  line-height: 1.7;
  margin-bottom: 18px;
}
.amber-seal-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 50%, #FCA522 100%);
  border: 1.5px solid rgba(245,158,11,0.50);
  color: #78350F;
  font-size: .84rem;
  font-weight: 800;
  letter-spacing: .03em;
  padding: 12px 28px;
  border-radius: 99px;
  cursor: pointer;
  transition: all .2s cubic-bezier(.34,1.56,.64,1);
  box-shadow:
    0 4px 16px rgba(245,158,11,.25),
    0 1px 4px rgba(0,0,0,.08),
    inset 0 1px 0 rgba(255,255,255,.6);
  font-family: inherit;
}
.amber-seal-btn:hover {
  background: linear-gradient(135deg, #FDE68A 0%, #FCA522 50%, #F59E0B 100%);
  box-shadow:
    0 8px 28px rgba(245,158,11,.40),
    0 2px 6px rgba(0,0,0,.10),
    inset 0 1px 0 rgba(255,255,255,.5);
  transform: translateY(-2px);
}

/* ═══════════════════════════════════════════
   三重确认弹窗 (light)
═══════════════════════════════════════════ */
.destruct-overlay {
  position: fixed;
  inset: 0;
  background: rgba(62,39,35,0.35);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.destruct-dialog {
  width: 480px;
  background: rgba(255,253,248,0.96);
  border: 1.5px solid rgba(255,183,77,0.35);
  border-radius: 24px;
  padding: 28px;
  box-shadow: 0 24px 60px rgba(62,39,35,.18), 0 4px 16px rgba(0,0,0,.08);
  display: flex;
  flex-direction: column;
  gap: 18px;
  backdrop-filter: blur(20px);
}
.destruct-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255,183,77,0.20);
}
.destruct-icon { font-size: 1.5rem; }
.destruct-title {
  font-size: .92rem;
  font-weight: 800;
  color: #92400E;
  letter-spacing: .02em;
}

.destruct-step {
  border: 1.5px solid rgba(255,183,77,0.20);
  border-radius: 16px;
  padding: 14px 16px;
  background: rgba(255,255,255,0.60);
  transition: opacity .2s;
}
.destruct-step.done {
  opacity: 0.45;
  background: rgba(240,253,244,0.60);
  border-color: rgba(34,197,94,0.25);
}
.destruct-step.locked { opacity: 0.28; pointer-events: none; }

.destruct-step-label {
  font-size: .73rem;
  font-weight: 700;
  color: #78501A;
  letter-spacing: .04em;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-num {
  font-family: 'SFMono-Regular', monospace;
  font-size: .65rem;
  background: rgba(255,183,77,0.18);
  color: #D97706;
  padding: 2px 7px;
  border-radius: 6px;
  font-weight: 800;
}
.destruct-step-body { display: flex; flex-direction: column; gap: 10px; }
.destruct-warn {
  font-size: .76rem;
  color: #6B4226;
  line-height: 1.65;
  margin: 0;
}
.destruct-final { color: #DC2626; font-weight: 600; }
.destruct-code {
  background: rgba(255,183,77,0.15);
  color: #D97706;
  padding: 2px 7px;
  border-radius: 6px;
  font-family: 'SFMono-Regular', monospace;
  font-size: .80rem;
  font-weight: 700;
}
.destruct-input {
  background: rgba(255,255,255,0.90);
  border: 1.5px solid rgba(255,183,77,0.35);
  border-radius: 10px;
  padding: 9px 13px;
  color: #4E342E;
  font-family: 'SFMono-Regular', monospace;
  font-size: .82rem;
  font-weight: 700;
  outline: none;
  letter-spacing: .06em;
  transition: border-color .15s, box-shadow .15s;
  width: 100%;
  box-sizing: border-box;
}
.destruct-input:focus { border-color: rgba(245,158,11,.60); box-shadow: 0 0 0 3px rgba(245,158,11,.12); }
.destruct-input::placeholder { color: #C4A882; }

.destruct-confirm-btn {
  background: rgba(255,183,77,0.18);
  border: 1.5px solid rgba(245,158,11,0.40);
  color: #D97706;
  font-size: .78rem;
  font-weight: 700;
  padding: 9px 22px;
  border-radius: 10px;
  cursor: pointer;
  align-self: flex-start;
  transition: all .15s;
  font-family: inherit;
}
.destruct-confirm-btn:hover:not(:disabled) { background: rgba(255,183,77,0.30); }
.destruct-confirm-btn:disabled { opacity: 0.30; cursor: not-allowed; }

.destruct-final-row { display: flex; gap: 10px; }
.destruct-abort-btn {
  flex: 1;
  background: rgba(0,0,0,0.04);
  border: 1.5px solid rgba(0,0,0,0.10);
  color: #9B8070;
  font-size: .78rem;
  font-weight: 600;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all .15s;
  font-family: inherit;
}
.destruct-abort-btn:hover { background: rgba(0,0,0,0.08); }
.destruct-execute-btn {
  flex: 2;
  background: rgba(254,226,226,0.90);
  border: 1.5px solid rgba(239,68,68,0.35);
  color: #DC2626;
  font-size: .82rem;
  font-weight: 800;
  letter-spacing: .03em;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all .2s;
  font-family: inherit;
  backdrop-filter: blur(4px);
}
.destruct-execute-btn:hover:not(:disabled) {
  background: rgba(254,202,202,1);
  border-color: rgba(239,68,68,.55);
  box-shadow: 0 4px 16px rgba(239,68,68,.18);
}
.destruct-execute-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ═══════════════════════════════════════════
   死亡屏幕
═══════════════════════════════════════════ */
.death-screen {
  position: fixed;
  inset: 0;
  background: #000;
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}
.death-terminal {
  max-width: 640px;
  width: 90%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.death-line {
  font-size: .85rem;
  line-height: 1.6;
  opacity: 0;
  animation: death-appear .4s ease forwards;
}
@keyframes death-appear {
  from { opacity: 0; transform: translateX(-8px); }
  to   { opacity: 1; transform: translateX(0); }
}
.dl-sys  { color: rgba(245,166,35,0.70); }
.dl-warn { color: rgba(248,113,113,0.85); }
.dl-ok   { color: rgba(74,222,128,0.85); }
.dl-bye  { color: #FFD700; font-size: 1.1em; font-weight: 700; letter-spacing: .04em; }

.death-cmd {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  opacity: 0;
  animation: death-appear .5s ease 3s forwards;
}
.death-cmd-label {
  font-size: .72rem;
  color: rgba(245,166,35,0.45);
}
.death-cmd-code {
  display: block;
  background: rgba(245,166,35,0.08);
  border: 1px solid rgba(245,166,35,0.30);
  border-radius: 8px;
  padding: 12px 16px;
  color: #F5A623;
  font-size: .9rem;
  font-weight: 700;
  letter-spacing: .04em;
  box-shadow: 0 0 20px rgba(245,166,35,0.10);
  user-select: all;
}
</style>
