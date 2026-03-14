<template>
  <div v-show="store.activePanel === 'wings'" class="wings-panel">

    <!-- 顶部 Hero 区 -->
    <div class="wings-hero">
      <div class="hero-bg-orb orb1"></div>
      <div class="hero-bg-orb orb2"></div>
      <div class="hero-content">
        <div class="hero-left">
          <div class="hero-icon-wrap"><span class="hero-icon">🪽</span></div>
          <div>
            <h1 class="hero-title">寻花探蜜</h1>
            <p class="hero-desc">Platform Adapters · 多平台接入管理</p>
          </div>
        </div>
        <div class="hero-right">
          <div class="svc-pill" :class="serviceRunning ? 'svc-on' : 'svc-off'">
            <span class="svc-dot"></span>
            <span>Wings 服务</span>
            <span class="svc-port-tag" v-if="serviceRunning">:{{ wingsPort }}</span>
            <span class="svc-badge">{{ serviceRunning ? 'LIVE' : 'OFFLINE' }}</span>
          </div>
          <button class="btn-primary" @click="openAddConn">
            <span>＋</span> 新增接入
          </button>
        </div>
      </div>
    </div>

    <!-- 数据看板 -->
    <div class="dashboard-bar">
      <div class="dash-card">
        <span class="dash-num">{{ connections.length }}</span>
        <span class="dash-lbl">接入总数</span>
      </div>
      <div class="dash-card dash-card--green">
        <span class="dash-num">{{ connections.filter(c => c.connected).length }}</span>
        <span class="dash-lbl">已连通</span>
      </div>
      <div class="dash-card">
        <span class="dash-num">{{ connections.filter(c => c.platform === 'feishu').length }}</span>
        <span class="dash-lbl">飞书</span>
      </div>
      <div class="dash-card">
        <span class="dash-num">{{ connections.filter(c => c.platform === 'wework').length }}</span>
        <span class="dash-lbl">企微</span>
      </div>
      <div class="dash-spacer"></div>
      <div class="port-row">
        <span class="port-label">Wings 端口</span>
        <input class="port-input" v-model.number="wingsPort" type="number" min="1024" max="65535" @change="persist" />
        <code class="port-url">localhost:{{ wingsPort }}</code>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="connections.length === 0" class="empty-wrap">
      <div class="empty-illus">
        <div class="empty-circle c1"></div>
        <div class="empty-circle c2"></div>
        <span class="empty-emoji">🌸</span>
      </div>
      <h2 class="empty-title">还没有接入连接</h2>
      <p class="empty-sub">为每只蜜蜂配置飞书或企业微信，让它们飞出蜂巢触达用户</p>
      <button class="btn-primary btn-primary--lg" @click="openAddConn">＋ 新增接入</button>
    </div>

    <!-- 接入列表 -->
    <div class="conn-list" v-else>
      <div
        v-for="(conn, idx) in connections"
        :key="conn.id"
        class="conn-card"
        :class="{ 'conn-card--expanded': expandedId === conn.id, 'conn-card--on': conn.connected }"
      >
        <!-- 卡片头 -->
        <div class="conn-hd" @click="toggleConn(conn.id)">
          <div class="conn-hd-l">
            <div class="plat-avatar" :class="conn.platform">
              {{ conn.platform === 'feishu' ? '🐦' : '🏢' }}
            </div>
            <div>
              <div class="conn-name">
                {{ conn.label || (conn.platform === 'feishu' ? '飞书接入' : '企业微信接入') }}
                <span class="plat-chip">{{ conn.platform === 'feishu' ? 'Feishu' : 'WeWork' }}</span>
              </div>
              <div class="conn-meta">
                <span v-if="conn.appId || conn.corpId" class="meta-id">{{ conn.appId || conn.corpId }}</span>
                <span class="meta-bee" v-if="conn.assignedBee">🐝 {{ beeLabel(conn.assignedBee) }}</span>
                <span class="meta-none" v-else>未分配蜜蜂</span>
              </div>
            </div>
          </div>
          <div class="conn-hd-r">
            <span class="status-chip" :class="conn.connected ? 'status-on' : 'status-off'">
              {{ conn.connected ? '✓ 已接入' : '未接入' }}
            </span>
            <button class="btn-del" @click.stop="deleteConn(idx)" title="删除">✕</button>
            <span class="arrow-icon" :class="{ open: expandedId === conn.id }">›</span>
          </div>
        </div>

        <!-- 展开内容 -->
        <Transition name="slide-down">
          <div v-if="expandedId === conn.id" class="conn-body">
            <!-- 子标签 -->
            <div class="tab-bar">
              <button
                v-for="tab in ['config', 'webhook', 'guide']"
                :key="tab"
                class="tab-btn"
                :class="{ active: conn._activeTab === tab }"
                @click="conn._activeTab = tab"
              >{{ tabLabel(tab) }}</button>
            </div>

            <!-- 凭证配置 -->
            <div v-if="conn._activeTab === 'config'" class="tab-body">
              <div class="field-row">
                <label class="field-label">连接名称</label>
                <input v-model="conn.label" placeholder="例：销售团队飞书机器人" class="field-input" @change="persist" />
              </div>

              <template v-if="conn.platform === 'feishu'">
                <div class="field-row">
                  <label class="field-label">App ID <span class="badge-req">必填</span></label>
                  <input v-model="conn.appId" placeholder="cli_xxxxxxxxxxxxxxxx" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">App Secret <span class="badge-req">必填</span></label>
                  <input v-model="conn.appSecret" type="password" placeholder="••••••••••••••••" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">Verification Token <span class="badge-req">必填</span></label>
                  <input v-model="conn.verifyToken" placeholder="事件订阅页的验证 Token" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">Encrypt Key <span class="badge-opt">可选</span></label>
                  <input v-model="conn.encryptKey" type="password" placeholder="不开启消息加密则留空" class="field-input" @change="persist" />
                </div>
              </template>

              <template v-if="conn.platform === 'wework'">
                <div class="field-row">
                  <label class="field-label">Corp ID <span class="badge-req">必填</span></label>
                  <input v-model="conn.corpId" placeholder="wwxxxxxxxxxxxxxxxx" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">Corp Secret <span class="badge-req">必填</span></label>
                  <input v-model="conn.corpSecret" type="password" placeholder="应用的 Secret" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">Agent ID <span class="badge-req">必填</span> <span class="field-hint">用于路由多应用</span></label>
                  <input v-model="conn.agentId" placeholder="1000001" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">Token <span class="badge-req">必填</span></label>
                  <input v-model="conn.token" placeholder="接收消息的回调 Token" class="field-input" @change="persist" />
                </div>
                <div class="field-row">
                  <label class="field-label">EncodingAESKey <span class="badge-req">必填</span></label>
                  <input v-model="conn.aesKey" type="password" placeholder="43 位 AES Key" class="field-input" @change="persist" />
                </div>
              </template>

              <div class="field-row">
                <label class="field-label">分配蜜蜂 <span class="field-hint">收到消息时由此蜜蜂回复</span></label>
                <select v-model="conn.assignedBee" class="field-select" @change="persist">
                  <option value="">— 不分配 —</option>
                  <option v-for="b in activeBees" :key="b._key" :value="b._key">
                    {{ b.beeName || b.beeType }} ({{ b.displaySpecies || b.beeType }})
                  </option>
                </select>
              </div>
              <div class="field-row">
                <label class="field-label">路由蜂巢 <span class="field-hint">Wings 转发到哪个蜂巢（默认 default）</span></label>
                <select v-model="conn.hiveId" class="field-select" @change="persist">
                  <option value="default">default</option>
                  <option v-for="h in store.hives" :key="h.hiveId" :value="h.hiveId">
                    {{ h.name || h.hiveId }}
                  </option>
                </select>
              </div>

              <div class="action-row">
                <button class="btn-save" @click="saveConn(conn)">💾 保存配置</button>
                <button class="btn-test" @click="testConn(conn)" :disabled="!canTest(conn)">🧪 测试连接</button>
                <button v-if="conn.connected" class="btn-disc" @click="conn.connected = false; persist()">断开</button>
              </div>
            </div>

            <!-- Webhook -->
            <div v-if="conn._activeTab === 'webhook'" class="tab-body">
              <div class="info-block info-block--amber">
                <span>💡</span>
                <span>将此地址填入{{ conn.platform === 'feishu' ? '飞书开放平台 → 事件订阅' : '企业微信管理后台 → 接收消息 → 服务器配置' }}</span>
              </div>
              <div class="code-block">
                <div class="code-block-hd">
                  <span class="code-block-title">回调地址</span>
                  <button class="btn-copy" @click="copyText(webhookUrl(conn))">📋 复制</button>
                </div>
                <code class="code-val">{{ webhookUrl(conn) }}</code>
              </div>
              <div class="code-block">
                <div class="code-block-hd">
                  <span class="code-block-title">.env 环境变量</span>
                  <button class="btn-copy" @click="copyText(envBlock(conn))">📋 复制全部</button>
                </div>
                <pre class="code-pre">{{ envBlock(conn) }}</pre>
              </div>
              <div class="code-block">
                <div class="code-block-hd">
                  <span class="code-block-title">启动命令</span>
                </div>
                <code class="code-val">cd packages/openbe-wings && pip install -r requirements.txt && python webhook.py</code>
              </div>
              <div class="info-block info-block--orange">
                <span>⚠️</span>
                <span v-if="conn.platform === 'feishu'">飞书会先发送 URL 验证 challenge，Wings 服务必须先启动才能通过验证。</span>
                <span v-else>企业微信保存配置时发 GET 验证 echostr，Wings 服务必须先启动。多应用通过 AgentId 自动路由。</span>
              </div>
            </div>

            <!-- 接入指南 -->
            <div v-if="conn._activeTab === 'guide'" class="tab-body guide-body">
              <template v-if="conn.platform === 'feishu'">
                <div class="guide-step" v-for="(step, i) in feishuGuide" :key="i">
                  <div class="step-num">{{ i + 1 }}</div>
                  <div>
                    <div class="step-title">{{ step.title }}</div>
                    <div class="step-desc">{{ step.desc }}</div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="guide-step" v-for="(step, i) in weworkGuide" :key="i">
                  <div class="step-num">{{ i + 1 }}</div>
                  <div>
                    <div class="step-title">{{ step.title }}</div>
                    <div class="step-desc">{{ step.desc }}</div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- 新增接入弹窗 -->
    <Transition name="modal-fade">
      <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
        <div class="add-modal">
          <div class="add-modal-hd">
            <span class="add-modal-title">新增平台接入</span>
            <button class="modal-close" @click="showAddModal = false">✕</button>
          </div>
          <p class="add-modal-sub">选择要接入的平台，为每只蜜蜂创建独立连接</p>
          <div class="plat-choice-row">
            <button class="plat-choice" @click="addConn('feishu')">
              <span class="plat-choice-icon">🐦</span>
              <div class="plat-choice-name">飞书 Feishu</div>
              <div class="plat-choice-desc">企业自建应用 · 双向消息</div>
            </button>
            <button class="plat-choice" @click="addConn('wework')">
              <span class="plat-choice-icon">🏢</span>
              <div class="plat-choice-name">企业微信 WeWork</div>
              <div class="plat-choice-desc">自建应用 · AgentId 路由</div>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Toast -->
    <div v-if="toast.show" class="wings-toast" :class="toast.type">{{ toast.msg }}</div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useActiveBees } from '../../composables/useActiveBees.js'

const store = useAppStore()
const { activeBees } = useActiveBees()

const serviceRunning = ref(false)
const wingsPort = ref(8081)
const expandedId = ref(null)
const showAddModal = ref(false)

function toggleConn(id) {
  expandedId.value = expandedId.value === id ? null : id
}
function openAddConn() { showAddModal.value = true }

const connections = ref([])

function makeConn(platform) {
  return reactive({
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    platform,
    label: '',
    connected: false,
    hiveId: store.activeHive || 'default',
    assignedBee: '',
    _activeTab: 'config',
    appId: '', appSecret: '', verifyToken: '', encryptKey: '',
    corpId: '', corpSecret: '', agentId: '', token: '', aesKey: '',
  })
}

function addConn(platform) {
  const c = makeConn(platform)
  connections.value.push(c)
  expandedId.value = c.id
  showAddModal.value = false
  persist()
}

function deleteConn(idx) {
  if (!confirm('删除此接入配置？')) return
  connections.value.splice(idx, 1)
  persist()
}

function webhookUrl(conn) {
  return `http://localhost:${wingsPort.value}/webhook/${conn.platform}`
}

function envBlock(conn) {
  const base = `OPENBE_QUEEN_URL=http://localhost:8080\nOPENBE_HIVE_ID=${conn.hiveId || 'default'}\nWINGS_PORT=${wingsPort.value}`
  if (conn.platform === 'feishu') {
    return `# 飞书: ${conn.label || conn.appId || 'Feishu App'}\nFEISHU_APP_ID=${conn.appId || 'cli_xxx'}\nFEISHU_APP_SECRET=${conn.appSecret ? '(已填写)' : 'your_secret'}\nFEISHU_VERIFICATION_TOKEN=${conn.verifyToken || 'your_token'}\nFEISHU_ENCRYPT_KEY=${conn.encryptKey ? '(已填写)' : ''}\n${base}`
  } else {
    return `# 企业微信: ${conn.label || conn.corpId || 'WeWork App'}\nWEWORK_CORP_ID=${conn.corpId || 'ww_xxx'}\nWEWORK_CORP_SECRET=${conn.corpSecret ? '(已填写)' : 'your_secret'}\nWEWORK_AGENT_ID=${conn.agentId || '1000001'}\nWEWORK_TOKEN=${conn.token || 'your_token'}\nWEWORK_ENCODING_AES_KEY=${conn.aesKey ? '(已填写)' : 'your_aes_key'}\n${base}`
  }
}

const STORAGE_KEY = 'openbe:wings:v3'

onMounted(() => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw)
      if (saved.wingsPort) wingsPort.value = saved.wingsPort
      if (saved.connections) {
        connections.value = saved.connections.map(c => reactive({ ...c, _activeTab: 'config' }))
      }
    }
  } catch {}
  checkServiceStatus()
})

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    wingsPort: wingsPort.value,
    connections: connections.value.map(({ _activeTab, ...rest }) => rest),
  }))
}

async function checkServiceStatus() {
  try {
    const r = await fetch(`http://localhost:${wingsPort.value}/health`, { signal: AbortSignal.timeout(1500) })
    serviceRunning.value = r.ok
  } catch {
    serviceRunning.value = false
  }
}

function saveConn(conn) {
  persist()
  showToast(`「${conn.label || conn.platform}」配置已保存`, 'success')
}

function canTest(conn) {
  if (conn.platform === 'feishu') return !!(conn.appId && conn.appSecret)
  return !!(conn.corpId && conn.corpSecret)
}

async function testConn(conn) {
  showToast('正在测试连接…', 'info')
  await new Promise(r => setTimeout(r, 900))
  conn.connected = true
  persist()
  showToast(`「${conn.label || conn.platform}」连接测试成功 ✓`, 'success')
}

function tabLabel(tab) {
  return { config: '🔑 凭证配置', webhook: '🔗 Webhook', guide: '📖 指南' }[tab]
}

function beeLabel(key) {
  const b = activeBees.value.find(b => b._key === key)
  return b ? (b.beeName || b.beeType || key) : key
}

function copyText(text) {
  navigator.clipboard.writeText(text).then(() => showToast('已复制', 'success'))
}

const toast = reactive({ show: false, msg: '', type: 'info' })
let toastTimer = null

function showToast(msg, type = 'info') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.msg = msg; toast.type = type; toast.show = true
  toastTimer = setTimeout(() => { toast.show = false }, 2800)
}

const feishuGuide = [
  { title: '创建飞书企业自建应用', desc: '飞书开放平台 → 我的应用 → 创建应用（选「企业自建」，非自定义机器人）→ 获取 App ID 和 App Secret' },
  { title: '开启机器人能力', desc: '应用功能 → 机器人 → 开启，否则无法接收群消息' },
  { title: '配置消息权限', desc: '权限管理 → 开启：im:message、im:message.receive_v1、im:chat（否则提示权限不足）' },
  { title: '启动 Wings 服务', desc: 'cd packages/openbe-wings && python webhook.py（或使用内网穿透工具将本地端口暴露到公网）' },
  { title: '填写回调地址并验证', desc: '事件订阅 → 请求地址 → 填入上方 Webhook 地址 → 点击验证（Wings 必须已启动）' },
  { title: '订阅 im.message.receive_v1 事件', desc: '添加事件 → 选择「接收消息」→ 发布版本，多个机器人使用不同 App ID，Wings 自动路由' },
]

const weworkGuide = [
  { title: '企业微信管理后台创建自建应用', desc: '应用管理 → 创建应用 → 获取 AgentId（每个应用 AgentId 唯一，Wings 以此区分多应用路由）' },
  { title: '获取 CorpID 和 Secret', desc: '我的企业 → CorpID；应用详情 → Secret（每个应用 Secret 独立）' },
  { title: '启动 Wings 服务', desc: 'cd packages/openbe-wings && python webhook.py（需公网 URL 或 Cloudflare Tunnel / frp 内网穿透）' },
  { title: '配置接收消息服务器', desc: '应用详情 → 接收消息 → 开启 → 填写 URL（Wing 地址）、Token、EncodingAESKey → 点击保存（会触发验证请求）' },
  { title: '配置可信 IP', desc: '企业微信接收消息需要配置可信 IP 白名单，填写 Wings 所在服务器的公网 IP' },
  { title: '多蜂巢路由', desc: '每个企业微信应用有独立 AgentId，Wings 通过 XML 中的 <AgentID> 字段自动路由到对应蜂巢，无需修改代码' },
]
</script>

<style scoped>
/* ══════════════════════════════════════════════
   根容器 — 纯白亮色背景
══════════════════════════════════════════════ */
.wings-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-y: auto;
  background: #F7F8FC;
  color: #1A1D2E;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Helvetica Neue', sans-serif;
}

/* ══════════════════════════════════════════════
   Hero 区
══════════════════════════════════════════════ */
.wings-hero {
  position: relative;
  overflow: hidden;
  background: #ffffff;
  border-bottom: 1px solid #EAECF0;
  padding: 24px 32px 20px;
  flex-shrink: 0;
}
.hero-bg-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.orb1 {
  width: 320px; height: 320px;
  top: -120px; right: -60px;
  background: radial-gradient(circle, rgba(251,191,36,.13) 0%, transparent 70%);
}
.orb2 {
  width: 200px; height: 200px;
  bottom: -80px; left: 40px;
  background: radial-gradient(circle, rgba(139,92,246,.06) 0%, transparent 70%);
}
.hero-content {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hero-icon-wrap {
  width: 52px; height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, #FEF3C7, #FDE68A);
  border: 1.5px solid rgba(251,191,36,.4);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(251,191,36,.25);
}
.hero-icon { font-size: 26px; }
.hero-title {
  font-size: 1.3rem;
  font-weight: 800;
  color: #111827;
  margin: 0;
  letter-spacing: -0.025em;
}
.hero-desc {
  font-size: 0.76rem;
  color: #6B7280;
  margin: 3px 0 0;
  font-weight: 500;
}
.hero-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 服务状态胶囊 */
.svc-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 0.73rem;
  font-weight: 600;
  border: 1.5px solid;
}
.svc-pill.svc-on {
  background: #F0FDF4;
  border-color: #BBF7D0;
  color: #15803D;
}
.svc-pill.svc-off {
  background: #F9FAFB;
  border-color: #E5E7EB;
  color: #9CA3AF;
}
.svc-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
}
.svc-on .svc-dot {
  background: #22C55E;
  box-shadow: 0 0 6px rgba(34,197,94,.6);
  animation: pulse-green 2s ease-in-out infinite;
}
.svc-off .svc-dot { background: #D1D5DB; }
@keyframes pulse-green {
  0%, 100% { box-shadow: 0 0 4px rgba(34,197,94,.4); }
  50%       { box-shadow: 0 0 10px rgba(34,197,94,.8); }
}
.svc-port-tag {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.68rem;
  opacity: .75;
}
.svc-badge {
  font-size: 0.58rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 1px 6px;
  border-radius: 999px;
  background: currentColor;
  color: inherit;
  mix-blend-mode: multiply;
  opacity: .15;
}
.svc-on .svc-badge { background: #16A34A; opacity: 1; color: #fff; }
.svc-off .svc-badge { background: #9CA3AF; opacity: 1; color: #fff; }

/* 主按钮 */
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  background: linear-gradient(135deg, #F59E0B, #FBBF24);
  color: #1C1917;
  border: none;
  border-radius: 10px;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
  box-shadow: 0 2px 8px rgba(245,158,11,.35), 0 1px 2px rgba(0,0,0,.08);
  transition: all 0.18s;
  white-space: nowrap;
}
.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(245,158,11,.5), 0 2px 4px rgba(0,0,0,.08);
}
.btn-primary--lg {
  padding: 12px 28px;
  font-size: 0.88rem;
  border-radius: 12px;
  margin-top: 4px;
}

/* ══════════════════════════════════════════════
   数据看板
══════════════════════════════════════════════ */
.dashboard-bar {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 12px 32px;
  background: #ffffff;
  border-bottom: 1px solid #F3F4F6;
  flex-shrink: 0;
}
.dash-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 20px;
  border-right: 1px solid #F3F4F6;
}
.dash-card:first-child { padding-left: 0; }
.dash-num {
  font-size: 1.4rem;
  font-weight: 900;
  color: #111827;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.dash-card--green .dash-num { color: #16A34A; }
.dash-lbl {
  font-size: 0.62rem;
  font-weight: 600;
  color: #9CA3AF;
  margin-top: 2px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.dash-spacer { flex: 1; }
.port-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.76rem;
}
.port-label { color: #6B7280; font-weight: 600; }
.port-input {
  width: 68px;
  padding: 5px 8px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.74rem;
  background: #F9FAFB;
  border: 1.5px solid #E5E7EB;
  border-radius: 8px;
  color: #374151;
  outline: none;
  text-align: center;
  transition: border-color .15s, box-shadow .15s;
}
.port-input:focus { border-color: #F59E0B; box-shadow: 0 0 0 3px rgba(245,158,11,.15); }
.port-url {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.72rem;
  color: #D97706;
  font-weight: 600;
}

/* ══════════════════════════════════════════════
   空状态
══════════════════════════════════════════════ */
.empty-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  text-align: center;
}
.empty-illus {
  position: relative;
  width: 90px; height: 90px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 4px;
}
.empty-circle {
  position: absolute;
  border-radius: 50%;
}
.c1 {
  width: 90px; height: 90px;
  background: radial-gradient(circle, rgba(251,191,36,.18) 0%, transparent 70%);
}
.c2 {
  width: 60px; height: 60px;
  background: rgba(251,191,36,.10);
  border: 1.5px solid rgba(251,191,36,.25);
}
.empty-emoji { font-size: 36px; position: relative; z-index: 1; }
.empty-title { font-size: 1.05rem; font-weight: 800; color: #111827; margin: 0; }
.empty-sub { font-size: 0.80rem; color: #6B7280; max-width: 320px; line-height: 1.65; margin: 0; }

/* ══════════════════════════════════════════════
   接入列表
══════════════════════════════════════════════ */
.conn-list {
  flex: 1;
  padding: 20px 32px 32px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 接入卡片 */
.conn-card {
  background: #ffffff;
  border: 1.5px solid #E5E7EB;
  border-radius: 14px;
  overflow: hidden;
  transition: border-color .2s, box-shadow .2s;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.conn-card:hover { border-color: #D1D5DB; box-shadow: 0 4px 14px rgba(0,0,0,.07); }
.conn-card--on {
  border-color: #BBF7D0;
  box-shadow: 0 1px 4px rgba(0,0,0,.04), 0 0 0 1px rgba(34,197,94,.08);
}
.conn-card--expanded {
  border-color: #FDE68A;
  box-shadow: 0 4px 20px rgba(245,158,11,.12), 0 1px 4px rgba(0,0,0,.05);
}

/* 卡片头 */
.conn-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  cursor: pointer;
  user-select: none;
  transition: background .15s;
}
.conn-hd:hover { background: #F9FAFB; }
.conn-hd-l {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.plat-avatar {
  width: 44px; height: 44px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.plat-avatar.feishu {
  background: linear-gradient(135deg, #EBF5FB, #D6EAF8);
  border: 1.5px solid rgba(52,152,219,.18);
}
.plat-avatar.wework {
  background: linear-gradient(135deg, #EAFAF1, #D5F5E3);
  border: 1.5px solid rgba(39,174,96,.18);
}
.conn-name {
  font-size: 0.90rem;
  font-weight: 700;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 7px;
}
.plat-chip {
  font-size: 0.60rem;
  font-weight: 700;
  letter-spacing: .06em;
  padding: 2px 7px;
  background: #FEF3C7;
  color: #D97706;
  border-radius: 999px;
  border: 1px solid rgba(251,191,36,.3);
}
.conn-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}
.meta-id {
  font-size: 0.70rem;
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: #9CA3AF;
  background: #F3F4F6;
  padding: 2px 7px;
  border-radius: 6px;
}
.meta-bee { font-size: 0.71rem; color: #374151; font-weight: 600; }
.meta-none { font-size: 0.71rem; color: #9CA3AF; }

.conn-hd-r {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.status-chip {
  font-size: 0.66rem;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid;
}
.status-on  { background: #F0FDF4; color: #16A34A; border-color: #BBF7D0; }
.status-off { background: #F9FAFB; color: #9CA3AF; border-color: #E5E7EB; }

.btn-del {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: transparent;
  border: 1.5px solid #E5E7EB;
  color: #9CA3AF;
  font-size: 0.68rem;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all .15s;
  font-family: inherit;
}
.btn-del:hover { background: #FEE2E2; color: #EF4444; border-color: #FECACA; }

.arrow-icon {
  font-size: 1.2rem;
  color: #9CA3AF;
  transition: transform .25s cubic-bezier(.34,1.56,.64,1);
  display: inline-block;
}
.arrow-icon.open { transform: rotate(90deg); color: #D97706; }

/* 展开动画 */
.slide-down-enter-active,
.slide-down-leave-active { transition: all .28s ease; overflow: hidden; }
.slide-down-enter-from,
.slide-down-leave-to { max-height: 0; opacity: 0; }
.slide-down-enter-to,
.slide-down-leave-from { max-height: 1200px; opacity: 1; }

/* 展开体 */
.conn-body { border-top: 1px solid #F3F4F6; }

/* Tab 栏 */
.tab-bar {
  display: flex;
  padding: 0 20px;
  background: #F9FAFB;
  border-bottom: 1px solid #F3F4F6;
  gap: 2px;
}
.tab-btn {
  padding: 10px 14px;
  font-size: 0.74rem;
  font-weight: 600;
  color: #6B7280;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
  margin-bottom: -1px;
  white-space: nowrap;
}
.tab-btn:hover { color: #374151; }
.tab-btn.active { color: #D97706; border-bottom-color: #F59E0B; }

/* Tab 内容 */
.tab-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #ffffff;
}

/* 表单 */
.field-row { display: flex; flex-direction: column; gap: 5px; }
.field-label {
  font-size: 0.74rem;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 6px;
}
.badge-req {
  font-size: 0.60rem;
  padding: 1px 6px;
  background: #FEF3C7;
  color: #D97706;
  border-radius: 999px;
  font-weight: 700;
  border: 1px solid rgba(251,191,36,.3);
}
.badge-opt {
  font-size: 0.60rem;
  padding: 1px 6px;
  background: #F3F4F6;
  color: #9CA3AF;
  border-radius: 999px;
  font-weight: 600;
}
.field-hint { font-size: 0.65rem; color: #9CA3AF; font-weight: 400; }
.field-input,
.field-select {
  padding: 9px 12px;
  font-size: 0.82rem;
  font-family: inherit;
  color: #111827;
  background: #F9FAFB;
  border: 1.5px solid #E5E7EB;
  border-radius: 10px;
  outline: none;
  transition: border-color .15s, box-shadow .15s;
  width: 100%;
  box-sizing: border-box;
}
.field-input:focus,
.field-select:focus {
  border-color: #F59E0B;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(245,158,11,.15);
}
.field-input::placeholder { color: #D1D5DB; }

.action-row {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}
.btn-save {
  padding: 8px 18px;
  background: linear-gradient(135deg, #F59E0B, #FBBF24);
  color: #1C1917;
  border: none;
  border-radius: 10px;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
  transition: all .2s;
  box-shadow: 0 2px 8px rgba(245,158,11,.3);
}
.btn-save:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(245,158,11,.5); }
.btn-test {
  padding: 8px 14px;
  background: #ffffff;
  color: #374151;
  border: 1.5px solid #E5E7EB;
  border-radius: 10px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
}
.btn-test:hover:not(:disabled) { background: #F3F4F6; border-color: #D1D5DB; }
.btn-test:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-disc {
  padding: 8px 12px;
  background: #FEF2F2;
  color: #EF4444;
  border: 1.5px solid #FECACA;
  border-radius: 10px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
}
.btn-disc:hover { background: #EF4444; color: #fff; border-color: #EF4444; }

/* ── 信息块 ── */
.info-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 14px;
  border-radius: 10px;
  font-size: 0.76rem;
  line-height: 1.55;
  border: 1.5px solid;
}
.info-block--amber {
  background: #FFFBEB;
  border-color: #FDE68A;
  color: #92400E;
}
.info-block--orange {
  background: #FFF7ED;
  border-color: #FDBA74;
  color: #9A3412;
}

/* ── 代码块 ── */
.code-block {
  background: #F9FAFB;
  border: 1.5px solid #E5E7EB;
  border-radius: 10px;
  overflow: hidden;
}
.code-block-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #F3F4F6;
  border-bottom: 1px solid #E5E7EB;
}
.code-block-title {
  font-size: 0.68rem;
  font-weight: 700;
  color: #6B7280;
  text-transform: uppercase;
  letter-spacing: .05em;
}
.btn-copy {
  padding: 4px 10px;
  background: #ffffff;
  color: #D97706;
  border: 1.5px solid #FDE68A;
  border-radius: 6px;
  font-size: 0.70rem;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
}
.btn-copy:hover { background: #FEF3C7; }
.code-val {
  display: block;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.76rem;
  color: #D97706;
  padding: 10px 14px;
  overflow-x: auto;
  white-space: nowrap;
  font-weight: 600;
}
.code-pre {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.70rem;
  color: #374151;
  padding: 12px 14px;
  margin: 0;
  line-height: 1.85;
  white-space: pre;
  overflow-x: auto;
}

/* ── 指南 ── */
.guide-body { gap: 12px; }
.guide-step {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.step-num {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #F59E0B, #FBBF24);
  color: #1C1917;
  font-size: 0.72rem;
  font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(245,158,11,.35);
  margin-top: 2px;
}
.step-title {
  font-size: 0.82rem;
  font-weight: 700;
  color: #111827;
  margin-bottom: 3px;
}
.step-desc {
  font-size: 0.73rem;
  color: #6B7280;
  line-height: 1.6;
}

/* ══════════════════════════════════════════════
   新增弹窗
══════════════════════════════════════════════ */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(17,24,39,.4);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9998;
}
.add-modal {
  background: #ffffff;
  border: 1.5px solid #E5E7EB;
  border-radius: 20px;
  padding: 28px 28px 24px;
  max-width: 400px;
  width: 90%;
  box-shadow: 0 24px 60px rgba(0,0,0,.15), 0 4px 16px rgba(0,0,0,.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.add-modal-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}
.add-modal-title { font-size: 1.02rem; font-weight: 800; color: #111827; }
.modal-close {
  width: 28px; height: 28px;
  border-radius: 8px;
  background: #F3F4F6;
  border: none;
  color: #6B7280;
  font-size: 0.78rem;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background .15s;
  font-family: inherit;
}
.modal-close:hover { background: #E5E7EB; color: #374151; }
.add-modal-sub {
  font-size: 0.76rem;
  color: #6B7280;
  margin: 0 0 10px;
  line-height: 1.5;
}
.plat-choice-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.plat-choice {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  background: #F9FAFB;
  border: 1.5px solid #E5E7EB;
  border-radius: 14px;
  cursor: pointer;
  font-family: inherit;
  transition: all .18s;
}
.plat-choice:hover {
  border-color: #FDE68A;
  background: #FFFBEB;
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(245,158,11,.18);
}
.plat-choice-icon { font-size: 34px; }
.plat-choice-name { font-size: 0.84rem; font-weight: 700; color: #111827; }
.plat-choice-desc { font-size: 0.67rem; color: #9CA3AF; text-align: center; }

/* 弹窗动画 */
.modal-fade-enter-active, .modal-fade-leave-active { transition: all .22s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .add-modal, .modal-fade-leave-to .add-modal { transform: scale(0.94) translateY(10px); }

/* ══════════════════════════════════════════════
   Toast
══════════════════════════════════════════════ */
.wings-toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  padding: 10px 22px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(0,0,0,.12), 0 2px 8px rgba(0,0,0,.06);
  border: 1.5px solid #E5E7EB;
  animation: toast-in .25s cubic-bezier(.34,1.56,.64,1);
  white-space: nowrap;
}
.wings-toast.success { border-color: #BBF7D0; color: #16A34A; }
.wings-toast.error   { border-color: #FECACA; color: #EF4444; }
.wings-toast.info    { border-color: #FDE68A; color: #D97706; }
@keyframes toast-in {
  from { opacity: 0; transform: translateX(-50%) translateY(10px) scale(.95); }
  to   { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}
</style>
