<template>
  <div id="top-bar">

    <!-- Logo -->
    <div class="topbar-logo">
      <div class="logo-icon">🐝</div>
      <span>OpenBe</span>
    </div>

    <!-- 连接状态 -->
    <div class="topbar-pill">
      <span class="ws-dot" :class="{ connected: wsConnected }"></span>
      <span>{{ wsConnected ? '已连接' : '未连接' }}</span>
    </div>

    <!-- 面包屑导航 -->
    <div class="topbar-breadcrumb" v-if="store.currentHive">
      <span class="bc-seg">蜂巢</span>
      <span class="bc-sep">›</span>
      <span class="bc-hive">{{ hiveName }}</span>
    </div>

    <div class="topbar-spacer"></div>

    <!-- Command Center 胶囊 -->
    <div class="cmd-center">

      <!-- Hive 状态（仅有蜂巢时显示） -->
      <div class="cmd-status" v-if="store.currentHive">
        <span class="cmd-hb-dot" :class="{ beating: wsConnected }"></span>
        <span class="cmd-hb-label">{{ hiveName }}</span>
        <span class="cmd-hb-count" v-if="store.onlineCount">{{ store.onlineCount }} 蜂</span>
      </div>

      <div class="cmd-divider" v-if="store.currentHive"></div>

      <!-- 版本 + 更新通知 -->
      <div class="version-wrap" @click="toggleUpdatePanel" :class="{ 'has-update': hasUpdate }">
        <span class="version-label">v{{ CURRENT_VERSION }}</span>
        <span v-if="hasUpdate" class="update-dot"></span>
      </div>

    </div>

    <!-- 更新通知浮层 -->
    <Transition name="update-pop">
      <div v-if="showUpdatePanel" class="update-panel" @click.stop>
        <div class="up-header">
          <span class="up-icon">{{ hasUpdate ? '🚀' : '✅' }}</span>
          <div>
            <div class="up-title">{{ hasUpdate ? '发现新版本！' : '当前已是最新版本' }}</div>
            <div class="up-cur">当前版本 <code>v{{ CURRENT_VERSION }}</code></div>
          </div>
          <button class="up-close" @click="showUpdatePanel = false">✕</button>
        </div>

        <template v-if="hasUpdate && latestRelease">
          <div class="up-new-ver">
            <span class="up-new-label">最新版本</span>
            <span class="up-new-tag">v{{ latestRelease.version }}</span>
          </div>
          <div v-if="latestRelease.notes" class="up-notes">{{ latestRelease.notes }}</div>
          <a v-if="latestRelease.url" :href="latestRelease.url" target="_blank" class="up-btn">
            前往下载 →
          </a>
        </template>

        <template v-else-if="!hasUpdate">
          <div class="up-ok-msg">🍯 蜂巢系统已是最新，无需更新。</div>
          <div class="up-check-time" v-if="lastChecked">上次检查：{{ lastChecked }}</div>
        </template>

        <button class="up-recheck" @click="checkUpdate(true)">
          <span :class="{ spinning: checking }">⟳</span> {{ checking ? '检查中…' : '重新检查' }}
        </button>
      </div>
    </Transition>

    <!-- 点击外部关闭 -->
    <div v-if="showUpdatePanel" class="update-backdrop" @click="showUpdatePanel = false"></div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAppStore } from '../stores/app.js'
import { useWebSocket } from '../composables/useWebSocket.js'

const CURRENT_VERSION = '0.2.0'
// 更新检查地址（上线后替换为真实接口）
const UPDATE_CHECK_URL = '/api/version/latest'

const store     = useAppStore()
const { status } = useWebSocket()

const wsConnected   = computed(() => status.value === 'connected')
const hiveName      = computed(() => store.currentHive?.name || store.currentHive?.hiveId || '')

const showUpdatePanel = ref(false)
const hasUpdate       = ref(false)
const latestRelease   = ref(null)   // { version, notes, url }
const checking        = ref(false)
const lastChecked     = ref('')

function toggleUpdatePanel() {
  showUpdatePanel.value = !showUpdatePanel.value
  if (showUpdatePanel.value && !lastChecked.value) {
    checkUpdate(false)
  }
}

async function checkUpdate(manual = false) {
  if (checking.value) return
  checking.value = true
  try {
    const res = await fetch(UPDATE_CHECK_URL, { signal: AbortSignal.timeout(5000) })
    if (!res.ok) throw new Error('no endpoint')
    const data = await res.json()
    const latest = data.version || data.tag || ''
    if (latest && latest !== CURRENT_VERSION) {
      hasUpdate.value = true
      latestRelease.value = {
        version: latest,
        notes:   data.notes || data.body || '',
        url:     data.url   || data.html_url || '',
      }
    } else {
      hasUpdate.value = false
      latestRelease.value = null
    }
  } catch {
    // 接口不存在或超时 — 静默失败，不弹错误
    if (manual) {
      hasUpdate.value = false
      latestRelease.value = null
    }
  } finally {
    checking.value = false
    const now = new Date()
    lastChecked.value = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`
  }
}

onMounted(() => {
  // 启动后 3 秒静默检查一次
  setTimeout(() => checkUpdate(false), 3000)
})

defineEmits(['emergency-stop'])
</script>

<style scoped>
.version-wrap {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
  user-select: none;
}
.version-wrap:hover {
  background: rgba(26, 25, 23, 0.06);
}
.version-wrap.has-update {
  background: rgba(255, 195, 0, 0.10);
}
.version-label {
  font-size: 0.70rem;
  font-weight: 700;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.02em;
}
.version-wrap.has-update .version-label {
  color: #d97706;
}

.update-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.7);
  animation: dot-pulse 1.6s ease-in-out infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: .5; transform: scale(0.7); }
}

/* ── 浮层 ── */
.update-backdrop {
  position: fixed;
  inset: 0;
  z-index: 199;
}

.update-panel {
  position: fixed;
  top: calc(var(--topbar-h) + 8px);
  right: 16px;
  width: 300px;
  background: rgba(255, 254, 249, 0.97);
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.12), 0 0 0 1px rgba(255,255,255,0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 16px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.up-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.up-icon { font-size: 1.4rem; flex-shrink: 0; }
.up-title {
  font-size: 0.82rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.2;
}
.up-cur {
  font-size: 0.68rem;
  color: var(--text-muted);
  margin-top: 2px;
}
.up-cur code {
  font-family: 'JetBrains Mono', monospace;
  background: rgba(0,0,0,0.05);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.70rem;
}
.up-close {
  margin-left: auto;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 0.75rem;
  padding: 2px 4px;
  flex-shrink: 0;
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
}
.up-close:hover { background: rgba(0,0,0,0.07); color: var(--text-primary); }

.up-new-ver {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.20);
  border-radius: 10px;
}
.up-new-label {
  font-size: 0.68rem;
  color: var(--text-muted);
  font-weight: 600;
}
.up-new-tag {
  font-size: 0.82rem;
  font-weight: 800;
  color: #d97706;
  font-family: 'JetBrains Mono', monospace;
}

.up-notes {
  font-size: 0.72rem;
  color: var(--text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  max-height: 100px;
  overflow-y: auto;
  background: rgba(0,0,0,0.03);
  border-radius: 8px;
  padding: 8px 10px;
}

.up-btn {
  display: block;
  text-align: center;
  padding: 8px 0;
  background: linear-gradient(135deg, #FFC300, #FFD740);
  border-radius: 99px;
  color: #1A1710;
  font-size: 0.78rem;
  font-weight: 800;
  text-decoration: none;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 4px 12px rgba(255,195,0,0.30);
}
.up-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(255,195,0,0.45);
}

.up-ok-msg {
  font-size: 0.75rem;
  color: var(--text-secondary);
  text-align: center;
  padding: 4px 0;
}
.up-check-time {
  font-size: 0.64rem;
  color: var(--text-muted);
  text-align: center;
}

.up-recheck {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: none;
  border: 1px solid var(--border);
  border-radius: 99px;
  color: var(--text-muted);
  font-size: 0.72rem;
  font-family: var(--font-family);
  padding: 5px 14px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.up-recheck:hover {
  border-color: rgba(245,158,11,0.40);
  color: var(--text-primary);
}
.spinning {
  display: inline-block;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── 弹出动画 ── */
.update-pop-enter-active {
  animation: pop-in 0.22s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.update-pop-leave-active {
  animation: pop-in 0.15s ease-in reverse;
}
@keyframes pop-in {
  from { opacity: 0; transform: translateY(-8px) scale(0.95); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
