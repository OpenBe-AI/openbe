<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="visible" class="bdm-overlay" @click.self="$emit('close')">
        <div class="bdm-card">

          <!-- 头部 -->
          <div class="bdm-header">
            <span class="bdm-glyph">🔮</span>
            <div>
              <div class="bdm-title">思维共振</div>
              <div class="bdm-sub">选择两只蜜蜂，让她们展开对话</div>
            </div>
            <button class="bdm-close" @click="$emit('close')">✕</button>
          </div>

          <!-- 蜜蜂选择行 -->
          <div class="bdm-bee-row">
            <div class="bdm-bee-slot" :class="{ selected: beeA }" @click="clearBee('A')" :title="beeA ? '点击取消' : ''">
              <template v-if="beeA">
                <img class="bdm-bee-img" :src="`/assets/bees/${speciesKey(beeA).toLowerCase()}.png`" @error="onImgError"/>
                <div class="bdm-bee-info">
                  <span class="bdm-bee-name">{{ beeA.beeName || speciesKey(beeA) }}</span>
                  <span class="bdm-bee-sp">{{ speciesKey(beeA) }}</span>
                </div>
                <span class="bdm-unselect">✕</span>
              </template>
              <template v-else>
                <div class="bdm-bee-empty">🐝<br><span>选择蜜蜂 A</span></div>
              </template>
            </div>

            <div class="bdm-vs">⚡</div>

            <div class="bdm-bee-slot" :class="{ selected: beeB }" @click="clearBee('B')" :title="beeB ? '点击取消' : ''">
              <template v-if="beeB">
                <img class="bdm-bee-img" :src="`/assets/bees/${speciesKey(beeB).toLowerCase()}.png`" @error="onImgError"/>
                <div class="bdm-bee-info">
                  <span class="bdm-bee-name">{{ beeB.beeName || speciesKey(beeB) }}</span>
                  <span class="bdm-bee-sp">{{ speciesKey(beeB) }}</span>
                </div>
                <span class="bdm-unselect">✕</span>
              </template>
              <template v-else>
                <div class="bdm-bee-empty">🐝<br><span>选择蜜蜂 B</span></div>
              </template>
            </div>
          </div>

          <!-- 从活跃蜜蜂中选择 -->
          <div v-if="!beeA || !beeB" class="bdm-pick-list">
            <div class="bdm-pick-title">{{ !beeA ? '选择蜜蜂 A' : '选择蜜蜂 B' }}</div>
            <div class="bdm-pick-grid">
              <button
                v-for="bee in availableBees"
                :key="bee._key"
                class="bdm-pick-btn"
                :disabled="(beeA && beeA._key === bee._key) || (beeB && beeB._key === bee._key)"
                @click="selectBee(bee)"
              >
                <img :src="`/assets/bees/${speciesKey(bee).toLowerCase()}.png`" @error="onImgError" class="bdm-pick-img"/>
                <span>{{ bee.beeName || speciesKey(bee) }}</span>
              </button>
            </div>
          </div>

          <!-- 话题 + 轮数 -->
          <div v-if="beeA && beeB && !running && messages.length === 0" class="bdm-config">
            <textarea
              class="bdm-topic"
              v-model="topic"
              placeholder="输入对话话题或开场白…（例：讨论一个 AI 时代的哲学问题）"
              rows="3"
            ></textarea>
            <div class="bdm-rounds-row">
              <label class="bdm-rounds-label">对话轮数</label>
              <input type="range" v-model.number="rounds" min="1" max="5" class="bdm-slider"/>
              <span class="bdm-rounds-val">{{ rounds }} 轮</span>
            </div>
            <button class="bdm-start-btn" :disabled="!topic.trim()" @click="runDialogue">
              ⚡ 开始思维共振
            </button>
          </div>

          <!-- 对话消息流 -->
          <div v-if="messages.length > 0" class="bdm-chat" ref="chatEl">
            <div v-for="(msg, i) in messages" :key="i" class="bdm-msg" :class="msg.from === 'A' ? 'from-a' : 'from-b'">
              <div class="bdm-msg-avatar">
                <img :src="`/assets/bees/${msg.species.toLowerCase()}.png`" @error="onImgError"/>
              </div>
              <div class="bdm-msg-body">
                <div class="bdm-msg-name">{{ msg.name }}</div>
                <div class="bdm-msg-bubble" :class="{ loading: msg.loading }">
                  <template v-if="msg.loading">
                    <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                  </template>
                  <template v-else>{{ msg.content }}</template>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部操作 -->
          <div v-if="messages.length > 0" class="bdm-footer">
            <button v-if="running" class="bdm-stop-btn" @click="stop">⏹ 停止</button>
            <button v-else class="bdm-reset-btn" @click="reset">🔄 重新开始</button>
            <span class="bdm-progress" v-if="running">第 {{ currentRound }} / {{ rounds }} 轮…</span>
            <span class="bdm-done" v-else-if="messages.length > 0">共振完成 ✨</span>
          </div>

        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { useApi } from '../composables/useApi.js'
import { useWebSocket } from '../composables/useWebSocket.js'
import { useAppStore } from '../stores/app.js'

const props = defineProps({ visible: Boolean })
const emit  = defineEmits(['close'])

const api   = useApi()
const store = useAppStore()
const { onTaskComplete } = useWebSocket()

// ── 蜂种 key ─────────────────────────────────────────────────
const SPECIES_META = {
  WORKER:'',SOLDIER:'',NURSE:'',SCOUT:'',PAINTER:'',
  MECHANIC:'',MEDIC:'',SCRIBE:'',EDITOR:'',INFLUENCER:'',SENTINEL:'',
}
function speciesKey(bee) {
  if (!bee) return 'WORKER'
  const ds = (bee.displaySpecies || '').toUpperCase()
  if (ds && ds in SPECIES_META) return ds
  const id = bee.beeId || ''
  if (id.includes('-')) {
    const p = id.split('-')[0].toUpperCase()
    if (p in SPECIES_META) return p
  }
  return (bee.beeType || bee.type || 'WORKER').toUpperCase()
}
function onImgError(e) { e.target.src = '/assets/bees/worker.png' }

// ── 活跃蜜蜂列表 ──────────────────────────────────────────────
const availableBees = computed(() =>
  Object.entries(store.bees)
    .filter(([, b]) => (b.beeType || b.type || '').toLowerCase() !== 'queen')
    .map(([key, b]) => ({ ...b, _key: key }))
)

// ── 选蜂 ─────────────────────────────────────────────────────
const beeA = ref(null)
const beeB = ref(null)

function selectBee(bee) {
  if (!beeA.value) { beeA.value = bee; return }
  if (!beeB.value && beeA.value._key !== bee._key) beeB.value = bee
}
function clearBee(side) {
  if (side === 'A') beeA.value = null
  else              beeB.value = null
}

// ── 对话配置 ─────────────────────────────────────────────────
const topic   = ref('')
const rounds  = ref(3)

// ── 对话状态 ─────────────────────────────────────────────────
const messages     = ref([])
const running      = ref(false)
const currentRound = ref(0)
const chatEl       = ref(null)

function reset() {
  messages.value     = []
  running.value      = false
  currentRound.value = 0
  topic.value        = ''
}

watch(() => props.visible, v => { if (!v) reset() })

// ── 发消息给工作蜂（异步 WebSocket） ─────────────────────────
function sendToWorkerBee(beeKey, message) {
  const beeType = beeKey.split(':')[0]
  return api.sendChat(beeType, message).then(res => {
    const taskId = res.taskId || res.id
    if (!taskId) return res.answer || res.message || ''
    return new Promise((resolve, reject) => {
      onTaskComplete(taskId, answer => resolve(answer || ''))
      setTimeout(() => reject(new Error('响应超时 (120s)')), 120000)
    })
  })
}

// ── 发消息给蜂王（同步直连） ──────────────────────────────────
function sendToQueenBee(hiveId, message) {
  return api.sendHiveChat(hiveId, message).then(res => res.answer || '')
}

async function sendToBee(bee, message) {
  const type = (bee.beeType || bee.type || '').toLowerCase()
  if (type === 'queen' && bee.hiveId) return sendToQueenBee(bee.hiveId, message)
  return sendToWorkerBee(bee._key, message)
}

// ── 滚动到底部 ────────────────────────────────────────────────
async function scrollBottom() {
  await nextTick()
  if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight
}

// ── 主对话循环 ────────────────────────────────────────────────
async function runDialogue() {
  if (!beeA.value || !beeB.value || !topic.value.trim()) return
  running.value      = true
  currentRound.value = 0
  messages.value     = []

  let lastInput = topic.value.trim()

  for (let r = 0; r < rounds.value && running.value; r++) {
    currentRound.value = r + 1

    // ── Bee A 回应 ──
    const msgA = reactive({
      from:    'A',
      name:    beeA.value.beeName || speciesKey(beeA.value),
      species: speciesKey(beeA.value),
      content: '',
      loading: true,
    })
    messages.value.push(msgA)
    await scrollBottom()

    try {
      msgA.content = await sendToBee(beeA.value, lastInput)
    } catch (e) {
      msgA.content = `[错误: ${e.message}]`
    }
    msgA.loading = false
    await scrollBottom()
    if (!running.value) break

    // ── Bee B 回应 ──
    const msgB = reactive({
      from:    'B',
      name:    beeB.value.beeName || speciesKey(beeB.value),
      species: speciesKey(beeB.value),
      content: '',
      loading: true,
    })
    messages.value.push(msgB)
    await scrollBottom()

    try {
      msgB.content = await sendToBee(beeB.value, msgA.content)
    } catch (e) {
      msgB.content = `[错误: ${e.message}]`
    }
    msgB.loading = false
    await scrollBottom()

    lastInput = msgB.content  // B 的回应作为下一轮 A 的输入
  }

  running.value = false
}

function stop() { running.value = false }
</script>

<style scoped>
/* ── 遮罩 ── */
.bdm-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,.40); backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center; padding: 16px;
}

/* ── 卡片 ── */
.bdm-card {
  width: 560px; max-width: 96vw; max-height: 88vh;
  background: rgba(255,255,255,.78);
  border: 1.5px solid rgba(255,255,255,.90);
  border-radius: 28px; backdrop-filter: blur(30px);
  box-shadow: 0 28px 70px rgba(0,0,0,.20), 0 0 0 1px rgba(255,255,255,.45);
  display: flex; flex-direction: column; overflow: hidden;
}

/* ── 头部 ── */
.bdm-header {
  display: flex; align-items: center; gap: 12px; padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(0,0,0,.06); flex-shrink: 0;
}
.bdm-glyph { font-size: 1.7rem; }
.bdm-title { font-size: .95rem; font-weight: 800; color: #3a2d22; }
.bdm-sub   { font-size: .66rem; color: #a1887f; margin-top: 1px; }
.bdm-close {
  margin-left: auto; background: rgba(0,0,0,.06); border: none; border-radius: 50%;
  width: 28px; height: 28px; cursor: pointer; font-size: .8rem; color: #8d6e63;
  display: flex; align-items: center; justify-content: center;
}
.bdm-close:hover { background: rgba(0,0,0,.12); }

/* ── 蜜蜂选择行 ── */
.bdm-bee-row {
  display: flex; align-items: center; gap: 10px; padding: 14px 18px;
  flex-shrink: 0;
}
.bdm-bee-slot {
  flex: 1; border-radius: 16px; border: 2px dashed rgba(161,136,127,.35);
  padding: 10px 14px; display: flex; align-items: center; gap: 10px;
  cursor: pointer; transition: all .2s; min-height: 70px;
  background: rgba(255,255,255,.50);
}
.bdm-bee-slot.selected { border-style: solid; border-color: rgba(249,168,37,.55); background: rgba(255,249,196,.40); }
.bdm-bee-slot:hover { background: rgba(249,168,37,.08); }
.bdm-bee-img { width: 44px; height: 44px; object-fit: contain; flex-shrink: 0; }
.bdm-bee-info { flex: 1; }
.bdm-bee-name { display: block; font-size: .78rem; font-weight: 800; color: #3a2d22; }
.bdm-bee-sp   { font-size: .60rem; color: #a1887f; text-transform: uppercase; }
.bdm-unselect { font-size: .7rem; color: rgba(161,136,127,.55); margin-left: auto; }
.bdm-bee-empty { text-align: center; color: rgba(161,136,127,.60); font-size: .72rem; line-height: 1.8; width: 100%; }
.bdm-vs { font-size: 1.4rem; flex-shrink: 0; opacity: .7; animation: vs-pulse 1.5s ease-in-out infinite alternate; }
@keyframes vs-pulse { from { transform: scale(.9); } to { transform: scale(1.1); } }

/* ── 蜜蜂选择列表 ── */
.bdm-pick-list { padding: 0 18px 14px; flex-shrink: 0; }
.bdm-pick-title { font-size: .72rem; font-weight: 700; color: #a1887f; margin-bottom: 8px; }
.bdm-pick-grid { display: flex; flex-wrap: wrap; gap: 7px; }
.bdm-pick-btn {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  background: rgba(255,255,255,.70); border: 1.5px solid rgba(245,166,35,.25);
  border-radius: 12px; padding: 7px 12px; cursor: pointer; min-width: 70px;
  font-size: .65rem; font-weight: 700; color: #5d4037;
  transition: all .18s cubic-bezier(.175,.885,.32,1.275);
}
.bdm-pick-btn:hover:not(:disabled) { transform: translateY(-2px) scale(1.06); border-color: rgba(245,166,35,.55); background: rgba(255,249,196,.60); }
.bdm-pick-btn:disabled { opacity: .35; cursor: not-allowed; transform: none; }
.bdm-pick-img { width: 36px; height: 36px; object-fit: contain; }

/* ── 配置区 ── */
.bdm-config { padding: 0 18px 16px; display: flex; flex-direction: column; gap: 10px; flex-shrink: 0; }
.bdm-topic {
  width: 100%; box-sizing: border-box; resize: none;
  background: rgba(255,255,255,.70); border: 1.5px solid rgba(245,166,35,.30);
  border-radius: 12px; padding: 10px 14px; font-size: .82rem; color: #3a2d22;
  outline: none; font-family: inherit; line-height: 1.6;
}
.bdm-topic:focus { border-color: rgba(245,166,35,.65); }
.bdm-topic::placeholder { color: #c5b5a0; }
.bdm-rounds-row { display: flex; align-items: center; gap: 10px; }
.bdm-rounds-label { font-size: .72rem; color: #a1887f; font-weight: 600; flex-shrink: 0; }
.bdm-slider { flex: 1; accent-color: #f9a825; }
.bdm-rounds-val { font-size: .72rem; font-weight: 800; color: #5d4037; width: 36px; text-align: right; }
.bdm-start-btn {
  padding: 10px 0; border-radius: 14px; border: none; width: 100%;
  background: linear-gradient(135deg, #ffe082, #f9a825);
  color: #3a2d22; font-size: .85rem; font-weight: 800; cursor: pointer; letter-spacing: .04em;
  box-shadow: 0 4px 16px rgba(249,168,37,.40);
  transition: transform .2s cubic-bezier(.175,.885,.32,1.275), box-shadow .2s;
}
.bdm-start-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 7px 24px rgba(249,168,37,.55); }
.bdm-start-btn:disabled { opacity: .45; cursor: not-allowed; transform: none; }

/* ── 对话消息流 ── */
.bdm-chat {
  flex: 1; overflow-y: auto; padding: 10px 18px; display: flex; flex-direction: column; gap: 12px;
  min-height: 0;
}
.bdm-chat::-webkit-scrollbar { width: 3px; }
.bdm-chat::-webkit-scrollbar-thumb { background: rgba(245,166,35,.30); border-radius: 3px; }

.bdm-msg { display: flex; gap: 10px; align-items: flex-start; animation: msg-in .3s cubic-bezier(.175,.885,.32,1.275); }
@keyframes msg-in { from { opacity:0; transform: translateY(8px) scale(.97); } }
.bdm-msg.from-b { flex-direction: row-reverse; }

.bdm-msg-avatar img {
  width: 42px; height: 42px; object-fit: contain; border-radius: 50%;
  background: rgba(255,255,255,.75); box-shadow: 0 2px 10px rgba(0,0,0,.10);
  padding: 3px;
}

.bdm-msg-body { max-width: 75%; display: flex; flex-direction: column; gap: 3px; }
.bdm-msg.from-b .bdm-msg-body { align-items: flex-end; }

.bdm-msg-name { font-size: .60rem; font-weight: 700; color: #a1887f; padding: 0 4px; }

.bdm-msg-bubble {
  padding: 10px 14px; border-radius: 4px 16px 16px 16px;
  background: rgba(255,253,248,.85); border: 1px solid rgba(245,166,35,.20);
  backdrop-filter: blur(8px); font-size: .82rem; line-height: 1.65; color: #3a2d22;
  box-shadow: 0 3px 14px rgba(0,0,0,.07);
  white-space: pre-wrap; word-break: break-word;
}
.bdm-msg.from-b .bdm-msg-bubble {
  border-radius: 16px 4px 16px 16px;
  background: rgba(179,229,252,.40); border-color: rgba(30,136,229,.20);
}
.bdm-msg-bubble.loading {
  display: flex; gap: 5px; align-items: center; min-width: 52px;
}
.dot { width: 7px; height: 7px; border-radius: 50%; background: rgba(245,166,35,.60); animation: dot-bounce 1.2s ease-in-out infinite; }
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes dot-bounce { 0%,100% { transform: translateY(0); opacity:.35; } 50% { transform: translateY(-5px); opacity:1; } }

/* ── 底部操作 ── */
.bdm-footer {
  display: flex; align-items: center; gap: 10px; padding: 10px 18px 16px; flex-shrink: 0;
  border-top: 1px solid rgba(0,0,0,.06);
}
.bdm-stop-btn {
  padding: 7px 18px; border-radius: 10px; border: none;
  background: rgba(239,68,68,.12); color: #dc2626; font-weight: 700; font-size: .78rem; cursor: pointer;
}
.bdm-stop-btn:hover { background: rgba(239,68,68,.22); }
.bdm-reset-btn {
  padding: 7px 18px; border-radius: 10px; border: 1px solid rgba(245,166,35,.35);
  background: rgba(255,249,196,.50); color: #92400e; font-weight: 700; font-size: .78rem; cursor: pointer;
}
.bdm-reset-btn:hover { background: rgba(255,249,196,.85); }
.bdm-progress { font-size: .70rem; color: #a1887f; font-style: italic; }
.bdm-done     { font-size: .70rem; color: #43a047; font-weight: 700; }

/* ── 弹窗过渡 ── */
.modal-fade-enter-active { animation: m-in .32s cubic-bezier(.175,.885,.32,1.275); }
.modal-fade-leave-active { animation: m-in .22s ease-in reverse; }
@keyframes m-in { from { opacity:0; transform: scale(.82) translateY(24px); } }
</style>
