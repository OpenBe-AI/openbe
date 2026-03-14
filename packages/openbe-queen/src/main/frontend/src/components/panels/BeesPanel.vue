<template>
  <div class="panel" :class="{ active: store.activePanel === 'bees' }" id="panel-bees">

    <!-- ── Header ── -->
    <div class="bees-header">
      <div class="bees-header-left">
        <span class="bees-glyph">🐝</span>
        <div>
          <div class="bees-title">{{ t('bees_title') }}</div>
          <div class="bees-subtitle">{{ store.currentHive?.name || t('bees_subtitle_default') }} — {{ t('bees_subtitle_suffix') }}</div>
        </div>
      </div>
      <span class="bees-header-led"></span>
    </div>

    <!-- ── Body ── -->
    <div class="bees-body">

      <!-- Queen Hero -->
      <div class="queen-hero" :class="{ 'queen-hero--active': currentHiveBee }">
        <div class="queen-hero-crown">👑</div>
        <div class="queen-hero-info">
          <div class="queen-hero-row1">
            <span class="queen-hero-label">{{ t('queen_label') }}</span>
            <span v-if="store.currentHive" class="queen-hive-tag">{{ store.currentHive.name || store.currentHive.hiveId }}</span>
            <button class="queen-config-btn" @click="editQueenBee" :disabled="!store.currentHive">{{ t('config_queen_btn') }}</button>
          </div>
          <div v-if="currentHiveBee" class="queen-hero-name">{{ currentHiveBee.name }}</div>
          <div v-else class="queen-hero-empty">{{ t('queen_not_configured') }}</div>
          <div v-if="currentHiveBee" class="queen-hero-badges">
            <span class="qh-badge qh-badge--provider">{{ currentHiveBee.provider || 'ollama' }}</span>
            <span v-if="currentHiveBee.model" class="qh-badge qh-badge--model">{{ currentHiveBee.model }}</span>
            <span class="qh-badge qh-badge--online"><span class="qh-led"></span> {{ t('online') }}</span>
          </div>
        </div>
      </div>

      <!-- Two-column layout -->
      <div class="bees-cols">

        <!-- LEFT: 蜂蛹孵舱 -->
        <div class="bees-col">
          <div class="incubation-bay">

            <!-- Bay header -->
            <div class="bay-hd">
              <span class="bay-led" :style="{ background: speciesGlowColor(spawnSpecies), boxShadow: `0 0 8px ${speciesGlowColor(spawnSpecies)}` }"></span>
              <span class="bay-title">{{ t('incubation_bay') }}</span>
              <span class="bay-sub">蜂种基因序列</span>
            </div>

            <!-- Pupa centerpiece -->
            <div class="pupa-chamber">
              <div class="pupa-aura" :style="{ '--pupa-glow': speciesGlowColor(spawnSpecies) }"></div>
              <div class="pupa-egg" :class="{ hatching: isHatching }" :style="{ '--pupa-glow': speciesGlowColor(spawnSpecies) }">
                <span class="pupa-icon">{{ isHatching ? speciesEmoji(spawnSpecies) : '🥚' }}</span>
              </div>
              <div class="pupa-species-label" :style="{ color: speciesGlowColor(spawnSpecies) }">
                {{ speciesEmoji(spawnSpecies) }} {{ SPECIES_META[spawnSpecies]?.label || spawnSpecies }}
              </div>
            </div>

            <!-- Species grid -->
            <div class="species-grid-dark">
              <button
                v-for="(meta, key) in SPECIES_META"
                :key="key"
                class="sp-pill"
                :class="{ active: spawnSpecies === key }"
                :style="spawnSpecies === key ? { '--sp-glow': speciesGlowColor(key), borderColor: speciesGlowColor(key) } : {}"
                @click="spawnSpecies = key"
              >
                <span>{{ meta.emoji }}</span>
                <span class="sp-pill-name">{{ meta.label || key }}</span>
              </button>
            </div>

            <!-- Desc -->
            <div class="bay-desc">{{ speciesDesc(spawnSpecies) }}</div>

            <!-- Name input -->
            <input class="bay-input" v-model="spawnName" type="text" :placeholder="t('pupa_name_placeholder')">

            <!-- Gene sliders -->
            <div class="gene-panel">
              <div class="gene-row">
                <span class="gene-label">{{ t('gene_will_stability') }}</span>
                <input type="range" class="gene-slider gene-amber" v-model="willStability" min="0" max="100">
                <span class="gene-val">{{ willStability }}</span>
              </div>
              <div class="gene-row">
                <span class="gene-label">{{ t('gene_sync_freq') }}</span>
                <input type="range" class="gene-slider gene-pulse" v-model="syncFreq" min="0" max="100">
                <span class="gene-val">{{ syncFreq }}</span>
              </div>
            </div>

            <!-- Hive -->
            <select class="bay-select" v-model="spawnHiveId">
              <option value="">{{ t('no_hive_bind') }}</option>
              <option v-for="h in store.hives" :key="h.hiveId" :value="h.hiveId">{{ h.name || h.hiveId }}</option>
            </select>
            <div v-if="!spawnHiveId" class="bay-no-hive-hint">⚠ 请先绑定蜂巢才能孵化蜜蜂</div>

            <!-- Industrial hatch button -->
            <button class="hatch-btn" @click="doSpawnBee" :disabled="isHatching || !spawnHiveId" :class="{ hatching: isHatching, disabled: !spawnHiveId }" :style="{ '--hatch-glow': speciesGlowColor(spawnSpecies) }">
              <span class="hatch-switch-rail">
                <span class="hatch-switch-knob"></span>
              </span>
              <span class="hatch-text">{{ isHatching ? t('hatching') : t('hatch_start') }}</span>
            </button>

          </div>
        </div>

        <!-- RIGHT: 蜂蛹列表 + 巢穴记忆库 -->
        <div class="bees-col">
          <!-- 运行中蜂蛹 -->
          <div class="bc-card">
            <div class="bc-card-hd">
              <span class="bc-led bc-led--teal"></span>
              <span class="bc-card-title">{{ t('active_bees_title') }}</span>
              <span class="bc-card-sub">Active Bees</span>
              <span class="bc-count">{{ nonQueenBees.length }}</span>
            </div>
            <div v-if="nonQueenBees.length === 0" class="bees-empty">
              <span class="bees-empty-icon">🫙</span>
              <span>{{ t('no_active_bees') }}</span>
            </div>
            <div class="bees-grid">
              <div v-for="bee in nonQueenBees" :key="bee._key" class="bee-tile" :class="{ 'bee-tile--editing': store.activeEditingBeeKey === bee._key }" @click="editBee(bee)">
                <div class="bee-tile-hd">
                  <span class="bee-tile-icon">{{ beeIcon(bee) }}</span>
                  <div class="bee-tile-info">
                    <span class="bee-tile-name">{{ bee.beeName || speciesFromBee(bee) }}</span>
                    <span class="bee-tile-pid">PID #{{ bee.pid }}</span>
                  </div>
                  <span class="bee-status-dot" :class="bee.statusLower"></span>
                </div>
                <div class="bee-tile-mem">
                  <div class="mem-track">
                    <div class="mem-fill" :style="{ width: bee.memPct + '%' }" :class="memClass(bee.memPct)"></div>
                  </div>
                  <span class="mem-label">{{ bee.memPct }}%</span>
                </div>
                <div class="bee-tile-stats">
                  <span class="bee-stat">⚙ {{ bee.activeTasks || 0 }}</span>
                  <span class="bee-stat">🍯 {{ bee.totalHoney || 0 }}</span>
                  <button v-if="bee.statusLower !== 'offline'" class="bee-kill-btn" @click="terminateBee(bee)">{{ t('terminate') }}</button>
                </div>
              </div>
            </div>
          </div>

        </div>

      </div><!-- end bees-cols -->
    </div><!-- end bees-body -->

    <!-- Security Gate 确认弹窗 -->
    <Teleport to="body">
      <div v-if="securityGate.visible" class="sg-overlay" @click.self="denyGate">
        <div class="sg-modal">
          <div class="sg-icon">🔐</div>
          <div class="sg-title">{{ t('security_confirm') }}</div>
          <div class="sg-message">{{ securityGate.message }}</div>
          <div class="sg-actions">
            <button class="sg-btn deny"    @click="denyGate">{{ t('deny_btn') }}</button>
            <button class="sg-btn approve" @click="approveGate">{{ t('approve_btn') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { useSpecies } from '../../composables/useSpecies.js'
import { useActiveBees } from '../../composables/useActiveBees.js'

const store = useAppStore()
const { t } = useI18n()
const api = useApi()

const { SPECIES_META, speciesEmoji, speciesColor, speciesGlow, speciesLabel } = useSpecies()
const { activeBees } = useActiveBees()

function speciesGlowColor(s) { return speciesGlow(s) }
function speciesFromBee(bee) { return speciesLabel(bee) }
function speciesDesc(s)  { return SPECIES_META[s]?.desc  || '' }

const emit = defineEmits(['toast', 'edit-hive-bee', 'refresh-hives'])

// ── 蜂蛹管理 ────────────────────────────────────────────
const spawnSpecies  = ref('WORKER')
const spawnName     = ref('')
const spawnHiveId   = ref(store.activeHiveId || '')
const willStability = ref(75)
const syncFreq      = ref(50)
const isHatching    = ref(false)

// 切换蜂巢时自动同步孵化选择器
watch(() => store.activeHiveId, (id) => { spawnHiveId.value = id || '' })

const currentHiveBee = computed(() => {
  const hive = store.currentHive
  return hive && hive.bee && hive.bee.name ? hive.bee : null
})

// Enrich activeBees with display fields needed by this panel
const nonQueenBees = computed(() =>
  activeBees.value.map(b => {
    const memUsed  = parseInt(b.memoryMB || 0)
    const memTotal = parseInt(b.maxMemoryMB || 1)
    return {
      ...b,
      type: (b.beeType || b.type || 'UNKNOWN').toLowerCase(),
      pid: b.pid || '—',
      statusLower: (b.status || 'offline').toLowerCase(),
      memPct: memTotal > 0 ? Math.round((memUsed / memTotal) * 100) : 0,
    }
  })
)

function beeIcon(beeOrType) {
  const species = typeof beeOrType === 'string'
    ? beeOrType.toUpperCase()
    : speciesFromBee(beeOrType)
  return SPECIES_META[species]?.emoji || '🐝'
}

function memClass(pct) {
  if (pct > 80) return 'mem-danger'
  if (pct > 60) return 'mem-warn'
  return ''
}

function editQueenBee() {
  const hive = store.currentHive
  if (!hive) return
  emit('edit-hive-bee', { hiveId: hive.hiveId, bee: hive.bee || {} })
}

async function doSpawnBee() {
  if (isHatching.value) return
  isHatching.value = true
  const species = spawnSpecies.value
  const name    = spawnName.value.trim()
  const hiveId  = spawnHiveId.value
  try {
    const res = await api.spawnBee(species, name, hiveId)
    const label = name || `${speciesEmoji(species)} ${species}`
    const hiveTip = hiveId ? ` → 蜂巢 ${hiveId}` : ''
    emit('toast', { message: `${label} 已孵化 (pid:${res.pid})${hiveTip}`, type: 'success' })
    emit('refresh-hives')
  } catch (err) {
    emit('toast', { message: `孵化失败: ${err.message}`, type: 'error' })
  } finally {
    setTimeout(() => { isHatching.value = false }, 1400)
  }
}

function editBee(bee) {
  store.activeEditingBeeKey = bee._key
  store.setActivePanel('will')
}

async function terminateBee(bee) {
  try {
    await api.terminateBee(bee.type)
    emit('toast', { message: `${bee.type} terminated`, type: 'success' })
    emit('refresh-hives')
  } catch (err) {
    emit('toast', { message: `Terminate failed: ${err.message}`, type: 'error' })
  }
}

// ── Workspace ────────────────────────────────────────────
const WS_FILES = [
  'IDENTITY.md', 'SOUL.md', 'TOOLS.md', 'USER.md', 'MEMORY.md',
  'HEARTBEAT.md', 'BOOTSTRAP.md', 'SKILLS.md', 'SENSORS.md', 'GLOSSARY.md', 'AGENTS.md',
]

const FILE_ICONS = {
  'IDENTITY.md': '🪪', 'SOUL.md': '✨', 'TOOLS.md': '🔧', 'USER.md': '👤',
  'MEMORY.md': '🧠', 'HEARTBEAT.md': '💓', 'BOOTSTRAP.md': '🚀',
  'SKILLS.md': '⚡', 'SENSORS.md': '📡', 'GLOSSARY.md': '📖', 'AGENTS.md': '🤝',
}

function fileIcon(name) { return FILE_ICONS[name] || '📄' }

const wsSearch     = ref('')
const filteredWsFiles = computed(() =>
  wsSearch.value.trim()
    ? WS_FILES.filter(f => f.toLowerCase().includes(wsSearch.value.toLowerCase()))
    : WS_FILES
)

const fileStatus   = ref({})
const selectedFile = ref(null)
const fileContent  = ref('')
const fileSaving   = ref(false)
const wsLoading    = ref(false)
const textareaRef  = ref(null)
let   heartbeatTimer = null

async function loadWorkspace() {
  const hive = store.currentHive
  if (!hive) { fileStatus.value = {}; return }
  wsLoading.value = true
  try {
    const data = await api.listWorkspace(hive.hiveId)
    fileStatus.value = data.files || {}
  } catch {
    fileStatus.value = {}
  } finally {
    wsLoading.value = false
  }
}

async function selectFile(name) {
  clearInterval(heartbeatTimer)
  selectedFile.value = name
  fileContent.value = ''
  const hive = store.currentHive
  if (!hive) return
  fileContent.value = await api.readWorkspaceFile(hive.hiveId, name)
  if (name === 'HEARTBEAT.md') {
    heartbeatTimer = setInterval(async () => {
      if (store.currentHive)
        fileContent.value = await api.readWorkspaceFile(store.currentHive.hiveId, name)
    }, 3000)
  }
}

async function saveFile() {
  const hive = store.currentHive
  if (!hive || !selectedFile.value || selectedFile.value === 'HEARTBEAT.md') return
  fileSaving.value = true
  try {
    await api.writeWorkspaceFile(hive.hiveId, selectedFile.value, fileContent.value)
    fileStatus.value = { ...fileStatus.value, [selectedFile.value]: 'exists' }
    emit('toast', { message: `${selectedFile.value} 已保存`, type: 'success' })
  } catch (err) {
    emit('toast', { message: err.message, type: 'error' })
  } finally {
    fileSaving.value = false
  }
}

async function createDefault(name) {
  const hive = store.currentHive
  if (!hive) return
  try {
    const data = await api.listWorkspace(hive.hiveId)
    fileStatus.value = data.files || {}
    fileContent.value = await api.readWorkspaceFile(hive.hiveId, name)
    emit('toast', { message: `${name} 已创建`, type: 'success' })
  } catch (err) {
    emit('toast', { message: err.message, type: 'error' })
  }
}

function onKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
    e.preventDefault()
    saveFile()
  }
}

watch(() => store.activeHiveId, async () => {
  clearInterval(heartbeatTimer)
  selectedFile.value = null
  fileContent.value = ''
  await loadWorkspace()
}, { immediate: true })

onUnmounted(() => clearInterval(heartbeatTimer))

// ── Security Gate ─────────────────────────────────────────
const securityGate = ref({ visible: false, message: '', resolve: null })

function requestApproval(message) {
  return new Promise(resolve => {
    securityGate.value = { visible: true, message, resolve }
  })
}
function approveGate() {
  securityGate.value.resolve(true)
  securityGate.value.visible = false
}
function denyGate() {
  securityGate.value.resolve(false)
  securityGate.value.visible = false
}

// 执行 Stinger（自动走安全门）
async function executeStinger(stingerName, dangerous = false, dangerMsg = '') {
  if (dangerous) {
    const approved = await requestApproval(dangerMsg || `即将执行 "${stingerName}"，此操作会修改系统状态。是否授权？`)
    if (!approved) {
      emit('toast', { message: '操作已拒绝', type: 'info' })
      return null
    }
  }
  try {
    const result = await api.executeStinger(stingerName, store.activeHiveId || '')
    emit('toast', { message: `✓ ${stingerName} 执行成功`, type: 'success' })
    return result
  } catch (err) {
    emit('toast', { message: `✗ ${stingerName} 失败: ${err.message}`, type: 'error' })
    return null
  }
}

defineExpose({ executeStinger })
</script>

<style scoped>
/* Panel root — no display:flex here, that's handled by global .panel.active */
#panel-bees {
  background:
    radial-gradient(ellipse at 10% 15%, rgba(245,166,35,.09) 0%, transparent 45%),
    radial-gradient(ellipse at 90% 85%, rgba(255,180,0,.06) 0%, transparent 45%),
    linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
  color: #2C2724;
}

/* ── Header ──────────────────────────────────────────────── */
.bees-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid rgba(245,166,35,0.18);
  background: rgba(255,253,248,0.85);
  backdrop-filter: blur(12px);
  flex-shrink: 0;
}
.bees-header-left { display: flex; align-items: center; gap: 12px; }
.bees-glyph { font-size: 1.7rem; }
.bees-title { font-size: .92rem; font-weight: 800; letter-spacing: .05em; color: #2C2724; }
.bees-subtitle { font-size: .7rem; color: #B5A898; margin-top: 2px; }
.bees-header-led {
  width: 9px; height: 9px; border-radius: 50%;
  background: #F59E0B; box-shadow: 0 0 8px #F59E0B;
  animation: led-pulse 2s ease-in-out infinite;
}
@keyframes led-pulse {
  0%,100% { opacity:1; transform:scale(1); }
  50%      { opacity:.45; transform:scale(.75); }
}

/* ── Body ────────────────────────────────────────────────── */
.bees-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.bees-body::-webkit-scrollbar { width: 4px; }
.bees-body::-webkit-scrollbar-thumb { background: rgba(245,166,35,.25); border-radius: 4px; }

/* ── Queen Hero ──────────────────────────────────────────── */
.queen-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(255,255,255,0.85);
  border: 1.5px solid rgba(245,166,35,0.45);
  border-radius: 14px;
  padding: 12px 18px;
  position: relative;
  backdrop-filter: blur(14px);
  box-shadow: 0 2px 16px rgba(180,130,50,.12), inset 0 1px 0 rgba(255,255,255,.9);
  overflow: hidden;
  flex-shrink: 0;
}
.queen-hero::after {
  content: '👑';
  position: absolute;
  right: -4px; top: -12px;
  font-size: 5rem;
  opacity: .05;
  pointer-events: none;
  transform: rotate(12deg);
}

.queen-hero-crown {
  font-size: 1.6rem;
  filter: drop-shadow(0 0 6px rgba(245,166,35,.5));
  animation: crown-breathe 3s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes crown-breathe {
  0%,100% { transform: scale(1); filter: drop-shadow(0 0 6px rgba(245,166,35,.5)); }
  50%      { transform: scale(1.08); filter: drop-shadow(0 0 14px rgba(245,166,35,.8)); }
}
.queen-hero-info { flex: 1; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.queen-hero-row1 { display: flex; align-items: center; gap: 8px; }
.queen-hero-label { font-size: .62rem; font-weight: 700; color: #B5A898; letter-spacing: .08em; text-transform: uppercase; flex-shrink: 0; }
.queen-hero-name { font-size: .95rem; font-weight: 800; color: #2C2724; }
.queen-hero-empty { font-size: .78rem; color: #C5B5A0; font-style: italic; }
.queen-hero-badges { display: flex; gap: 5px; align-items: center; flex-wrap: wrap; }
.qh-badge {
  font-size: .60rem; font-weight: 700; padding: 2px 7px;
  border-radius: 99px; letter-spacing: .04em;
}
.qh-badge--provider { background: rgba(245,166,35,.15); color: #D97706; border: 1px solid rgba(245,166,35,.30); }
.qh-badge--model { background: rgba(100,90,80,.08); color: #7A6A5A; border: 1px solid rgba(100,90,80,.15); font-family: monospace; }
.qh-badge--online { background: rgba(22,163,74,.10); color: #16a34a; border: 1px solid rgba(22,163,74,.25); display: flex; align-items: center; gap: 5px; }
.qh-led { width: 6px; height: 6px; border-radius: 50%; background: #16a34a; box-shadow: 0 0 5px #16a34a; animation: led-pulse 1.5s ease-in-out infinite; }

.queen-hive-tag {
  font-size: .60rem; font-family: monospace; color: #B5A898;
  background: rgba(245,166,35,.08); border: 1px solid rgba(245,166,35,.20);
  border-radius: 99px; padding: 2px 8px; flex-shrink: 0;
}
.queen-config-btn {
  background: linear-gradient(135deg, #D97706 0%, #F59E0B 100%);
  color: #1a0c00; font-weight: 800; font-size: .72rem;
  border: none; border-radius: 99px; padding: 5px 14px;
  cursor: pointer; transition: all .15s;
  box-shadow: 0 2px 8px rgba(245,158,11,.30);
  flex-shrink: 0; margin-left: auto;
}
.queen-config-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 16px rgba(245,158,11,.50); }
.queen-config-btn:disabled { opacity: .4; cursor: not-allowed; }

/* ── Two-column layout ───────────────────────────────────── */
.bees-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: start;
}
.bees-col { display: flex; flex-direction: column; gap: 0; }

/* ── Base Card ───────────────────────────────────────────── */
.bc-card {
  background: rgba(255,255,255,0.72);
  border: 1px solid rgba(245,166,35,.20);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(180,130,50,.08);
}
.bc-card-hd { display: flex; align-items: center; gap: 8px; }
.bc-led {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
  animation: led-pulse 2s ease-in-out infinite;
}
.bc-led--amber { background: #F59E0B; box-shadow: 0 0 6px #F59E0B; }
.bc-led--teal  { background: #14B8A6; box-shadow: 0 0 6px #14B8A6; }
.bc-card-title { font-size: .82rem; font-weight: 800; color: #2C2724; letter-spacing: .03em; }
.bc-card-sub { font-size: .62rem; color: #B5A898; flex: 1; }
.bc-count {
  font-size: .65rem; font-weight: 800; min-width: 20px; text-align: center;
  background: rgba(245,166,35,.15); color: #D97706;
  border-radius: 99px; padding: 1px 7px;
}

/* ── 蜂蛹孵舱 ─────────────────────────────────────────────── */
.incubation-bay {
  background: linear-gradient(160deg, rgba(255,253,248,0.98) 0%, rgba(255,248,228,0.95) 100%);
  border: 1.5px solid rgba(245,166,35,.30);
  border-radius: 16px;
  padding: 14px 16px;
  margin-bottom: 20px;
  display: flex; flex-direction: column; gap: 8px;
  position: relative; overflow: hidden;
  box-shadow: 0 2px 16px rgba(245,166,35,.10), inset 0 1px 0 rgba(255,255,255,.9);
}
.incubation-bay::before { display: none; }

.bay-hd { display: flex; align-items: center; gap: 8px; }
.bay-led { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; animation: led-pulse 1.5s ease-in-out infinite; }
.bay-title { font-size: .82rem; font-weight: 800; color: #D97706; letter-spacing: .06em; }
.bay-sub { font-size: .56rem; color: #C5B5A0; letter-spacing: .14em; }

/* Pupa chamber */
.pupa-chamber {
  display: flex; flex-direction: column; align-items: center;
  gap: 6px; padding: 6px 0; position: relative;
}
.pupa-aura {
  position: absolute; width: 90px; height: 90px; border-radius: 50%;
  background: radial-gradient(circle, var(--pupa-glow, #F59E0B) 0%, transparent 70%);
  opacity: .18; top: 50%; left: 50%; transform: translate(-50%, -50%);
  animation: aura-pulse 2.5s ease-in-out infinite;
  pointer-events: none;
}
@keyframes aura-pulse {
  0%,100% { opacity: .18; transform: translate(-50%,-50%) scale(1); }
  50%      { opacity: .30; transform: translate(-50%,-50%) scale(1.15); }
}
.pupa-egg {
  width: 54px; height: 66px;
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  background: rgba(255,255,255,.90);
  border: 2px solid var(--pupa-glow, #F59E0B);
  box-shadow: 0 0 14px rgba(245,166,35,.25), inset 0 0 10px rgba(255,248,220,.60);
  display: flex; align-items: center; justify-content: center;
  font-size: 1.5rem;
  animation: pupa-breathe 3s ease-in-out infinite;
  transition: border-color .4s, box-shadow .4s;
  position: relative; z-index: 1;
}
.pupa-egg.hatching { animation: hatch-break 1.4s ease-out forwards; }
@keyframes pupa-breathe {
  0%,100% { transform: scale(1); }
  50%      { transform: scale(1.06); }
}
@keyframes hatch-break {
  0%   { transform: scale(1) rotate(0deg); opacity: 1; }
  20%  { transform: scale(1.18) rotate(-6deg); }
  45%  { transform: scale(0.85) rotate(8deg); opacity: .7; }
  70%  { transform: scale(1.12) rotate(-3deg); opacity: 1; }
  100% { transform: scale(1) rotate(0deg); }
}
.pupa-species-label {
  font-size: .7rem; font-weight: 700; letter-spacing: .06em;
  text-transform: uppercase; transition: color .3s;
  font-family: 'JetBrains Mono', monospace;
}

/* Species grid */
.species-grid-dark {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 5px;
}
.sp-pill {
  display: flex; flex-direction: column; align-items: center; gap: 1px;
  padding: 4px 3px;
  background: rgba(255,255,255,.75);
  border: 1px solid rgba(245,166,35,.22);
  border-radius: 7px; cursor: pointer;
  transition: all .15s;
}
.sp-pill:hover { background: rgba(245,166,35,.10); border-color: rgba(245,166,35,.40); }
.sp-pill.active {
  background: rgba(245,166,35,.14);
  border-color: rgba(245,166,35,.50);
  box-shadow: 0 0 8px rgba(245,166,35,.20);
}
.sp-pill-name { font-size: .45rem; font-weight: 700; color: #C5B5A0; letter-spacing: .04em; text-transform: uppercase; }
.sp-pill.active .sp-pill-name { color: #D97706; }

/* Desc */
.bay-desc {
  font-size: .64rem; color: #7A6A5A; line-height: 1.45;
  background: rgba(255,255,255,.70); border-radius: 6px; padding: 5px 8px;
  border: 1px solid rgba(245,166,35,.15); min-height: 28px;
}

/* Bay inputs */
.bay-input {
  background: rgba(255,255,255,.90); border: 1px solid rgba(245,166,35,.28);
  border-radius: 7px; padding: 6px 10px;
  color: #2C2724; font-size: .73rem; outline: none;
  transition: border-color .15s;
}
.bay-input:focus { border-color: rgba(245,166,35,.60); }
.bay-input::placeholder { color: #C5B5A0; }

.bay-select {
  background: rgba(255,255,255,.90); border: 1px solid rgba(245,166,35,.28);
  border-radius: 7px; padding: 6px 10px;
  color: #2C2724; font-size: .73rem; outline: none;
  cursor: pointer;
}
.bay-select option { background: #fff; color: #2C2724; }

/* Gene sliders */
.gene-panel { display: flex; flex-direction: column; gap: 5px; }
.gene-row { display: flex; align-items: center; gap: 8px; }
.gene-label { font-size: .62rem; color: #7A6A5A; width: 64px; flex-shrink: 0; }
.gene-val { font-size: .62rem; font-family: 'JetBrains Mono', monospace; color: #D97706; width: 24px; text-align: right; flex-shrink: 0; }

.gene-slider {
  flex: 1; -webkit-appearance: none; appearance: none;
  height: 4px; border-radius: 2px; outline: none; cursor: pointer;
}
.gene-amber {
  background: linear-gradient(90deg, #F59E0B calc(var(--val, 75%) * 1%), rgba(245,166,35,.15) 0);
}
.gene-amber::-webkit-slider-thumb {
  -webkit-appearance: none; width: 13px; height: 13px; border-radius: 50%;
  background: #F59E0B; box-shadow: 0 0 8px #F59E0B; cursor: pointer;
}
.gene-pulse {
  background: linear-gradient(90deg, #14B8A6 calc(var(--val, 50%) * 1%), rgba(20,184,166,.15) 0);
}
.gene-pulse::-webkit-slider-thumb {
  -webkit-appearance: none; width: 13px; height: 13px; border-radius: 50%;
  background: #14B8A6; box-shadow: 0 0 8px #14B8A6; cursor: pointer;
}

/* Hatch button */
.hatch-btn {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  background: linear-gradient(135deg, rgba(245,166,35,.12) 0%, rgba(245,166,35,.06) 100%);
  border: 1.5px solid var(--hatch-glow, #F59E0B);
  border-radius: 9px; padding: 9px 0; width: 100%;
  cursor: pointer; color: #D97706;
  font-weight: 800; font-size: .82rem; letter-spacing: .05em;
  box-shadow: 0 2px 12px rgba(245,166,35,.15), inset 0 1px 0 rgba(255,255,255,.8);
  transition: all .2s;
  position: relative; overflow: hidden;
}
.hatch-btn::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(245,166,35,.15) 0%, transparent 60%);
  opacity: 0; transition: opacity .2s;
}
.hatch-btn:hover::before { opacity: 1; }
.hatch-btn:hover { box-shadow: 0 4px 20px rgba(245,166,35,.28); transform: translateY(-1px); }
.hatch-btn.hatching { opacity: .7; cursor: not-allowed; animation: hatch-pulse .6s ease-in-out infinite; }
.hatch-btn.disabled { opacity: .45; cursor: not-allowed; }
.hatch-btn:disabled { cursor: not-allowed; }
.bay-no-hive-hint {
  font-size: .68rem; color: #B45309;
  background: rgba(245,158,11,.10); border: 1px solid rgba(245,158,11,.25);
  border-radius: 6px; padding: 4px 10px; text-align: center;
}
@keyframes hatch-pulse {
  0%,100% { box-shadow: 0 2px 12px rgba(245,166,35,.15); }
  50%      { box-shadow: 0 4px 24px rgba(245,166,35,.40); }
}
.hatch-switch-rail {
  width: 28px; height: 14px; border-radius: 7px;
  background: rgba(245,166,35,.18); border: 1px solid rgba(245,166,35,.30);
  position: relative; flex-shrink: 0;
  transition: background .2s;
}
.hatch-switch-knob {
  position: absolute; top: 1px; left: 1px;
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--hatch-glow, #F59E0B);
  box-shadow: 0 0 6px var(--hatch-glow, #F59E0B);
  transition: transform .2s;
}
.hatch-btn:not(:disabled):hover .hatch-switch-knob { transform: translateX(14px); }
.hatch-btn.hatching .hatch-switch-knob { transform: translateX(14px); }
.hatch-text { position: relative; z-index: 1; }

/* ── Bee Tiles ───────────────────────────────────────────── */
.bees-empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 24px 0;
  color: #B5A898; font-size: .78rem;
}
.bees-empty-icon { font-size: 1.8rem; opacity: .35; }

.bees-grid { display: flex; flex-direction: column; gap: 8px; }

.bee-tile {
  background: rgba(255,253,248,0.65);
  border: 1px solid rgba(245,166,35,.18);
  border-left: 3px solid rgba(245,166,35,.50);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
  transition: border-color .15s, box-shadow .15s;
}
.bee-tile { cursor: pointer; }
.bee-tile:hover {
  border-left-color: #F59E0B;
  box-shadow: 0 3px 16px rgba(245,166,35,.12);
}
.bee-tile--editing {
  border-left-color: #F59E0B;
  box-shadow: 0 0 0 2px rgba(245,166,35,.35), 0 3px 16px rgba(245,166,35,.18);
  background: rgba(255,249,235,.90);
}

.bee-tile-hd { display: flex; align-items: center; gap: 8px; }
.bee-tile-icon { font-size: 1rem; }
.bee-tile-info { flex: 1; display: flex; flex-direction: column; gap: 1px; }
.bee-tile-name { font-size: .8rem; font-weight: 700; color: #2C2724; }
.bee-tile-pid { font-size: .6rem; color: #B5A898; font-family: monospace; }

.bee-status-dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
  animation: led-pulse 2s ease-in-out infinite;
}
.bee-status-dot.online { background: #16a34a; box-shadow: 0 0 6px #16a34a; }
.bee-status-dot.busy   { background: #F59E0B; box-shadow: 0 0 6px #F59E0B; }
.bee-status-dot.offline { background: #9ca3af; box-shadow: none; animation: none; }

.bee-tile-mem { display: flex; align-items: center; gap: 8px; }
.mem-track { flex: 1; height: 5px; background: rgba(245,166,35,.12); border-radius: 3px; overflow: hidden; }
.mem-fill { height: 100%; background: linear-gradient(90deg, #F59E0B, #FCD34D); border-radius: 3px; transition: width .4s ease; }
.mem-fill.mem-warn   { background: linear-gradient(90deg, #D97706, #F59E0B); }
.mem-fill.mem-danger { background: linear-gradient(90deg, #DC2626, #EF4444); }
.mem-label { font-size: .6rem; font-family: monospace; color: #B5A898; width: 30px; text-align: right; flex-shrink: 0; }

.bee-tile-stats { display: flex; align-items: center; gap: 6px; }
.bee-stat {
  font-size: .65rem; color: #7A6A5A;
  background: rgba(245,166,35,.08); border: 1px solid rgba(245,166,35,.15);
  border-radius: 6px; padding: 2px 7px;
}
.bee-kill-btn {
  margin-left: auto; font-size: .65rem; font-weight: 700;
  background: rgba(220,38,38,.08); border: 1px solid rgba(220,38,38,.25);
  color: #DC2626; border-radius: 6px; padding: 2px 10px; cursor: pointer;
  transition: all .15s;
}
.bee-kill-btn:hover { background: rgba(220,38,38,.18); }

/* ── Workspace — 右列内，蜂蛹列表下方 ──────────────────── */
.bees-workspace-wrap {
  margin-top: 12px;
  background: rgba(255,255,255,0.72);
  border: 1px solid rgba(245,166,35,.20);
  border-radius: 14px;
  backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(180,130,50,.08);
  overflow: hidden;
  display: flex; flex-direction: column;
}

.ws-hd {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 18px;
  background: rgba(255,249,235,.80);
  border-bottom: 1px solid rgba(245,166,35,.15);
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}
.ws-hd-icon { font-size: .85rem; }
.ws-hd-title { font-size: .78rem; font-weight: 800; color: #2C2724; }
.ws-hd-hive { font-size: .65rem; font-family: monospace; color: #B5A898; flex: 1; }
.ws-spin-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #F59E0B; animation: led-pulse 1s ease-in-out infinite;
}

.ws-body { display: flex; height: 280px; }

.ws-search-wrap { padding: 6px 8px; border-bottom: 1px solid rgba(245,166,35,.12); flex-shrink: 0; }
.ws-search {
  width: 100%; box-sizing: border-box;
  background: rgba(245,166,35,.07); border: 1px solid transparent;
  border-radius: 6px; padding: 4px 8px;
  font-size: .68rem; color: #2C2724; outline: none;
  transition: border-color .15s;
}
.ws-search:focus { border-color: rgba(245,166,35,.40); }
.ws-search::placeholder { color: #C5B5A0; }

.ws-sidebar {
  width: 200px; flex-shrink: 0;
  border-right: 1px solid rgba(245,166,35,.15);
  overflow-y: auto; padding: 6px 0;
  background: rgba(255,249,235,.50);
}
.ws-sidebar::-webkit-scrollbar { width: 2px; }
.ws-sidebar::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); }

.ws-file {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; cursor: pointer; user-select: none;
  border-left: 2px solid transparent;
  transition: background .1s;
  overflow: hidden;
}
.ws-file:hover { background: rgba(245,166,35,.08); }
.ws-file.active { background: rgba(245,166,35,.14); border-left-color: #D97706; }
.ws-file-icon { font-size: .7rem; flex-shrink: 0; }
.ws-file-name {
  flex: 1; font-size: .72rem; font-family: monospace; color: #7A6A5A;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  min-width: 0;
}
.ws-file.active .ws-file-name { color: #D97706; font-weight: 700; }
.ws-file-dot { flex-shrink: 0; }
.ws-file-dot.exists svg { fill: #16a34a; }
.ws-file-dot.missing svg { fill: rgba(245,166,35,.25); }

.ws-editor { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.ws-editor-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 14px;
  background: rgba(255,249,235,.80);
  border-bottom: 1px solid rgba(245,166,35,.12);
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}
.ws-editor-name {
  flex: 1; font-size: .72rem; font-family: monospace; color: #7A6A5A;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ws-live { display: flex; align-items: center; gap: 5px; font-size: .6rem; font-weight: 700; color: #16a34a; letter-spacing: .08em; }
.ws-live-dot { width: 6px; height: 6px; border-radius: 50%; background: #16a34a; animation: led-pulse 1.2s ease-in-out infinite; }
.ws-missing-tag {
  font-size: .6rem; font-weight: 600; padding: 1px 7px;
  background: rgba(245,166,35,.10); color: #B5A898;
  border: 1px solid rgba(245,166,35,.20); border-radius: 99px;
}
.ws-btn-ghost {
  font-size: .68rem; font-weight: 600; padding: 3px 10px;
  border: 1px solid rgba(245,166,35,.25); border-radius: 6px;
  background: transparent; color: #7A6A5A; cursor: pointer;
  transition: background .1s;
}
.ws-btn-ghost:hover { background: rgba(245,166,35,.10); }
.ws-btn-save {
  font-size: .68rem; font-weight: 700; padding: 3px 12px;
  border: none; border-radius: 6px;
  background: #D97706; color: #1a0c00; cursor: pointer;
  display: flex; align-items: center; gap: 5px;
  transition: background .1s;
}
.ws-btn-save:hover:not(:disabled) { background: #B45309; }
.ws-btn-save:disabled { opacity: .5; cursor: not-allowed; }
kbd { font-size: .6rem; font-family: monospace; opacity: .7; background: rgba(255,255,255,.25); border-radius: 3px; padding: 0 4px; }

.ws-textarea {
  flex: 1; resize: none; border: none; border-radius: 0; outline: none; box-shadow: none;
  font-family: monospace; font-size: .78rem; line-height: 1.7;
  padding: 16px 18px;
  background: rgba(255,255,255,0.65);
  color: #2C2724;
}
.ws-placeholder {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; background: rgba(255,249,235,.50);
}
.ws-ph-icon { font-size: 2rem; opacity: .25; }
.ws-ph-text { font-size: .78rem; color: #B5A898; }

/* ── Security Gate ───────────────────────────────────────── */
.sg-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.45);
  backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999;
}
.sg-modal {
  background: rgba(255,252,244,.97);
  border: 1px solid rgba(245,166,35,.30);
  border-radius: 18px;
  box-shadow: 0 24px 48px rgba(0,0,0,.25), 0 0 40px rgba(245,166,35,.10);
  padding: 32px 28px 24px;
  width: 360px; max-width: 90vw;
  display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center;
}
.sg-icon { font-size: 2.4rem; }
.sg-title { font-size: 1rem; font-weight: 800; color: #2C2724; }
.sg-message { font-size: .82rem; color: #7A6A5A; line-height: 1.6; }
.sg-actions { display: flex; gap: 10px; margin-top: 8px; width: 100%; }
.sg-btn { flex: 1; padding: 10px 0; border-radius: 10px; border: none; font-size: .8rem; font-weight: 700; cursor: pointer; transition: opacity .15s; }
.sg-btn:hover { opacity: .85; }
.sg-btn.deny    { background: rgba(245,166,35,.10); color: #7A6A5A; border: 1px solid rgba(245,166,35,.25); }
.sg-btn.approve { background: linear-gradient(135deg, #DC2626, #EF4444); color: #fff; }
</style>
