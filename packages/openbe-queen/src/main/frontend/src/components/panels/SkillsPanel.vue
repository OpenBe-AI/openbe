<template>
  <div class="panel" :class="{ active: store.activePanel === 'skills' }" id="panel-skills">

    <!-- Header -->
    <div class="sp-header">
      <span class="sp-title">⚡ {{ t('skills_title') }}</span>
      <div class="sp-search-wrap">
        <span class="sp-search-icon">🔍</span>
        <input v-model="searchQuery" class="sp-search" placeholder="搜索蜂刺…技能名 / 工具 / 描述" />
        <button v-if="searchQuery" class="sp-search-clear" @click="searchQuery = ''">✕</button>
      </div>
      <div class="sp-header-actions">
        <button class="sp-btn sp-btn-forge" @click="showForge = true">{{ t('forge_btn') }}</button>
        <label class="sp-btn">
          <input type="file" accept=".js,.ts,.sh,.scpt" @change="handleImport" style="display:none">
          {{ t('import_btn') }}
        </label>
        <button class="sp-btn" @click="exportConfig">{{ t('export_btn') }}</button>
      </div>
    </div>

    <!-- Species Filter Tabs -->
    <div class="sp-species-tabs" ref="tabsRef">
      <button
        v-for="tab in speciesTabs"
        :key="tab.key"
        class="sp-tab"
        :class="{ active: filterSpecies === tab.key }"
        :style="filterSpecies === tab.key ? { '--tab-color': tab.glow } : {}"
        @click="filterSpecies = tab.key"
      >
        <span class="tab-emoji">{{ tab.emoji }}</span>
        <span class="tab-name">{{ tab.label }}</span>
        <span class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <!-- Body: Left Armory + Right Library -->
    <div class="sp-body">

      <!-- LEFT: Armory -->
      <div class="sp-armory">
        <!-- Bee selector -->
        <div class="armory-header">
          <select class="bee-select" v-model="activeBeeKey" @change="onBeeChange">
            <option value="queen">👑 {{ currentHiveName }} · 蜂王</option>
            <option v-if="!runtimeBees.length" disabled>（暂无运行中蜂蛹）</option>
            <option v-for="b in runtimeBees" :key="b._key" :value="b._key">
              {{ getEmoji(b) }} {{ b.beeName || b.beeType }}
            </option>
          </select>
        </div>

        <!-- BeeLogo + aura -->
        <div class="armory-logo" ref="centerRef">
          <div class="aura-wrap" :style="{ '--ac': activeGlow }">
            <div class="aura-ring ar-outer"></div>
            <div class="aura-ring ar-mid"></div>
            <div class="aura-ring ar-inner"></div>
            <BeeLogo :species="activeSpeciesKey.toLowerCase()" :seed="activeBeeKey" :size="160" class="bee-logo" />
          </div>
          <!-- Stats row below logo -->
          <div class="armory-stats">
            <div class="astat">
              <span class="astat-n" :style="{ color: activeGlow }">{{ activeEquippedCount }}</span>
              <span class="astat-l">已装载</span>
            </div>
            <div class="astat">
              <span class="astat-n">{{ activeSlotTotal }}</span>
              <span class="astat-l">插槽数</span>
            </div>
            <div class="astat">
              <span class="astat-n">{{ rankStars }}</span>
              <span class="astat-l">战力</span>
            </div>
          </div>
        </div>

        <!-- Slot groups -->
        <div class="armory-slots">
          <div v-for="(group, gi) in activeSlotGroups" :key="gi" class="slot-group">
            <div class="slot-group-label">{{ group.group }}</div>
            <div class="slot-group-row">
              <div
                v-for="(slot, si) in group.displaySlots"
                :key="si"
                class="gem-slot"
                :class="[
                  slot.filled ? `slot-filled rarity-${slot.rarity}` : 'slot-empty',
                  selectedSlotIdx === slot.globalIdx ? 'slot-selected' : ''
                ]"
                :title="slot.filled ? slot.name : slot.label"
                @click="onSlotClick(slot)"
                @mouseenter="tooltipStinger = slot.filled ? slot : null"
                @mouseleave="tooltipStinger = null"
              >
                <div class="slot-label">{{ slot.label }}</div>
                <div v-if="slot.filled" class="gem-icon">{{ slot.icon }}</div>
                <div v-else class="gem-plus">＋</div>
                <div v-if="slot.filled" class="gem-glow" :class="`glow-${slot.rarity}`"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="armory-footer">
          <button class="save-btn" @click="save">
            <span>💎</span> {{ t('save_gear') }}
          </button>
        </div>
      </div>

      <!-- RIGHT: Stinger Library -->
      <div class="sp-library">
        <!-- Library header -->
        <div class="lib-header">
          <div class="lib-title">
            蜂刺神兵库
            <span class="lib-count">{{ filteredStingers.length }} 枚</span>
          </div>
          <div class="lib-hint" v-if="selectedSlotIdx >= 0">
            ← 点击蜂刺装入第 {{ selectedSlotIdx + 1 }} 槽位
            <button class="lib-cancel-slot" @click="selectedSlotIdx = -1">取消</button>
          </div>
        </div>

        <!-- Card grid -->
        <div class="lib-grid" v-if="filteredStingers.length">
          <div
            v-for="stinger in filteredStingers"
            :key="stinger.id"
            class="stinger-card"
            :class="{
              'card-equipped': isEquipped(stinger.id),
              'card-dangerous': stinger.dangerous,
              'card-gold': stinger.rarity === 'gold',
              'card-purple': stinger.rarity === 'purple',
            }"
            @click="onCardClick(stinger)"
          >
            <!-- Card top -->
            <div class="card-top">
              <span class="card-icon">{{ stinger.icon }}</span>
              <span
                class="card-species-badge"
                :style="{
                  background: speciesBadgeBg(stinger.species),
                  color: speciesBadgeColor(stinger.species)
                }"
              >
                {{ speciesEmojiFor(stinger.species) }}{{ speciesShortName(stinger.species) }}
              </span>
            </div>

            <!-- Card name -->
            <div class="card-name" :class="`card-name-${stinger.rarity}`">{{ stinger.name }}</div>
            <div class="card-tool">{{ stinger.tool }}</div>

            <!-- Card desc -->
            <div class="card-desc">{{ stinger.desc }}</div>

            <!-- Card bottom -->
            <div class="card-bottom">
              <span class="card-rarity" :class="`rarity-chip-${stinger.rarity}`">
                ◆ {{ rarityLabel(stinger.rarity) }}
              </span>
              <span v-if="stinger.dangerous" class="card-danger-flag">⚠ 高危</span>
              <button
                v-if="!isEquipped(stinger.id)"
                class="card-equip-btn"
                @click.stop="onCardClick(stinger)"
              >装载 ↑</button>
              <span v-else class="card-equipped-tag">已装载 ✓</span>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else class="lib-empty">
          <div class="lib-empty-icon">⚔️</div>
          <div class="lib-empty-title">{{ searchQuery ? '未找到匹配蜂刺' : '该蜂种暂无蜂刺' }}</div>
          <div class="lib-empty-sub">{{ searchQuery ? '尝试其他关键词' : '切换蜂种或在锻造台创建新蜂刺' }}</div>
        </div>
      </div>

    </div>

    <!-- Oath Popup -->
    <Teleport to="body">
      <Transition name="oath-pop">
        <div v-if="oathVisible" class="oath-overlay" @click="oathVisible = false">
          <div class="oath-card" @click.stop>
            <div class="oath-bee-row">
              <span class="oath-bee-emoji">{{ activeSpeciesEmoji }}</span>
              <div>
                <div class="oath-bee-name">{{ activeSpeciesName }}</div>
                <div class="oath-bee-type">{{ activeSpeciesKey }}</div>
              </div>
            </div>
            <div class="oath-divider">
              <span>✦ ✦ ✦</span>
            </div>
            <div class="oath-skill-name">「{{ oathStinger?.name }}」</div>
            <div class="oath-skill-sub">已植入基因组 ✨</div>
            <div class="oath-text">
              主人，蜂刺融合完毕！<br>
              以【{{ oathStinger?.name }}】之力，誓为蜂巢效力！🫡
            </div>
            <div class="oath-tool">{{ oathStinger?.tool }}</div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Spark particles -->
    <Teleport to="body">
      <div class="sp-sparks-layer">
        <div
          v-for="s in activeSparks"
          :key="s.id"
          class="data-spark"
          :style="s.style"
        >{{ s.glyph }}</div>
      </div>
    </Teleport>

    <!-- Tooltip -->
    <Teleport to="body">
      <div
        v-if="tooltipStinger"
        class="sp-tooltip"
        :style="{ left: mouseX + 'px', top: mouseY + 'px' }"
      >
        <div class="tt-row">
          <span>{{ tooltipStinger.icon }}</span>
          <span class="tt-name" :class="`tt-${tooltipStinger.rarity}`">{{ tooltipStinger.name }}</span>
          <span v-if="tooltipStinger.dangerous" class="tt-danger">{{ t('high_perm') }}</span>
        </div>
        <div class="tt-desc">{{ tooltipStinger.desc }}</div>
        <div class="tt-hint">{{ t('unload_hint') }}</div>
      </div>
    </Teleport>

    <!-- StingerForgeModal -->
    <StingerForgeModal :visible="showForge" @close="showForge = false" @forged="load" />

    <!-- Security Gate -->
    <Teleport to="body">
      <div v-if="gateShow" class="gate-overlay" @click.self="gateDeny">
        <div class="gate-modal">
          <div class="gate-stripe">{{ t('security_gate_title') }}</div>
          <div class="gate-body">{{ gateMsg }}</div>
          <div class="gate-actions">
            <button class="sp-btn" @click="gateDeny">{{ t('cancel_btn') }}</button>
            <button class="gate-confirm" @click="gateApprove">{{ t('force_import') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useAppStore } from '../../stores/app.js'
import { useI18n } from '../../composables/useI18n.js'
import { useApi } from '../../composables/useApi.js'
import { SPECIES_META, speciesEmoji, speciesGlow, speciesLabel } from '../../composables/useSpecies.js'
import {
  STINGER_REGISTRY, SPECIES_STINGER_GROUPS,
  getBySpecies, searchStingers, getById,
} from '../../composables/useStingerRegistry.js'
import BeeLogo from '../BeeLogo.vue'
import StingerForgeModal from '../StingerForgeModal.vue'

const store = useAppStore()
const { t } = useI18n()
const api   = useApi()
const emit  = defineEmits(['toast'])

// ── Species display helpers ──────────────────────────────────────────────────
const SPECIES_ZH_SHORT = {
  QUEEN: '蜂王', WORKER: '工蜂', SOLDIER: '兵蜂', NURSE: '育蜜',
  SCOUT: '侦察', MECHANIC: '机械', MEDIC: '医护', SCRIBE: '文书',
  PAINTER: '画师', EDITOR: '剪辑', INFLUENCER: '博主', SENTINEL: '哨兵', UTILITY: '通用',
}

function speciesEmojiFor(key) {
  return SPECIES_META[key]?.emoji || (key === 'QUEEN' ? '👑' : '🐝')
}
function speciesShortName(key) {
  return SPECIES_ZH_SHORT[key] || key
}
function speciesBadgeBg(key) {
  return SPECIES_META[key]?.color || 'rgba(255,195,0,0.12)'
}
function speciesBadgeColor(key) {
  return SPECIES_META[key]?.glow || '#F59E0B'
}

// ── State ────────────────────────────────────────────────────────────────────
const activeBeeKey    = ref('queen')
const filterSpecies   = ref('ALL')
const searchQuery     = ref('')
const slotMap         = ref({})         // beeKey → (number|string|null)[]
const selectedSlotIdx = ref(-1)         // which slot awaits a stinger (-1 = none)
const apiStingers     = ref([])         // user-created stingers from backend
const oathVisible     = ref(false)
const oathStinger     = ref(null)
const tooltipStinger  = ref(null)
const mouseX          = ref(0)
const mouseY          = ref(0)
const showForge       = ref(false)
const gateShow        = ref(false)
const gateMsg         = ref('')
const tabsRef         = ref(null)
const centerRef       = ref(null)
const activeSparks    = ref([])
let   gateResolve     = null
let   sparkIdCounter  = 0
let   oathTimer       = null

// ── Mouse tracking ───────────────────────────────────────────────────────────
function trackMouse(e) {
  mouseX.value = e.clientX + 16
  mouseY.value = e.clientY + 12
}
onMounted(() => window.addEventListener('mousemove', trackMouse))
onUnmounted(() => window.removeEventListener('mousemove', trackMouse))

// ── Runtime bees from store ──────────────────────────────────────────────────
const runtimeBees = computed(() =>
  Object.entries(store.bees || {})
    .filter(([, b]) => {
      const type = (b.beeType || b.type || '').toLowerCase()
      return type !== 'queen' && (b.status || '').toLowerCase() !== 'offline'
    })
    .map(([key, b]) => ({ ...b, _key: key }))
)

const currentHiveName = computed(() =>
  store.currentHive?.name || store.currentHive?.hiveId || '当前蜂巢'
)

function getEmoji(bee) {
  const key = speciesLabel(bee)
  return SPECIES_META[key]?.emoji || '🐝'
}

// ── Active species info ──────────────────────────────────────────────────────
const activeSpeciesKey = computed(() => {
  if (activeBeeKey.value === 'queen') return 'QUEEN'
  const bee = runtimeBees.value.find(b => b._key === activeBeeKey.value)
  return bee ? speciesLabel(bee) : 'WORKER'
})

const activeSpeciesEmoji = computed(() => {
  if (activeBeeKey.value === 'queen') return '👑'
  const bee = runtimeBees.value.find(b => b._key === activeBeeKey.value)
  return bee ? getEmoji(bee) : '🐝'
})

const activeSpeciesName = computed(() => {
  if (activeBeeKey.value === 'queen') return `${currentHiveName.value} · 蜂王`
  const bee = runtimeBees.value.find(b => b._key === activeBeeKey.value)
  return bee
    ? (bee.beeName || SPECIES_ZH_SHORT[activeSpeciesKey.value] || bee.beeType)
    : '工作蜂'
})

const activeGlow = computed(() => {
  return SPECIES_META[activeSpeciesKey.value]?.glow || '#F59E0B'
})

// ── Slot management ──────────────────────────────────────────────────────────
const activeSlotGroups = computed(() => {
  const groups = SPECIES_STINGER_GROUPS[activeSpeciesKey.value] || SPECIES_STINGER_GROUPS.WORKER
  const assignments = slotMap.value[activeBeeKey.value] || []
  let globalIdx = 0
  return groups.map(g => ({
    group: g.group,
    displaySlots: g.ids.map(stingerId => {
      const idx = globalIdx++
      const equippedId = assignments[idx]
      if (!equippedId) {
        const s = getById(stingerId)
        return { filled: false, label: s?.name || '空槽', globalIdx: idx }
      }
      const s = getById(Number(equippedId)) || apiStingers.value.find(st => st.id === equippedId)
      return s
        ? { ...s, filled: true, label: s.name, globalIdx: idx }
        : { filled: false, label: '空槽', globalIdx: idx }
    }),
  }))
})

const activeSlotTotal = computed(() => {
  const groups = SPECIES_STINGER_GROUPS[activeSpeciesKey.value] || []
  return groups.reduce((acc, g) => acc + g.ids.length, 0)
})

const activeEquippedCount = computed(() =>
  (slotMap.value[activeBeeKey.value] || []).filter(Boolean).length
)

const rankStars = computed(() => {
  const n = activeEquippedCount.value
  if (n === 0) return '☆☆☆'
  if (n <= 2)  return '★☆☆'
  if (n <= 5)  return '★★☆'
  return '★★★'
})

function setSlot(idx, id) {
  const key = activeBeeKey.value
  const len = activeSlotTotal.value
  const arr = Array.from({ length: len }, (_, i) => (slotMap.value[key] || [])[i] ?? null)
  arr[idx] = id
  slotMap.value = { ...slotMap.value, [key]: arr }
}

function isEquipped(id) {
  const slots = slotMap.value[activeBeeKey.value] || []
  return slots.includes(id) || slots.includes(String(id))
}

// ── Species filter tabs ──────────────────────────────────────────────────────
const SPECIES_TAB_ORDER = [
  'ALL', 'QUEEN', 'SCOUT', 'WORKER', 'NURSE', 'SCRIBE',
  'SOLDIER', 'MEDIC', 'PAINTER', 'EDITOR', 'INFLUENCER', 'SENTINEL', 'UTILITY',
]

const speciesTabs = computed(() =>
  SPECIES_TAB_ORDER.map(key => {
    if (key === 'ALL') {
      return {
        key, label: '全部', emoji: '⚡',
        count: STINGER_REGISTRY.length + apiStingers.value.length,
        glow: '#F59E0B',
      }
    }
    const stingers = STINGER_REGISTRY.filter(s => s.species === key)
    return {
      key,
      label: SPECIES_ZH_SHORT[key] || key,
      emoji: speciesEmojiFor(key),
      count: stingers.length,
      glow: SPECIES_META[key]?.glow || '#F59E0B',
    }
  })
)

// ── Merged stinger list ──────────────────────────────────────────────────────
const allStingers = computed(() => [
  ...STINGER_REGISTRY,
  ...apiStingers.value.map(s => ({
    id:        s.id || s.name,
    species:   'UTILITY',
    name:      s.name,
    tool:      s.id || s.name,
    desc:      s.description || '',
    icon:      s.icon || '⚡',
    rarity:    s.rarity || 'blue',
    dangerous: s.dangerous || false,
    tags:      s.tags || [],
  })),
])

// ── Filtered stingers for library ────────────────────────────────────────────
const filteredStingers = computed(() => {
  let list = allStingers.value

  if (filterSpecies.value !== 'ALL') {
    if (filterSpecies.value === 'UTILITY') {
      list = list.filter(s => s.species === 'UTILITY')
    } else {
      list = list.filter(s => s.species === filterSpecies.value || s.species === 'UTILITY')
    }
  } else if (activeBeeKey.value !== 'queen') {
    // When 'ALL' tab + non-queen bee: show compatible stingers only
    const compatible = getBySpecies(activeSpeciesKey.value)
    const apiMapped = apiStingers.value.map(s => ({
      ...s, species: 'UTILITY', id: s.id || s.name,
    }))
    list = [...compatible, ...apiMapped]
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(s =>
      s.name.toLowerCase().includes(q) ||
      s.desc.toLowerCase().includes(q) ||
      s.tool.toLowerCase().includes(q)
    )
  }

  return list
})

// ── Slot interaction ─────────────────────────────────────────────────────────
function onSlotClick(slot) {
  if (slot.filled) {
    setSlot(slot.globalIdx, null)
    selectedSlotIdx.value = -1
  } else {
    selectedSlotIdx.value = selectedSlotIdx.value === slot.globalIdx ? -1 : slot.globalIdx
  }
}

function onCardClick(stinger) {
  if (isEquipped(stinger.id)) return

  let targetIdx = selectedSlotIdx.value
  if (targetIdx < 0) {
    const assignments = slotMap.value[activeBeeKey.value] || []
    const len = activeSlotTotal.value
    targetIdx = -1
    for (let i = 0; i < len; i++) {
      if (!assignments[i]) { targetIdx = i; break }
    }
    if (targetIdx < 0) {
      emit('toast', { message: '所有槽位已满，请先点击插槽卸下一枚蜂刺', type: 'error' })
      return
    }
  }

  setSlot(targetIdx, stinger.id)
  selectedSlotIdx.value = -1

  oathStinger.value = stinger
  oathVisible.value = true
  clearTimeout(oathTimer)
  oathTimer = setTimeout(() => { oathVisible.value = false }, 2800)

  nextTick(() => launchSparks())
}

// ── Bee change ───────────────────────────────────────────────────────────────
async function onBeeChange() {
  selectedSlotIdx.value = -1
  await loadForKey(activeBeeKey.value)
}

// ── Load / Save ──────────────────────────────────────────────────────────────
watch(() => store.activePanel, p => { if (p === 'skills') load() })
watch(() => store.activeHiveId, () => {
  activeBeeKey.value = 'queen'
  if (store.activePanel === 'skills') load()
})

async function load() {
  try {
    const raw = await api.getStingers()
    apiStingers.value = (Array.isArray(raw) ? raw : []).map(s =>
      typeof s === 'string'
        ? { id: s, name: s, icon: '⚡', description: '', rarity: 'blue', dangerous: false }
        : s
    )
    await loadForKey(activeBeeKey.value)
  } catch (err) {
    emit('toast', { message: `加载失败: ${err.message}`, type: 'error' })
  }
}

async function loadForKey(key) {
  try {
    const equipped = await api.getBeeStingers(key)
    const ids = (Array.isArray(equipped) ? equipped : [])
      .map(s => typeof s === 'string' ? s : (s.id ?? s))
    const len = activeSlotTotal.value
    const arr = Array.from({ length: len }, (_, i) => ids[i] ?? null)
    slotMap.value = { ...slotMap.value, [key]: arr }
  } catch {
    // silently ignore — bee may not have stingers yet
  }
}

async function save() {
  try {
    const ids = (slotMap.value[activeBeeKey.value] || []).filter(Boolean)
    await api.putBeeStingers(activeBeeKey.value, ids)
    if (ids.length) await nextTick().then(() => launchSparks())
    const label = ids.length === 1
      ? `「${getById(Number(ids[0]))?.name || ids[0]}」已植入 ${activeSpeciesName.value} 基因组 ✨`
      : `${ids.length} 枚蜂刺已植入 ${activeSpeciesName.value} 基因组 ✨`
    emit('toast', { message: label, type: 'success', duration: 4000 })
  } catch (err) {
    emit('toast', { message: `保存失败: ${err.message}`, type: 'error' })
  }
}

function rarityLabel(r) {
  return { blue: t('rarity_basic'), purple: t('rarity_epic'), gold: t('rarity_legend') }[r] || r
}

// ── Sparks ───────────────────────────────────────────────────────────────────
function launchSparks() {
  const center = centerRef.value
  if (!center) return
  const cr = center.getBoundingClientRect()
  const cx = cr.left + cr.width / 2
  const cy = cr.top  + cr.height / 2
  const GLYPHS = ['⚡', '✦', '◆', '✸', '★', '💫']
  const glow   = activeGlow.value

  for (let i = 0; i < 9; i++) {
    const id = ++sparkIdCounter
    const sx = window.innerWidth  * 0.5 + Math.random() * 300 - 50
    const sy = window.innerHeight * 0.3 + Math.random() * 200
    activeSparks.value.push({
      id,
      glyph: GLYPHS[i % GLYPHS.length],
      style: {
        left:           sx + 'px',
        top:            sy + 'px',
        '--tx':         (cx - sx) + 'px',
        '--ty':         (cy - sy) + 'px',
        animationDelay: i * 60 + 'ms',
        color:          [glow, '#A855F7', '#3B82F6', '#10B981'][i % 4],
      },
    })
    setTimeout(() => {
      activeSparks.value = activeSparks.value.filter(s => s.id !== id)
    }, 900 + i * 60)
  }
}

// ── Import ───────────────────────────────────────────────────────────────────
async function handleImport(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  const content = await file.text()
  const isDangerous = ['sudo', 'rm -rf', 'chmod 777', 'eval(', 'exec(']
    .some(p => content.toLowerCase().includes(p))
  if (isDangerous) {
    const ok = await requestGate(
      `脚本「${file.name}」中检测到高危指令。\n强制导入后请谨慎使用。`
    )
    if (!ok) return
  }
  try {
    const r = await api.importStinger(file)
    emit('toast', { message: `「${r.name || file.name}」导入成功`, type: 'success' })
    await load()
  } catch (err) {
    emit('toast', { message: `导入失败: ${err.message}`, type: 'error' })
  }
}

async function exportConfig() {
  try {
    const blob = await api.exportStingers(activeBeeKey.value)
    const url  = URL.createObjectURL(blob)
    const a    = Object.assign(document.createElement('a'), {
      href:     url,
      download: `openbe-stingers-${activeBeeKey.value}.zip`,
    })
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    emit('toast', { message: `导出失败: ${err.message}`, type: 'error' })
  }
}

// ── Security gate ─────────────────────────────────────────────────────────────
function requestGate(msg) {
  gateMsg.value  = msg
  gateShow.value = true
  return new Promise(r => { gateResolve = r })
}
function gateApprove() { gateShow.value = false; gateResolve?.(true)  }
function gateDeny()    { gateShow.value = false; gateResolve?.(false) }

defineExpose({ load })
</script>

<style scoped>
/* ══════════════════════════════════════════════════════════════════════════════
   Panel root
   ══════════════════════════════════════════════════════════════════════════════ */
#panel-skills {
  position: relative;
  flex-direction: column;
  overflow: hidden;
  color: var(--text-primary);
  background: linear-gradient(160deg, #FDFAF6 0%, #FFF9EF 100%);
  --rarity-blue:   #3B82F6;
  --rarity-purple: #A855F7;
  --rarity-gold:   #F59E0B;
  --danger-light:  rgba(239, 68, 68, 0.10);
}

/* ── Header ─────────────────────────────────────────────────────────────── */
.sp-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--border-light);
  background: rgba(255, 253, 248, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 20;
}

.sp-title {
  font-size: 0.8rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

.sp-search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 360px;
  margin: 0 16px;
  background: var(--bg-hover);
  border: 1px solid var(--border);
  border-radius: 99px;
  padding: 5px 14px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.sp-search-wrap:focus-within {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.sp-search-icon {
  color: var(--text-muted);
  font-size: 0.85rem;
  flex-shrink: 0;
}

.sp-search {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 0.8rem;
  color: var(--text-primary);
  font-family: var(--font-family);
  min-width: 0;
}
.sp-search::placeholder { color: var(--text-muted); }

.sp-search-clear {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px 4px;
  font-size: 0.75rem;
  line-height: 1;
  border-radius: 4px;
  transition: color 0.15s;
}
.sp-search-clear:hover { color: var(--text-primary); }

.sp-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.sp-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.70);
  border: 1px solid rgba(0, 0, 0, 0.12);
  color: var(--text-muted);
  border-radius: 8px;
  padding: 4px 12px;
  font-size: 0.78rem;
  cursor: pointer;
  font-family: var(--font-family);
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  white-space: nowrap;
}
.sp-btn:hover {
  background: rgba(255, 195, 0, 0.10);
  border-color: rgba(255, 195, 0, 0.35);
  color: var(--text-primary);
}

@keyframes forge-btn-glow {
  0%, 100% { box-shadow: 0 0 8px rgba(255, 195, 0, 0.30); }
  50%       { box-shadow: 0 0 18px rgba(255, 195, 0, 0.65); }
}
.sp-btn-forge {
  background: linear-gradient(135deg, #FFC300 0%, #FFD740 100%);
  border-color: rgba(255, 165, 0, 0.4);
  color: #1A1710;
  font-weight: 800;
  animation: forge-btn-glow 2.4s ease-in-out infinite;
}
.sp-btn-forge:hover {
  background: linear-gradient(135deg, #FFD740 0%, #FFC300 100%);
  color: #1A1710;
}

/* ── Species tabs ────────────────────────────────────────────────────────── */
.sp-species-tabs {
  display: flex;
  overflow-x: auto;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
  gap: 2px;
  scrollbar-width: none;
}
.sp-species-tabs::-webkit-scrollbar { display: none; }

.sp-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  font-family: var(--font-family);
  white-space: nowrap;
  color: var(--text-muted);
  margin-bottom: -1px;
}
.sp-tab:hover { color: var(--text-primary); }
.sp-tab.active {
  border-bottom-color: var(--tab-color, var(--accent));
  color: var(--tab-color, var(--accent));
  font-weight: 700;
}

.tab-emoji { font-size: 0.9rem; }
.tab-name  { font-size: 0.76rem; font-weight: 600; }
.tab-count {
  font-size: 0.62rem;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 5px;
  border-radius: 99px;
}

/* ── Body ────────────────────────────────────────────────────────────────── */
.sp-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ══════════════════════════════════════════════════════════════════════════════
   LEFT: Armory
   ══════════════════════════════════════════════════════════════════════════════ */
.sp-armory {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-light);
  background: rgba(255, 253, 248, 0.65);
  overflow-y: auto;
  padding-bottom: 16px;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 195, 0, 0.2) transparent;
}
.sp-armory::-webkit-scrollbar { width: 4px; }
.sp-armory::-webkit-scrollbar-thumb {
  background: rgba(255, 195, 0, 0.2);
  border-radius: 4px;
}

/* Bee selector */
.armory-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}
.bee-select {
  width: 100%;
  padding: 7px 10px;
  font-size: 0.8rem;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  color: var(--text-primary);
  outline: none;
  cursor: pointer;
  font-family: var(--font-family);
  transition: border-color 0.15s;
  appearance: auto;
}
.bee-select:focus { border-color: var(--border-focus); }

/* Logo area */
.armory-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 12px 16px;
  background: radial-gradient(ellipse at center, rgba(245, 166, 35, 0.06) 0%, transparent 70%);
}

.aura-wrap {
  position: relative;
  width: 180px;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.aura-ring {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

@keyframes aura-spin { to { transform: rotate(360deg); } }

.ar-outer {
  inset: 0;
  background: conic-gradient(
    from 0deg,
    transparent 0%,
    color-mix(in srgb, var(--ac, #F59E0B) 30%, transparent) 25%,
    transparent 50%,
    color-mix(in srgb, var(--ac, #F59E0B) 20%, transparent) 75%,
    transparent 100%
  );
  filter: blur(10px);
  animation: aura-spin 16s linear infinite;
}

.ar-mid {
  inset: 18px;
  border: 1px solid color-mix(in srgb, var(--ac, #F59E0B) 22%, transparent);
  animation: aura-spin 10s linear reverse infinite;
}

.ar-inner {
  inset: 38px;
  border: 1px dashed color-mix(in srgb, var(--ac, #F59E0B) 14%, transparent);
  animation: aura-spin 22s linear infinite;
}

.bee-logo {
  position: relative;
  z-index: 2;
  background: transparent !important;
}

/* Stats */
.armory-stats {
  display: flex;
  gap: 20px;
}
.astat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.astat-n {
  font-size: 1.2rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}
.astat-l {
  font-size: 0.62rem;
  color: var(--text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* Slot groups */
.armory-slots {
  flex: 1;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.slot-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slot-group-label {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--text-muted);
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 6px;
}
.slot-group-label::before,
.slot-group-label::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(245, 166, 35, 0.15);
}

.slot-group-row {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

/* Gem slots */
.gem-slot {
  width: 62px;
  height: 62px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.15s, box-shadow 0.15s;
  user-select: none;
  gap: 1px;
}
.gem-slot:hover { transform: scale(1.08); }

.slot-selected {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.slot-label {
  font-size: 0.46rem;
  font-weight: 600;
  color: rgba(255, 220, 140, 0.75);
  letter-spacing: 0.02em;
  text-align: center;
  position: absolute;
  top: 5px;
  left: 2px;
  right: 2px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  z-index: 3;
}

.slot-empty {
  background: linear-gradient(145deg, #2a1f08 0%, #3d2d0e 50%, #2a1f08 100%);
  border: 2px solid rgba(205, 127, 50, 0.85);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.4), inset 0 -1px 3px rgba(255, 195, 0, 0.15);
}

.gem-icon {
  font-size: 1.7rem;
  position: relative;
  z-index: 2;
  margin-top: 8px;
  line-height: 1;
}

.gem-plus {
  font-size: 1rem;
  color: rgba(205, 127, 50, 0.5);
  line-height: 1;
}

/* Rarity slot fills */
.slot-filled.rarity-blue {
  background: linear-gradient(145deg, #0d1b3e 0%, #1a3a70 50%, #0d1b3e 100%);
  border: 2px solid rgba(59, 130, 246, 0.75);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 10px rgba(59, 130, 246, 0.2);
}
.slot-filled.rarity-purple {
  background: linear-gradient(145deg, #1a0933 0%, #3b1060 50%, #1a0933 100%);
  border: 2px solid rgba(168, 85, 247, 0.75);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 10px rgba(168, 85, 247, 0.2);
}
.slot-filled.rarity-gold {
  background: linear-gradient(145deg, #2a1800 0%, #5a3500 50%, #2a1800 100%);
  border: 2px solid rgba(245, 158, 11, 0.85);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 14px rgba(245, 158, 11, 0.3);
}

@keyframes gem-breathe {
  0%, 100% { opacity: 0.25; }
  50%       { opacity: 0.55; }
}
.gem-glow {
  position: absolute;
  inset: 0;
  border-radius: 10px;
  pointer-events: none;
  animation: gem-breathe 2.4s ease-in-out infinite;
  z-index: 1;
}
.glow-blue   { background: radial-gradient(ellipse at center, rgba(59, 130, 246, 0.4) 0%, transparent 70%); }
.glow-purple { background: radial-gradient(ellipse at center, rgba(168, 85, 247, 0.4) 0%, transparent 70%); }
.glow-gold   { background: radial-gradient(ellipse at center, rgba(245, 158, 11, 0.4) 0%, transparent 70%); }

/* Armory footer */
.armory-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  margin-top: auto;
  flex-shrink: 0;
}
.save-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 9px 0;
  background: linear-gradient(135deg, #FFC300 0%, #FFD740 100%);
  border: none;
  border-radius: 99px;
  color: #1A1710;
  font-weight: 800;
  font-size: 0.88rem;
  cursor: pointer;
  font-family: var(--font-family);
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 4px 14px rgba(255, 195, 0, 0.35);
}
.save-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(255, 195, 0, 0.55);
}
.save-btn:active { transform: translateY(0); }

/* ══════════════════════════════════════════════════════════════════════════════
   RIGHT: Library
   ══════════════════════════════════════════════════════════════════════════════ */
.sp-library {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.lib-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.lib-title {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.lib-count {
  font-size: 0.7rem;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 8px;
  border-radius: 99px;
  font-weight: 500;
}

.lib-hint {
  font-size: 0.72rem;
  color: var(--accent);
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.lib-cancel-slot {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 0.68rem;
  padding: 2px 7px;
  cursor: pointer;
  font-family: var(--font-family);
  transition: border-color 0.15s, color 0.15s;
}
.lib-cancel-slot:hover {
  border-color: var(--danger);
  color: var(--danger);
}

/* Card grid */
.lib-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  align-content: start;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 195, 0, 0.2) transparent;
}
.lib-grid::-webkit-scrollbar { width: 4px; }
.lib-grid::-webkit-scrollbar-thumb {
  background: rgba(255, 195, 0, 0.2);
  border-radius: 4px;
}

/* Stinger card */
.stinger-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: white;
  border: 1.5px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
  position: relative;
  overflow: hidden;
}
.stinger-card:hover {
  border-color: rgba(255, 195, 0, 0.5);
  box-shadow: 0 6px 20px rgba(255, 195, 0, 0.14);
  transform: translateY(-2px);
}

.card-equipped {
  border-color: rgba(24, 168, 90, 0.4) !important;
  background: rgba(24, 168, 90, 0.03);
}

.card-gold::before,
.card-purple::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  border-radius: var(--radius) var(--radius) 0 0;
}
.card-gold::before   { background: linear-gradient(90deg, transparent, #F59E0B, transparent); }
.card-purple::before { background: linear-gradient(90deg, transparent, #A855F7, transparent); }

.card-dangerous::after {
  content: '';
  position: absolute;
  top: 8px;
  right: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--danger);
  box-shadow: 0 0 5px var(--danger);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.card-icon {
  font-size: 1.8rem;
  line-height: 1;
}

.card-species-badge {
  font-size: 0.6rem;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 99px;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.card-name {
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--text-primary);
  text-align: center;
}
.card-name-gold   { color: #D97706; }
.card-name-purple { color: #7C3AED; }
.card-name-blue   { color: var(--text-primary); }

.card-tool {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-muted);
  text-align: center;
}

.card-desc {
  font-size: 0.72rem;
  color: var(--text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  flex-wrap: wrap;
}

.card-rarity {
  font-size: 0.62rem;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 99px;
  flex-shrink: 0;
}
.rarity-chip-blue {
  color: #2563EB;
  background: rgba(59, 130, 246, 0.10);
  border: 1px solid rgba(59, 130, 246, 0.25);
}
.rarity-chip-purple {
  color: #7C3AED;
  background: rgba(168, 85, 247, 0.10);
  border: 1px solid rgba(168, 85, 247, 0.25);
}
.rarity-chip-gold {
  color: #D97706;
  background: rgba(245, 158, 11, 0.10);
  border: 1px solid rgba(245, 158, 11, 0.30);
}

.card-danger-flag {
  font-size: 0.62rem;
  color: var(--danger);
  background: var(--danger-light);
  padding: 1px 5px;
  border-radius: 99px;
  font-weight: 700;
  flex-shrink: 0;
}

.card-equip-btn {
  margin-left: auto;
  padding: 4px 12px;
  background: linear-gradient(135deg, #FFC300 0%, #FFD740 100%);
  color: #1A1710;
  border: none;
  border-radius: 99px;
  font-size: 0.7rem;
  font-weight: 700;
  cursor: pointer;
  font-family: var(--font-family);
  transition: transform 0.15s, box-shadow 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}
.card-equip-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(255, 195, 0, 0.4);
}

.card-equipped-tag {
  margin-left: auto;
  font-size: 0.68rem;
  color: var(--success);
  font-weight: 700;
  flex-shrink: 0;
}

/* Empty state */
.lib-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-muted);
  padding: 40px 20px;
}
.lib-empty-icon  { font-size: 2.5rem; opacity: 0.4; }
.lib-empty-title { font-size: 0.9rem; font-weight: 700; color: var(--text-primary); }
.lib-empty-sub   { font-size: 0.76rem; }

/* ══════════════════════════════════════════════════════════════════════════════
   Oath popup
   ══════════════════════════════════════════════════════════════════════════════ */
.oath-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 23, 16, 0.55);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.oath-card {
  background: white;
  border: 2px solid rgba(255, 195, 0, 0.5);
  border-radius: 20px;
  padding: 32px 40px;
  max-width: 380px;
  width: 90%;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 24px 60px rgba(180, 140, 0, 0.25);
}

.oath-bee-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.oath-bee-emoji { font-size: 2.5rem; line-height: 1; }
.oath-bee-name  { font-size: 1rem; font-weight: 800; color: var(--text-primary); text-align: left; }
.oath-bee-type  { font-size: 0.68rem; color: var(--text-muted); font-family: var(--font-mono); text-align: left; }

.oath-divider {
  color: var(--accent);
  font-size: 1rem;
  letter-spacing: 0.4em;
  opacity: 0.6;
}

.oath-skill-name {
  font-size: 1.5rem;
  font-weight: 900;
  color: #D97706;
  letter-spacing: -0.02em;
}
.oath-skill-sub { font-size: 0.8rem; color: var(--text-muted); }

.oath-text {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

.oath-tool {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 3px 10px;
  border-radius: 99px;
}

.oath-pop-enter-active,
.oath-pop-leave-active {
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.oath-pop-enter-from,
.oath-pop-leave-to {
  opacity: 0;
}
.oath-pop-enter-from .oath-card,
.oath-pop-leave-to .oath-card {
  transform: scale(0.82) translateY(12px);
}

/* ══════════════════════════════════════════════════════════════════════════════
   Tooltip
   ══════════════════════════════════════════════════════════════════════════════ */
.sp-tooltip {
  position: fixed;
  z-index: 9999;
  min-width: 180px;
  max-width: 240px;
  background: rgba(255, 253, 248, 0.97);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(245, 166, 35, 0.2);
  border-radius: 12px;
  padding: 10px 14px;
  pointer-events: none;
  box-shadow: 0 8px 24px rgba(180, 130, 0, 0.18);
  color: var(--text-primary);
}

.tt-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
}
.tt-name   { font-weight: 700; font-size: 0.86rem; }
.tt-blue   { color: #2563EB; }
.tt-purple { color: #7C3AED; }
.tt-gold   { color: #D97706; }

.tt-danger {
  font-size: 0.62rem;
  color: var(--danger);
  background: var(--danger-light);
  padding: 1px 6px;
  border-radius: 99px;
  font-weight: 700;
  margin-left: auto;
}

.tt-desc {
  font-size: 0.74rem;
  color: var(--text-muted);
  line-height: 1.5;
}
.tt-hint {
  font-size: 0.66rem;
  color: var(--text-muted);
  opacity: 0.6;
  margin-top: 4px;
}

/* ══════════════════════════════════════════════════════════════════════════════
   Security gate
   ══════════════════════════════════════════════════════════════════════════════ */
.gate-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 23, 16, 0.65);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

.gate-modal {
  background: white;
  border: 2px solid rgba(239, 68, 68, 0.35);
  border-radius: 16px;
  width: 360px;
  max-width: 92vw;
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}

.gate-stripe {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  font-weight: 700;
  font-size: 0.85rem;
  padding: 12px 18px;
  letter-spacing: 0.04em;
}

.gate-body {
  padding: 18px;
  font-size: 0.83rem;
  line-height: 1.65;
  color: var(--text-primary);
  white-space: pre-line;
}

.gate-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 18px 18px;
}

.gate-confirm {
  padding: 6px 16px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  font-family: var(--font-family);
  transition: background 0.15s;
}
.gate-confirm:hover { background: #dc2626; }

/* ══════════════════════════════════════════════════════════════════════════════
   Spark particles
   ══════════════════════════════════════════════════════════════════════════════ */
.sp-sparks-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 99999;
}

@keyframes sparkFly {
  0%   { transform: translate(0, 0) scale(1.2); opacity: 1; }
  60%  { transform: translate(calc(var(--tx) * 0.7), calc(var(--ty) * 0.7)) scale(0.8); opacity: 0.7; }
  100% { transform: translate(var(--tx), var(--ty)) scale(0.2); opacity: 0; }
}

.data-spark {
  position: absolute;
  font-size: 1.1rem;
  animation: sparkFly 0.8s ease-in forwards;
  filter: drop-shadow(0 0 4px currentColor);
  will-change: transform, opacity;
}
</style>
