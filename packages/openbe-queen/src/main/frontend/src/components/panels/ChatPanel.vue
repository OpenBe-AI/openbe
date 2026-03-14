<template>
  <div class="panel" :class="{ active: store.activePanel === 'chat' }" id="panel-chat">

    <!-- 蜂巢网格底纹（背景层） -->
    <div class="honeycomb-bg" aria-hidden="true"></div>

    <!-- Bee Tabs -->
    <div class="bee-tabs">
      <div v-if="store.chatTabEntries.length === 0" class="bee-tabs-empty">
        {{ t('no_chat_bees') }}
      </div>
      <button
        v-for="entry in store.chatTabEntries"
        :key="entry.key"
        class="bee-tab"
        :class="{ active: store.activeChatBee === entry.key, 'bee-tab--worker': !entry.isQueen }"
        @click="activateBee(entry.key)"
      >
        <span class="status-dot" :class="tabStatus(entry)"></span>
        <span class="tab-avatar">{{ entry.isQueen ? '👑' : speciesEmoji(entry.species) }}</span>
        <span class="tab-label">{{ entry.label }}</span>
        <span v-if="!entry.isQueen && entry.pid" class="tab-pid">#{{ entry.pid }}</span>
        <span
          v-if="!entry.isQueen"
          class="tab-close"
          @click.stop="removeTab(entry)"
          title="删除对话"
        >×</span>
      </button>
    </div>

    <!-- Chat Body -->
    <div class="chat-body">

      <!-- Messages -->
      <div class="messages-wrap" ref="messagesEl">

        <!-- Empty state -->
        <div v-if="showEmpty" class="chat-empty">
          <div class="chat-empty-bee">
            <!-- 工作蜂：显示蜂种图片 -->
            <template v-if="activeEntry && !activeEntry.isQueen">
              <img
                :src="`/assets/bees/${(activeEntry.species||'worker').toLowerCase()}.png`"
                class="empty-queen-img"
                @error="e => e.target.src='/assets/bees/worker.png'"
                alt="Bee"
              />
            </template>
            <!-- 蜂王 -->
            <template v-else>
              <img src="/bee/queen_extracted.png" class="empty-queen-img" alt="Queen Bee"/>
            </template>
          </div>
          <!-- 没有任何可对话对象 -->
          <template v-if="store.chatTabEntries.length === 0">
            <p class="chat-empty-title">{{ t('no_chat_bees_title') }}</p>
            <p class="chat-empty-sub">{{ t('no_chat_bees_sub') }}</p>
          </template>
          <!-- 有工作蜂选中但无消息 -->
          <template v-else-if="activeEntry && !activeEntry.isQueen">
            <p class="chat-empty-title">{{ activeEntry.label }} {{ t('bee_standby') }}</p>
            <p class="chat-empty-sub">{{ t('worker_standby_sub') }}</p>
          </template>
          <!-- 有蜂王选中但无消息 -->
          <template v-else>
            <p class="chat-empty-title">{{ t('queen_standby') }}</p>
            <p class="chat-empty-sub">{{ t('chat_hint') }}</p>
          </template>

          <!-- 灵魂共振蜜蜂网格 — 显示所有可对话蜜蜂，点击即激活 -->
          <div v-if="store.chatTabEntries.length > 0" class="resonance-grid">
            <div class="resonance-grid-label">灵魂共振 · 选择对话对象</div>
            <div class="resonance-bee-row">
              <button
                v-for="entry in store.chatTabEntries"
                :key="entry.key"
                class="resonance-bee-card"
                :class="{ active: store.activeChatBee === entry.key }"
                @click="activateBee(entry.key)"
              >
                <span class="rb-avatar">{{ entry.isQueen ? '👑' : speciesEmoji(entry.species) }}</span>
                <span class="rb-name">{{ entry.label }}</span>
                <span class="rb-dot" :class="tabStatus(entry)"></span>
              </button>
            </div>
          </div>
        </div>

        <!-- Message rows -->
        <template v-if="!showEmpty">
          <div
            v-for="msg in currentMessages"
            :key="msg.id"
            class="msg-row"
            :class="msg.role"
          >
            <!-- Bot avatar -->
            <div v-if="msg.role === 'bot'" class="msg-avatar bot-avatar" :class="{ 'bot-avatar--worker': !activeEntryIsQueen }">
              <img v-if="activeEntryIsQueen" src="/bee/queen_extracted.png" class="bot-queen-img" alt="Queen"/>
              <span v-else class="bot-worker-emoji">{{ activeEntryEmoji }}</span>
            </div>

            <!-- Bubble -->
            <div class="msg-col">
              <div class="msg-bubble" :class="msg.role">
                <template v-if="msg.thinking">
                  <div class="thinking-dots">
                    <span></span><span></span><span></span>
                  </div>
                </template>
                <template v-else>
                  <span v-html="renderContent(msg.content)"></span>
                </template>
              </div>
              <span class="msg-time">{{ msg.time }}</span>
            </div>

            <!-- User avatar -->
            <div v-if="msg.role === 'user'" class="msg-avatar user-avatar">
              <span>{{ t('user_avatar') }}</span>
            </div>
          </div>
        </template>

      </div>

      <!-- Input Bar -->
      <div class="chat-input-bar">
        <div class="input-wrap">
          <textarea
            class="chat-input"
            ref="inputEl"
            v-model="inputText"
            rows="1"
            :placeholder="t('chat_hint')"
            @keydown="onKeydown"
            @input="autoGrow"
          ></textarea>
          <div class="input-hint">{{ t('input_hint_newline') }}</div>
        </div>
        <button class="chat-send-btn" @click="sendMessage" :disabled="!inputText.trim()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { useWebSocket } from '../../composables/useWebSocket.js'

const store = useAppStore()
const { t } = useI18n()
const api = useApi()
const { onTaskComplete, addLog } = useWebSocket()

const inputText = ref('')
const messagesEl = ref(null)
const inputEl = ref(null)

// ── 蜂种 emoji 映射 ───────────────────────────────────────────
const SPECIES_EMOJI = {
  WORKER:'🐝', SOLDIER:'🛡️', NURSE:'🌸', SCOUT:'🔭', PAINTER:'🎨',
  MECHANIC:'⚙️', MEDIC:'💊', SCRIBE:'📝', EDITOR:'🎬', INFLUENCER:'⭐', SENTINEL:'🔔',
}
function speciesEmoji(sp) { return SPECIES_EMOJI[(sp||'').toUpperCase()] || '🐝' }

// 当前激活标签页的信息
const activeEntry = computed(() =>
  store.chatTabEntries.find(e => e.key === store.activeChatBee) || null
)
const activeEntryIsQueen = computed(() => activeEntry.value?.isQueen ?? true)
const activeEntryEmoji   = computed(() => speciesEmoji(activeEntry.value?.species))

function tabStatus(entry) {
  if (entry.isQueen) return store.getBeeStatus('queen')
  return entry.status || 'offline'
}

const currentMessages = computed(() => {
  const key = store.activeChatBee
  if (!key) return []
  return store.chatMessages[key] || []
})

const showEmpty = computed(() => {
  if (!store.activeChatBee) return true
  return currentMessages.value.length === 0
})

watch(currentMessages, async () => {
  await nextTick()
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}, { deep: true })

function activateBee(key) {
  store.activateChatBee(key)
  loadMessagesFromServer(key)
}

async function removeTab(entry) {
  store.removeBee(entry.key)
  if (entry.beeId) {
    api.removeBeeById(entry.beeId).catch(() => {})
  }
}

// 解析 beeKey 对应的 { hiveId, beeId } 上下文，用于隔离路由
function resolveBeeCtx(beeKey) {
  if (beeKey.startsWith('bee:')) {
    const beeId = beeKey.slice(4)
    const bee = Object.values(store.bees).find(b => b.beeId === beeId)
    if (bee?.hiveId) return { hiveId: bee.hiveId, beeId }
  }
  return null
}

async function loadMessagesFromServer(beeKey) {
  const current = store.chatMessages[beeKey] || []
  if (current.some(m => m.thinking)) return
  try {
    let msgs
    if (beeKey.startsWith('queen:')) {
      msgs = await api.loadHiveQueenMessages(beeKey.slice(6))
    } else if (beeKey.startsWith('hive:')) {
      msgs = await api.loadHiveMessages(beeKey.slice(5))
    } else {
      const ctx = resolveBeeCtx(beeKey)
      if (ctx) {
        msgs = await api.loadHiveBeeMessages(ctx.hiveId, ctx.beeId)
      } else {
        const entry = store.chatTabEntries.find(e => e.key === beeKey)
        const beeType = entry?.type || (beeKey.includes(':') ? beeKey.split(':')[0] : beeKey)
        msgs = await api.loadBeeMessages(beeType)
      }
    }
    if (!Array.isArray(msgs) || msgs.length === 0) return
    const fresh = store.chatMessages[beeKey] || []
    if (fresh.some(m => m.thinking)) return
    if (msgs.length > fresh.filter(m => !m.thinking).length) {
      store.chatMessages[beeKey] = msgs
      store._persistChatLocal()
    }
  } catch { /* silently fail */ }
}

function persistMessages(beeKey) {
  const msgs = (store.chatMessages[beeKey] || []).filter(m => !m.thinking)
  if (beeKey.startsWith('queen:')) {
    api.saveHiveQueenMessages(beeKey.slice(6), msgs)
  } else if (beeKey.startsWith('hive:')) {
    api.saveHiveMessages(beeKey.slice(5), msgs)
  } else {
    const ctx = resolveBeeCtx(beeKey)
    if (ctx) {
      api.saveHiveBeeMessages(ctx.hiveId, ctx.beeId, msgs)
    } else {
      const entry = store.chatTabEntries.find(e => e.key === beeKey)
      const beeType = entry?.type || (beeKey.includes(':') ? beeKey.split(':')[0] : beeKey)
      api.saveBeeMessages(beeType, msgs)
    }
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  const beeKey = store.activeChatBee
  if (!beeKey) return

  inputText.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'

  store.addMessage(beeKey, 'user', text)
  const thinkingId = store.addMessage(beeKey, 'bot', '', { thinking: true })
  persistMessages(beeKey)

  try {
    let result
    if (beeKey.startsWith('queen:')) {
      result = await api.sendHiveChat(beeKey.slice(6), text)
    } else if (beeKey.startsWith('hive:')) {
      result = await api.sendHiveChat(beeKey.slice(5), text)
    } else {
      const ctx = resolveBeeCtx(beeKey)
      if (ctx) {
        // 蜂巢工作蜂：用 hiveId+beeId 路由，完全隔离
        result = await api.sendHiveBeeChat(ctx.hiveId, ctx.beeId, text)
      } else {
        // 兜底：按 type 路由（系统蜂或无 hiveId 的蜂）
        const entry = store.chatTabEntries.find(e => e.key === beeKey)
        const beeType = entry?.type || (beeKey.includes(':') ? beeKey.split(':')[0] : beeKey)
        result = await api.sendChat(beeType, text)
      }
    }
    const taskId = result.taskId || result.id
    if (taskId) {
      onTaskComplete(taskId, (answer) => {
        store.updateMessage(beeKey, thinkingId, answer || '(no response)')
        persistMessages(beeKey)
        addLog('green', `[CHAT] Task ${taskId} resolved`)
      })
    } else {
      store.updateMessage(beeKey, thinkingId, result.answer || result.message || JSON.stringify(result))
      persistMessages(beeKey)
    }
  } catch (err) {
    store.updateMessage(beeKey, thinkingId, `[Error] ${err.message}`)
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() }
}

function autoGrow(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 140) + 'px'
}

function renderContent(content) {
  if (typeof content !== 'string') content = String(content || '')
  return content
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#039;').replace(/\n/g, '<br>')
}

defineExpose({ loadMessagesFromServer })
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════
   面板根节点 — 暖象牙基底
═══════════════════════════════════════════════════════ */
#panel-chat {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 20% 30%, rgba(245,166,35,0.07) 0%, transparent 55%),
    radial-gradient(ellipse at 80% 70%, rgba(255,200,80,0.05) 0%, transparent 50%),
    linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
}

/* ═══════════════════════════════════════════════════════
   蜂巢网格底纹（极淡 SVG 六边形平铺）
═══════════════════════════════════════════════════════ */
.honeycomb-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  /* 数学上严格无缝的 flat-top 六边形 tile (s=20, w=60, h=69.28) */
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='69.28'%3E%3Cpolygon points='40,17.32 30,34.64 10,34.64 0,17.32 10,0 30,0' fill='none' stroke='%23f5a623' stroke-opacity='0.07' stroke-width='1'/%3E%3Cpolygon points='70,34.64 60,51.96 40,51.96 30,34.64 40,17.32 60,17.32' fill='none' stroke='%23f5a623' stroke-opacity='0.07' stroke-width='1'/%3E%3Cpolygon points='40,51.96 30,69.28 10,69.28 0,51.96 10,34.64 30,34.64' fill='none' stroke='%23f5a623' stroke-opacity='0.07' stroke-width='1'/%3E%3C/svg%3E");
  background-size: 60px 69.28px;
  background-repeat: repeat;
  opacity: 1;
}

/* ═══════════════════════════════════════════════════════
   消息区滚动容器
═══════════════════════════════════════════════════════ */
.messages-wrap {
  position: relative;
  z-index: 1;
}

/* ═══════════════════════════════════════════════════════
   消息布局
═══════════════════════════════════════════════════════ */
.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  animation: msgIn 0.24s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes msgIn {
  from { opacity: 0; transform: translateY(14px) scale(0.97); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.msg-row.user { justify-content: flex-end; }

.msg-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 75%;
}

.msg-row.user .msg-col { align-items: flex-end; }
.msg-row.bot  .msg-col { align-items: flex-start; }

/* ═══════════════════════════════════════════════════════
   头像 — Bot 用 3D 蜂王 PNG
═══════════════════════════════════════════════════════ */
.msg-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.bot-avatar {
  background: transparent;
  /* 轻微金色光晕托底 */
  filter: drop-shadow(0 0 6px rgba(245,166,35,0.5));
}

.bot-queen-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
  mix-blend-mode: multiply;
  animation: avatar-breathe 4s ease-in-out infinite alternate;
}

@keyframes avatar-breathe {
  0%   { filter: drop-shadow(0 0 4px rgba(245,166,35,0.5));  transform: scale(0.96); }
  100% { filter: drop-shadow(0 0 10px rgba(245,166,35,0.85)); transform: scale(1.04); }
}

.user-avatar {
  background: var(--text-primary);
  color: var(--bg-app);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0;
  border-radius: 50%;
  width: 40px;
  height: 40px;
}

/* ═══════════════════════════════════════════════════════
   气泡 — 半透明奶白毛玻璃
═══════════════════════════════════════════════════════ */
.msg-bubble {
  padding: 12px 16px;
  font-size: 0.9rem;
  line-height: 1.7;
  word-break: break-word;
  border-radius: 4px 18px 18px 18px;
}

/* Bot 气泡：奶白毛玻璃 + 琥珀金边 */
.msg-bubble.bot {
  background: rgba(255, 253, 248, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(245, 166, 35, 0.22);
  border-radius: 4px 18px 18px 18px;
  box-shadow:
    0 4px 24px rgba(180, 120, 40, 0.10),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  color: #2C2724;
}

/* User 气泡：暖琥珀金毛玻璃 */
.msg-bubble.user {
  background: rgba(245, 158, 11, 0.14);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(245, 158, 11, 0.38);
  border-radius: 18px 4px 18px 18px;
  box-shadow:
    0 4px 20px rgba(180, 120, 10, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  color: #2C2724;
}

.msg-time {
  font-size: 0.64rem;
  color: var(--text-muted);
  padding: 0 4px;
  opacity: 0.7;
}

/* ═══════════════════════════════════════════════════════
   思考动画
═══════════════════════════════════════════════════════ */
.thinking-dots {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 2px 4px;
  min-width: 44px;
}
.thinking-dots span {
  width: 7px;
  height: 7px;
  background: rgba(245, 166, 35, 0.6);
  border-radius: 50%;
  animation: bounce-dots 1.2s ease-in-out infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce-dots {
  0%, 100% { transform: translateY(0);   opacity: 0.35; }
  50%       { transform: translateY(-5px); opacity: 1; }
}

/* ═══════════════════════════════════════════════════════
   空状态 — 灵魂共振等待画面
═══════════════════════════════════════════════════════ */
.chat-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  pointer-events: none;
  user-select: none;
  z-index: 1;
}

.chat-empty-bee {
  margin-bottom: 4px;
}

.empty-queen-img {
  width: 120px;
  height: 120px;
  object-fit: contain;
  mix-blend-mode: multiply;
  animation: empty-pulse 5s ease-in-out infinite alternate;
}

@keyframes empty-pulse {
  0%   { filter: drop-shadow(0 0 10px rgba(245,166,35,0.4));  transform: scale(0.95) translateY(4px);  opacity: 0.8; }
  100% { filter: drop-shadow(0 0 28px rgba(245,166,35,0.75)); transform: scale(1.05) translateY(-4px); opacity: 1; }
}

.chat-empty-title {
  font-size: 1.05rem;
  font-weight: 800;
  color: #3A2A1A;
  letter-spacing: 0.04em;
}
.chat-empty-sub {
  font-size: 0.8rem;
  color: #7A6A5A;
}

/* 灵魂共振蜜蜂网格 */
.resonance-grid {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.resonance-grid-label {
  font-size: .65rem;
  font-weight: 700;
  letter-spacing: .1em;
  color: #B5A898;
  text-transform: uppercase;
}
.resonance-bee-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 400px;
}
.resonance-bee-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: rgba(255,255,255,.75);
  border: 1.5px solid rgba(245,166,35,.22);
  border-radius: 99px;
  cursor: pointer;
  font-family: inherit;
  font-size: .78rem;
  color: #4A3A2A;
  font-weight: 600;
  transition: all .18s;
  backdrop-filter: blur(6px);
}
.resonance-bee-card:hover {
  background: rgba(245,166,35,.12);
  border-color: rgba(245,166,35,.50);
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(245,166,35,.25);
}
.resonance-bee-card.active {
  background: linear-gradient(135deg,rgba(245,166,35,.18),rgba(255,200,80,.10));
  border-color: #D97706;
  color: #D97706;
}
.rb-avatar { font-size: 1rem; }
.rb-name { font-size: .75rem; font-weight: 700; }
.rb-dot {
  width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0;
}
.rb-dot.online  { background: #16a34a; box-shadow: 0 0 5px #16a34a; }
.rb-dot.busy    { background: #F59E0B; box-shadow: 0 0 5px #F59E0B; }
.rb-dot.offline { background: #9ca3af; }

/* ═══════════════════════════════════════════════════════
   输入区 — 毛玻璃底栏
═══════════════════════════════════════════════════════ */
.chat-input-bar {
  position: relative;
  z-index: 2;
  background: rgba(255, 253, 248, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(245, 166, 35, 0.18);
}

.input-wrap {
  flex: 1;
  position: relative;
}

.input-hint {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 0.62rem;
  color: var(--text-muted);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s;
}

.input-wrap:focus-within .input-hint { opacity: 1; }

.chat-send-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #D97706, #F59E0B);
  color: white;
  border: none;
  border-radius: var(--radius);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.4);
}
.chat-send-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #B45309, #D97706);
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(245, 158, 11, 0.55);
}
.chat-send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* Tabs 区域层级修正 */
.bee-tabs {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(255,253,248,.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(245,166,35,.18);
  overflow-x: auto;
  flex-shrink: 0;
}
.bee-tabs::-webkit-scrollbar { height: 2px; }
.bee-tabs::-webkit-scrollbar-thumb { background: rgba(245,166,35,.25); }

.bee-tabs-empty {
  font-size: .72rem; color: #B5A898; padding: 4px 8px;
}

.bee-tab {
  display: flex; align-items: center; gap: 5px;
  padding: 5px 12px; border-radius: 99px;
  border: 1px solid rgba(245,166,35,.22);
  background: rgba(255,255,255,.60);
  font-size: .74rem; font-weight: 600; color: #7A6A5A;
  cursor: pointer; white-space: nowrap; flex-shrink: 0;
  transition: all .15s;
}
.bee-tab:hover { background: rgba(245,166,35,.10); border-color: rgba(245,166,35,.40); }
.bee-tab.active {
  background: rgba(245,158,11,.12);
  border-color: rgba(245,158,11,.45);
  color: #92400e; font-weight: 700;
  box-shadow: 0 2px 10px rgba(245,158,11,.18);
}
.bee-tab--worker.active {
  background: rgba(134,239,172,.15);
  border-color: rgba(22,163,74,.35);
  color: #166534;
}

.tab-avatar { font-size: .9rem; }
.tab-label  { max-width: 80px; overflow: hidden; text-overflow: ellipsis; }
.tab-pid    { font-size: .58rem; font-family: monospace; opacity: .6; }
.tab-close {
  font-size: .72rem; line-height: 1; opacity: 0;
  width: 14px; height: 14px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  transition: opacity .15s, background .15s;
  flex-shrink: 0; margin-left: 1px;
}
.bee-tab:hover .tab-close { opacity: .45; }
.tab-close:hover { opacity: 1 !important; background: rgba(0,0,0,.12); }

.status-dot {
  width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0;
  animation: led-pulse 2s ease-in-out infinite;
}
.status-dot.online  { background: #16a34a; box-shadow: 0 0 5px #16a34a; }
.status-dot.busy    { background: #F59E0B; box-shadow: 0 0 5px #F59E0B; }
.status-dot.offline { background: #9ca3af; animation: none; }

/* 工作蜂头像气泡 */
.bot-avatar--worker {
  background: rgba(220,252,231,.80) !important;
  border: 1.5px solid rgba(22,163,74,.30) !important;
  border-radius: 50%;
}
.bot-worker-emoji {
  font-size: 1.5rem;
  animation: avatar-breathe 4s ease-in-out infinite alternate;
}
</style>
