<template>
  <div
    class="panel"
    :class="{ active: store.activePanel === 'habitat' }"
    id="panel-habitat"
    ref="fieldEl"
    :style="{ background: skyGradient }"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @mouseleave="onMouseUp"
  >

    <!-- ── 天空层 ── -->
    <div class="sky-layer" aria-hidden="true">
      <!-- 星星（夜晚） -->
      <div v-if="isNight" class="star-layer">
        <div v-for="s in stars" :key="s.id" class="star"
          :style="{ left: s.left, top: s.top, width: s.size+'px', height: s.size+'px',
                    animationDelay: s.delay, animationDuration: s.dur }"></div>
      </div>
      <!-- 太阳 / 月亮 -->
      <div class="sun" :style="celestialStyle">{{ celestialEmoji }}</div>
      <!-- 云朵 -->
      <div v-for="c in dynamicClouds" :key="c.id" class="cloud" :style="c.style">{{ c.e }}</div>
    </div>

    <!-- ── 天气覆盖层（雾/阴天/暴风） ── -->
    <div v-if="weatherOverlay" class="weather-overlay" :style="{ background: weatherOverlay }"></div>

    <!-- ── 雨层 ── -->
    <div v-if="showRain" class="rain-layer" aria-hidden="true">
      <div v-for="r in raindrops" :key="r.id" class="raindrop"
        :style="{ left: r.left, animationDelay: r.delay, animationDuration: r.dur,
                  opacity: r.opacity, height: r.h+'px' }"></div>
    </div>

    <!-- ── 雪层 ── -->
    <div v-if="showSnow" class="snow-layer" aria-hidden="true">
      <div v-for="s in snowflakes" :key="s.id" class="snowflake"
        :style="{ left: s.left, animationDelay: s.delay, animationDuration: s.dur, fontSize: s.size+'rem' }">❄</div>
    </div>

    <!-- ── 大地装饰（固定摆件） ── -->
    <div class="ground-deco" aria-hidden="true">
      <span class="deco-item" style="left:6%;bottom:52px;font-size:2.2rem" title="花丛">🌸</span>
      <span class="deco-item" style="left:12%;bottom:48px;font-size:1.5rem">🌼</span>
      <span class="deco-item" style="left:16%;bottom:50px;font-size:1.1rem">🌷</span>
      <span class="deco-item" style="left:74%;bottom:54px;font-size:2rem" title="蘑菇">🍄</span>
      <span class="deco-item" style="left:80%;bottom:50px;font-size:1.4rem">🍄</span>
      <span class="deco-item" style="left:38%;bottom:50px;font-size:1.8rem" title="水坑">💧</span>
      <span class="deco-item" style="left:55%;bottom:52px;font-size:1.4rem">🪨</span>
      <span class="deco-item" style="right:10%;bottom:56px;font-size:2.8rem" title="大树">🌳</span>
      <span class="deco-item" style="left:25%;bottom:50px;font-size:1.2rem">🌿</span>
      <span class="deco-item" style="left:90%;bottom:50px;font-size:1.1rem">🌿</span>
      <!-- 草地 -->
      <div class="grass-strip">
        <span v-for="g in 28" :key="g" class="grass" :style="grassStyle(g)">{{ g%3===0?'🌿':'🌱' }}</span>
      </div>
    </div>

    <!-- ── 萤火虫 ── -->
    <div class="firefly-layer" aria-hidden="true">
      <div v-for="i in 10" :key="i" class="firefly" :style="fireflyStyle(i)"></div>
    </div>

    <!-- ── 空状态 ── -->
    <Transition name="field-fade">
      <div v-if="activeBees.length === 0" class="field-empty">
        <div class="field-empty-egg">🥚</div>
        <p class="field-empty-title">草地空空如也</p>
        <p class="field-empty-sub">去蜂蛹管理孵化你的第一只蜜蜂</p>
        <button class="field-empty-btn" @click="store.setActivePanel('bees')">🥚 去孵化</button>
      </div>
    </Transition>

    <!-- ── 蜜蜂实体 ── -->
    <div
      v-for="bee in activeBees"
      :key="bee._key"
      class="bee-entity"
      :class="[
        `sp-${speciesKey(bee).toLowerCase()}`,
        `state-${entityState(bee._key)}`,
        {
          'is-dragging':  draggingKey === bee._key,
          'is-selected':  selectedKey === bee._key,
          'no-transition': draggingKey === bee._key,
          'bee-entering':  enteringKeys.has(bee._key),
        }
      ]"
      :style="entityStyle(bee._key)"
      @mousedown.prevent="startDrag(bee._key, $event)"
      @click="clickBee(bee._key, bee)"
    >
      <!-- 影子 -->
      <div class="bee-shadow" :class="`state-${entityState(bee._key)}`"></div>

      <!-- 图片 -->
      <img
        class="bee-sprite"
        :src="`/assets/bees/${speciesKey(bee).toLowerCase()}.png`"
        :alt="speciesKey(bee)"
        :style="{ transform: beeData[bee._key]?.facingRight === false ? 'scaleX(-1)' : '' }"
        @error="onImgError"
      />

      <!-- 对话气泡 -->
      <Transition name="bubble-pop">
        <div v-if="beeData[bee._key]?.bubble" class="speech-bubble">
          {{ beeData[bee._key].bubble }}
        </div>
      </Transition>

      <!-- 状态角标 -->
      <div class="bee-state-badge" :class="`badge-${entityState(bee._key)}`">
        {{ stateBadge(bee._key) }}
      </div>

      <!-- 名牌 -->
      <div class="bee-nametag">
        <span class="nametag-dot" :class="`dot-${beeHealth(bee)}`"></span>
        <span class="nametag-text">{{ bee.beeName || speciesLabel(bee) }}</span>
      </div>
    </div>

    <!-- ── Header ── -->
    <div class="field-header">
      <span class="fh-glyph">🌸</span>
      <span class="fh-title">栖息之野</span>
      <span class="fh-count">{{ activeBees.length }} 只</span>
      <div class="fh-weather">
        <span class="fh-wicon">{{ weatherInfo.emoji }}</span>
        <span class="fh-wtext">{{ weatherInfo.label }}<template v-if="weatherTemp !== null"> · {{ weatherTemp }}°C</template></span>
        <span v-if="weatherCity" class="fh-city">{{ weatherCity }}</span>
      </div>
      <button class="fab-btn fab-resonate fh-resonate" @click="showDialogue = true">🔮 共振</button>
    </div>

    <!-- ── Footer ── -->
    <div class="field-footer">
      <div class="ff-stat"><span class="hsd active"></span>工作中 {{ busyCount }}</div>
      <div class="ff-stat"><span class="hsd idle"></span>休闲中 {{ idleCount }}</div>
      <div class="ff-stat"><span class="hsd error"></span>异常 {{ errorCount }}</div>
      <div class="ff-motto">🌸 高自主 · 有灵性 · 自由生长 🌸</div>
    </div>

    <!-- ── 思维共振弹窗 ── -->
    <BeeDialogueModal :visible="showDialogue" @close="showDialogue = false" />

    <!-- ── 详情弹窗 ── -->
    <Transition name="modal-fade">
      <div v-if="modal.visible" class="modal-overlay" @click.self="closeModal">
        <div class="modal-card" :style="{ '--sp-glow': spGlow(modal.bee) }">
          <button class="modal-close" @click="closeModal">✕</button>
          <div class="modal-img-wrap">
            <img class="modal-bee-img"
              :src="`/assets/bees/${modal.bee ? speciesKey(modal.bee).toLowerCase() : 'worker'}.png`"
              @error="onImgError" />
            <div class="modal-glow-ring"></div>
          </div>
          <div class="modal-name">{{ modal.bee?.beeName || speciesLabel(modal.bee) }}</div>
          <div class="modal-tag">{{ speciesLabel(modal.bee) }}</div>
          <div class="modal-info-grid">
            <div class="mig-row"><span class="mig-k">状态</span><span class="mig-v" :class="`sv-${beeHealth(modal.bee)}`">{{ healthText(modal.bee) }}</span></div>
            <div class="mig-row"><span class="mig-k">PID</span><span class="mig-v mono">#{{ modal.bee?.pid || '—' }}</span></div>
            <div class="mig-row" v-if="modal.bee?.hiveId"><span class="mig-k">蜂巢</span><span class="mig-v">{{ hiveLabel(modal.bee.hiveId) }}</span></div>
            <div class="mig-row"><span class="mig-k">任务</span><span class="mig-v mono">{{ modal.bee?.activeTasks || 0 }}</span></div>
          </div>
          <div class="modal-actions">
            <button class="modal-btn chat-btn" @click="goChat">💬 对话</button>
            <button class="modal-btn will-btn" @click="goWill">💉 意志注入</button>
          </div>
        </div>
      </div>
    </Transition>

  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '../../stores/app.js'
import BeeDialogueModal from '../BeeDialogueModal.vue'

const store = useAppStore()

// ══════════════════════════════════════════════════════════════
// 天气 & 时刻系统
// ══════════════════════════════════════════════════════════════

// ── WMO 天气码映射 ───────────────────────────────────────────
const WEATHER_CODES = {
  0:  { label: '晴空万里', emoji: '☀️',  kind: 'clear' },
  1:  { label: '多云偶晴', emoji: '🌤',  kind: 'partly-cloudy' },
  2:  { label: '多云',     emoji: '⛅',  kind: 'cloudy' },
  3:  { label: '阴天',     emoji: '☁️',  kind: 'overcast' },
  45: { label: '大雾',     emoji: '🌫️', kind: 'fog' },
  48: { label: '冻雾',     emoji: '🌫️', kind: 'fog' },
  51: { label: '细雨蒙蒙', emoji: '🌦',  kind: 'drizzle' },
  53: { label: '毛毛雨',   emoji: '🌦',  kind: 'drizzle' },
  55: { label: '绵绵细雨', emoji: '🌧',  kind: 'drizzle' },
  61: { label: '小雨',     emoji: '🌧',  kind: 'rain' },
  63: { label: '中雨',     emoji: '🌧',  kind: 'rain' },
  65: { label: '大雨滂沱', emoji: '🌧',  kind: 'rain' },
  71: { label: '小雪',     emoji: '🌨',  kind: 'snow' },
  73: { label: '中雪',     emoji: '❄️',  kind: 'snow' },
  75: { label: '大雪纷飞', emoji: '❄️',  kind: 'snow' },
  80: { label: '阵雨',     emoji: '🌦',  kind: 'rain' },
  81: { label: '中等阵雨', emoji: '🌧',  kind: 'rain' },
  82: { label: '强阵雨',   emoji: '🌧',  kind: 'rain' },
  95: { label: '雷雨交加', emoji: '⛈',  kind: 'storm' },
  96: { label: '冰雹雷雨', emoji: '⛈',  kind: 'storm' },
  99: { label: '暴雨雷鸣', emoji: '⛈',  kind: 'storm' },
}

// ── 时刻分段 ────────────────────────────────────────────────
const TIME_GRADIENTS = {
  night:     'linear-gradient(180deg, #050c24 0%, #0a1438 22%, #0e1c10 58%, #182a0c 80%, #243815 100%)',
  dawn:      'linear-gradient(180deg, #2e0a58 0%, #b03010 14%, #e07020 28%, #f0b840 44%, #c8e090 66%, #8bc34a 100%)',
  morning:   'linear-gradient(180deg, #48b8e8 0%, #8cd8f5 18%, #d4f1c0 52%, #a8d880 72%, #8bc34a 100%)',
  noon:      'linear-gradient(180deg, #0e78d4 0%, #58b8e0 18%, #c8f0b0 52%, #a0d870 72%, #78b830 100%)',
  afternoon: 'linear-gradient(180deg, #2a88c8 0%, #85c8e8 18%, #d4f1c0 52%, #a8d880 72%, #8bc34a 100%)',
  dusk:      'linear-gradient(180deg, #b82000 0%, #d86818 14%, #e8a828 30%, #f0d858 46%, #c0e080 68%, #80b030 100%)',
  evening:   'linear-gradient(180deg, #0e0820 0%, #241440 22%, #182810 56%, #22380e 80%, #304818 100%)',
}
const WEATHER_OVERLAY = {
  clear: null, 'partly-cloudy': null,
  cloudy:   'rgba(140,150,160,0.20)',
  overcast: 'rgba(80,90,100,0.38)',
  fog:      'rgba(180,190,200,0.52)',
  drizzle:  'rgba(70,95,125,0.22)',
  rain:     'rgba(40,60,90,0.38)',
  snow:     'rgba(200,215,235,0.32)',
  storm:    'rgba(15,20,38,0.60)',
}

// ── 响应式状态 ───────────────────────────────────────────────
const currentHour  = ref(new Date().getHours())
const weatherCode  = ref(null)   // WMO code (null = 未获取)
const weatherTemp  = ref(null)   // °C
const weatherCity  = ref('')
const weatherIsDay = ref(true)   // from API

// ── 计算属性 ─────────────────────────────────────────────────
function getTimeSlot(h) {
  if (h < 5)  return 'night'
  if (h < 7)  return 'dawn'
  if (h < 10) return 'morning'
  if (h < 14) return 'noon'
  if (h < 17) return 'afternoon'
  if (h < 19) return 'dusk'
  if (h < 21) return 'evening'
  return 'night'
}

const timeSlot = computed(() => getTimeSlot(currentHour.value))
const isNight  = computed(() => {
  if (weatherCode.value !== null) return !weatherIsDay.value
  const h = currentHour.value
  return h < 5 || h >= 21
})

const skyGradient = computed(() => TIME_GRADIENTS[timeSlot.value])

const weatherInfo = computed(() => {
  if (weatherCode.value === null) {
    const defaults = { night: { label: '星光灿烂', emoji: '🌙' },
      dawn:      { label: '晨曦初现', emoji: '🌅' },
      morning:   { label: '微风和煦', emoji: '🌤' },
      noon:      { label: '烈日当空', emoji: '☀️' },
      afternoon: { label: '阳光明媚', emoji: '🌞' },
      dusk:      { label: '余晖斜照', emoji: '🌇' },
      evening:   { label: '暮色降临', emoji: '🌆' } }
    return defaults[timeSlot.value] || { label: '微风和煦', emoji: '🌤' }
  }
  const w = WEATHER_CODES[weatherCode.value]
  return w || { label: '天气未知', emoji: '🌡️', kind: 'clear' }
})

const weatherKind    = computed(() => weatherInfo.value.kind || 'clear')
const weatherOverlay = computed(() => WEATHER_OVERLAY[weatherKind.value] || null)
const showRain       = computed(() => ['drizzle', 'rain', 'storm'].includes(weatherKind.value))
const showSnow       = computed(() => weatherKind.value === 'snow')

// ── 天体（太阳/月亮）── ─────────────────────────────────────
const celestialEmoji = computed(() => {
  if (isNight.value) return '🌙'
  const s = timeSlot.value
  if (s === 'dawn' || s === 'dusk') return '🌅'
  return '☀️'
})
const celestialStyle = computed(() => {
  const h = currentHour.value
  if (isNight.value) {
    return 'right:18%;top:14%;font-size:2rem;filter:drop-shadow(0 0 18px rgba(200,220,255,.8));animation:sun-spin 120s linear infinite'
  }
  // Map hour 5→19 to left 4%→88%
  const t    = Math.max(0, Math.min(1, (h - 5) / 14))
  const left = 4 + t * 84
  // Arc: peak height at noon
  const bot  = 10 + Math.sin(t * Math.PI) * 52
  return `left:${left}%;bottom:${bot}%;font-size:2.6rem;filter:drop-shadow(0 0 18px rgba(255,220,50,.7));animation:sun-spin 40s linear infinite`
})

// ── 动态云朵（根据天气调整数量/不透明度）── ──────────────────
const dynamicClouds = computed(() => {
  const kind = weatherKind.value
  const base = [
    { id:1, e:'☁️', style:'left:8%;top:10%;animation:cloud-drift 18s ease-in-out infinite alternate;font-size:2rem' },
    { id:2, e:'⛅', style:'left:40%;top:6%;animation:cloud-drift 24s ease-in-out infinite alternate-reverse;font-size:1.5rem' },
    { id:3, e:'☁️', style:'left:70%;top:12%;animation:cloud-drift 20s ease-in-out infinite alternate;font-size:1.8rem' },
  ]
  const extra = [
    { id:4, e:'☁️', style:'left:20%;top:18%;animation:cloud-drift 22s ease-in-out 1s infinite alternate;font-size:2.2rem;opacity:.65' },
    { id:5, e:'☁️', style:'left:55%;top:8%;animation:cloud-drift 17s ease-in-out 2s infinite alternate-reverse;font-size:1.6rem;opacity:.60' },
    { id:6, e:'☁️', style:'left:82%;top:20%;animation:cloud-drift 25s ease-in-out 0.5s infinite alternate;font-size:2rem;opacity:.55' },
  ]
  const opacity = kind === 'clear' ? 0.45
    : kind === 'partly-cloudy' ? 0.60
    : kind === 'cloudy'   ? 0.80
    : kind === 'overcast' ? 0.92
    : kind === 'fog'      ? 0.30
    : 0.75

  const list = ['clear', 'partly-cloudy'].includes(kind) ? base : [...base, ...extra]
  return list.map(c => ({ ...c, style: c.style + `;opacity:${opacity}` }))
})

// ── 静态粒子（预生成，避免每渲染重算）── ─────────────────────
const stars = Array.from({ length: 65 }, (_, i) => ({
  id: i, left: `${(i * 13.7 + 5) % 93}%`, top: `${(i * 7.3 + 2) % 52}%`,
  size: 1 + (i % 3), delay: `${(i * 0.37) % 4}s`, dur: `${1.5 + (i * 0.23) % 2.5}s`,
}))
const raindrops = Array.from({ length: 55 }, (_, i) => ({
  id: i, left: `${(i * 7.3) % 100}%`,
  delay: `${(i * 0.11) % 1.8}s`, dur: `${0.55 + (i * 0.07) % 0.7}s`,
  opacity: 0.25 + (i % 4) * 0.13, h: 10 + (i % 3) * 5,
}))
const snowflakes = Array.from({ length: 32 }, (_, i) => ({
  id: i, left: `${(i * 3.7) % 100}%`,
  delay: `${(i * 0.27) % 3}s`, dur: `${3.5 + (i * 0.31) % 4}s`,
  size: 0.7 + (i % 3) * 0.25,
}))

// ── 天气获取（IP 定位 + Open-Meteo）── ──────────────────────
let _weatherFetched = false

async function fetchWeather() {
  if (_weatherFetched) return
  _weatherFetched = true
  try {
    const geoRes = await fetch('https://ipapi.co/json/', { signal: AbortSignal.timeout(5000) })
    const geo    = await geoRes.json()
    const lat = geo.latitude, lon = geo.longitude
    if (!lat || !lon) return
    weatherCity.value = geo.city || geo.region || ''

    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code,is_day&timezone=auto`
    const wRes = await fetch(url, { signal: AbortSignal.timeout(5000) })
    const w    = await wRes.json()
    const cur  = w.current
    weatherCode.value  = cur.weather_code
    weatherTemp.value  = Math.round(cur.temperature_2m)
    weatherIsDay.value = cur.is_day === 1
  } catch {
    // 静默失败，保留时刻默认值
  }
}

// ── 每分钟更新时刻 ───────────────────────────────────────────
let clockTimer = null

// ── 物种元数据 ──────────────────────────────────────────────────
const SPECIES_META = {
  WORKER:     { label: '工蜂',   glow: '#f9a825' },
  SOLDIER:    { label: '战蜂',   glow: '#e53935' },
  NURSE:      { label: '育蜜蜂', glow: '#e91e63' },
  SCOUT:      { label: '侦察蜂', glow: '#1e88e5' },
  PAINTER:    { label: '画蜂',   glow: '#8e24aa' },
  MECHANIC:   { label: '机械蜂', glow: '#546e7a' },
  MEDIC:      { label: '医疗蜂', glow: '#43a047' },
  SCRIBE:     { label: '书记蜂', glow: '#7b1fa2' },
  EDITOR:     { label: '剪辑蜂', glow: '#3949ab' },
  INFLUENCER: { label: '博主蜂', glow: '#ff6f00' },
  SENTINEL:   { label: '哨蜂',   glow: '#e64a19' },
}

const GREETINGS = {
  QUEEN:      ['我在统筹全局，有任务尽管说 👑', '蜂巢一切正常，放心吧 🌟', '需要我调度什么？🍯'],
  WORKER:     ['有代码要写吗？交给我！🏗️', '准备好了，随时开工！', '采蜜回来啦，嗨～ 🌸'],
  SOLDIER:    ['蜂巢安全无异常 🪖', '我在巡逻，放心', '发现可疑目标会通知你！🔍'],
  NURSE:      ['记忆库已更新 🍯', '有什么要记住的吗？', '让我帮你整理一下～ 📋'],
  SCOUT:      ['情报收集中！🔭', '发现新大陆了～ 🗺️', '好奇心爆棚！给我任务吧'],
  PAINTER:    ['灵感来了！🎨', '画个啥好呢～ 🖌️', '你来下单，我来画！'],
  MECHANIC:   ['系统一切正常 ⚙️', '来点自动化任务吧！', '脚本已就绪～'],
  MEDIC:      ['你还好吗？🏥', '健康检查中...', '有什么我能诊断的？💊'],
  SCRIBE:     ['文档准备好了 📝', '笔已拿好，说吧！', '写文档最喜欢了～'],
  EDITOR:     ['素材已剪好！🎬', '来个创意吧～', '剪辑完美主义者在线 ✂️'],
  INFLUENCER: ['今天发什么好 📱', '流量密码get！', '内容创作中，等等我～'],
  SENTINEL:   ['监控一切正常 🔔', '有风吹草动我马上告诉你！', '我盯着呢，放心'],
}

const REST_LINES = [
  '打个盹儿... Zzz 😴', '有点困了，休息一下 💤',
  '采完蜜好累，歇一歇 🌸', '在这里晒晒太阳真舒服 ☀️',
  '闻到花香了~ 🌼', '这朵花好看！🌷',
]

const WANDER_LINES = [
  '溜达溜达～', '去那边看看 🔍', '走走走！', '探索一下 🗺️',
]

// ── 休息锚点（蜜蜂会飞向这里休息） ────────────────────────────
const REST_SPOTS = [
  { x: 9,  y: 62 },   // 左侧花丛
  { x: 77, y: 60 },   // 右侧蘑菇
  { x: 40, y: 65 },   // 水坑
  { x: 57, y: 63 },   // 石头
  { x: 88, y: 55 },   // 大树旁
]

// ── 活跃蜜蜂列表（仅在线，仅当前蜂巢）──────────────────────────
const activeBees = computed(() => {
  const activeHiveId = store.activeHiveId
  return Object.entries(store.bees)
    .filter(([, b]) => {
      const type   = (b.beeType || b.type || '').toLowerCase()
      const status = (b.status  || '').toLowerCase()
      if (type === 'queen') return false
      if (status === 'offline') return false
      // 只显示属于当前蜂巢的蜜蜂
      if (activeHiveId && b.hiveId && b.hiveId !== activeHiveId) return false
      // 蜜蜂有 hiveId 但蜂巢已不存在 → 过滤掉
      if (b.hiveId && !store.hives.find(h => h.hiveId === b.hiveId)) return false
      return true
    })
    .map(([key, b]) => ({ ...b, _key: key }))
})

// ── 蜜蜂实体状态数据（响应式） ─────────────────────────────────
const beeData = reactive({})

function initBee(key) {
  if (beeData[key]) return
  beeData[key] = {
    x:           20 + Math.random() * 60,
    y:           20 + Math.random() * 50,
    targetX:     null,
    targetY:     null,
    state:       'idle',           // idle | wandering | resting | playing
    idleFor:     0,                // ticks in idle
    nextActionAt: 80 + Math.random() * 160,
    bubble:      null,
    bubbleTimer: null,
    facingRight: Math.random() > 0.5,
  }
}

watch(activeBees, bees => {
  bees.forEach(b => initBee(b._key))
}, { immediate: true, deep: true })

// ── 新孵化动画 ─────────────────────────────────────────────────
const enteringKeys = reactive(new Set())
let prevBeeKeys = new Set()
watch(activeBees, bees => {
  const now = new Set(bees.map(b => b._key))
  bees.forEach(b => {
    if (!prevBeeKeys.has(b._key)) {
      initBee(b._key)
      enteringKeys.add(b._key)
      // 孵化打招呼
      setTimeout(() => showBubble(b._key, '我孵化啦！好高兴见到你 🥚✨'), 400)
      setTimeout(() => enteringKeys.delete(b._key), 900)
    }
  })
  prevBeeKeys = now
}, { deep: true })

// ── 自主行为引擎（每200ms tick） ───────────────────────────────
let autonomyTimer = null

function autonomyTick() {
  activeBees.value.forEach(bee => {
    const d = beeData[bee._key]
    if (!d || draggingKey.value === bee._key) return

    const busy = (bee.status || '').toLowerCase() === 'busy'

    if (busy) {
      d.state = 'busy'
      return
    }

    if (d.state === 'wandering' || d.state === 'resting-walk') {
      // 向目标移动
      if (d.targetX !== null) {
        const dx = d.targetX - d.x
        const dy = d.targetY - d.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < 1.2) {
          d.x = d.targetX
          d.y = d.targetY
          d.targetX = null
          d.targetY = null
          d.state = d.state === 'resting-walk' ? 'resting' : 'idle'
          d.idleFor = 0
          d.nextActionAt = 60 + Math.random() * 120
        } else {
          const speed = d.state === 'resting-walk' ? 0.25 : 0.35
          d.x += (dx / dist) * speed
          d.y += (dy / dist) * speed
          d.facingRight = dx > 0
        }
      }
      return
    }

    if (d.state === 'resting' || d.state === 'playing') {
      d.idleFor++
      if (d.idleFor > d.nextActionAt) {
        d.state = 'idle'
        d.idleFor = 0
        d.nextActionAt = 60 + Math.random() * 100
      }
      return
    }

    // idle
    d.idleFor++
    if (d.idleFor < d.nextActionAt) return

    // 决策
    d.idleFor = 0
    const r = Math.random()
    if (r < 0.35) {
      // 漫游到随机点
      d.targetX = clamp(10 + Math.random() * 78, 5, 90)
      d.targetY = clamp(10 + Math.random() * 55, 8, 68)
      d.state = 'wandering'
      d.nextActionAt = 80 + Math.random() * 160
      if (Math.random() < 0.4) showBubble(bee._key, rand(WANDER_LINES))
    } else if (r < 0.60) {
      // 飞去休息点
      const spot = REST_SPOTS[Math.floor(Math.random() * REST_SPOTS.length)]
      d.targetX = spot.x + (Math.random() - 0.5) * 4
      d.targetY = spot.y + (Math.random() - 0.5) * 3
      d.state = 'resting-walk'
      d.nextActionAt = 100 + Math.random() * 200
      if (Math.random() < 0.5) showBubble(bee._key, rand(REST_LINES))
    } else if (r < 0.80) {
      // 原地玩耍
      d.state = 'playing'
      d.nextActionAt = 40 + Math.random() * 60
    } else {
      // 继续 idle，延迟下次决策
      d.nextActionAt = 40 + Math.random() * 80
    }
  })
}

onMounted(() => {
  autonomyTimer = setInterval(autonomyTick, 200)
  // 每分钟刷新时刻
  clockTimer = setInterval(() => { currentHour.value = new Date().getHours() }, 60_000)
  // 获取天气（延迟 800ms 避免阻塞渲染）
  setTimeout(fetchWeather, 800)
})
onUnmounted(() => {
  clearInterval(autonomyTimer)
  clearInterval(clockTimer)
})

// ── 拖拽 ──────────────────────────────────────────────────────
const fieldEl    = ref(null)
const draggingKey = ref(null)
let   dragOffX = 0, dragOffY = 0

function startDrag(key, e) {
  draggingKey.value = key
  const d = beeData[key]
  const rect = fieldEl.value.getBoundingClientRect()
  dragOffX = e.clientX - rect.left - (d.x / 100) * rect.width
  dragOffY = e.clientY - rect.top  - (d.y / 100) * rect.height
}

function onMouseMove(e) {
  if (!draggingKey.value) return
  const d    = beeData[draggingKey.value]
  const rect = fieldEl.value.getBoundingClientRect()
  const nx   = ((e.clientX - rect.left - dragOffX) / rect.width)  * 100
  const ny   = ((e.clientY - rect.top  - dragOffY) / rect.height) * 100
  d.facingRight = nx > d.x
  d.x = clamp(nx, 3, 92)
  d.y = clamp(ny, 5, 75)
}

function onMouseUp() {
  if (draggingKey.value) {
    const d = beeData[draggingKey.value]
    d.state = 'idle'
    d.idleFor = 0
    d.nextActionAt = 40 + Math.random() * 60
    draggingKey.value = null
  }
}

// ── 点击打招呼 ─────────────────────────────────────────────────
const selectedKey = ref(null)

function clickBee(key, bee) {
  if (draggingKey.value) return
  selectedKey.value = key
  const sp = speciesKey(bee)
  const lines = GREETINGS[sp] || GREETINGS.WORKER
  showBubble(key, rand(lines))
  setTimeout(() => openModal(bee), 300)
}

function showBubble(key, text) {
  const d = beeData[key]
  if (!d) return
  if (d.bubbleTimer) clearTimeout(d.bubbleTimer)
  d.bubble = text
  d.bubbleTimer = setTimeout(() => { d.bubble = null }, 3800)
}

// ── 弹窗 ──────────────────────────────────────────────────────
const modal     = reactive({ visible: false, bee: null })
const showDialogue = ref(false)

function openModal(bee)  { modal.bee = bee; modal.visible = true }
function closeModal()    { modal.visible = false; selectedKey.value = null }

function goChat() {
  store.activateChatBee(modal.bee._key)
  store.setActivePanel('chat')
  closeModal()
}
function goWill() {
  store.activeEditingBeeKey = modal.bee._key
  store.setActivePanel('will')
  closeModal()
}

// ── 计算样式 ───────────────────────────────────────────────────
function entityStyle(key) {
  const d = beeData[key]
  if (!d) return {}
  const glow = spGlowByKey(key)
  return {
    left:    `${d.x}%`,
    top:     `${d.y}%`,
    '--glow': glow,
  }
}

function spGlowByKey(key) {
  const bee = activeBees.value.find(b => b._key === key)
  return bee ? spGlow(bee) : '#f9a825'
}

function entityState(key) {
  return beeData[key]?.state || 'idle'
}

function stateBadge(key) {
  const s = entityState(key)
  const m = { idle: '😌', wandering: '🚶', 'resting-walk': '🚶', resting: '😴', playing: '🎉', busy: '⚡' }
  return m[s] || '😌'
}

// ── 物种工具 ───────────────────────────────────────────────────
function speciesKey(bee) {
  const ds = (bee.displaySpecies || '').toUpperCase()
  if (ds && SPECIES_META[ds]) return ds
  const id = bee.beeId || ''
  if (id.includes('-')) {
    const p = id.split('-')[0].toUpperCase()
    if (SPECIES_META[p]) return p
  }
  return (bee.beeType || bee.type || 'WORKER').toUpperCase()
}
function speciesLabel(bee) { return bee ? (SPECIES_META[speciesKey(bee)]?.label || 'WORKER') : '' }
function spGlow(bee)       { return bee ? (SPECIES_META[speciesKey(bee)]?.glow || '#f9a825') : '#f9a825' }

function beeHealth(bee) {
  if (!bee) return 'idle'
  const s = (bee.status || '').toLowerCase()
  if (s === 'busy') return 'active'
  if (s === 'error' || s === 'dead') return 'error'
  return 'idle'
}
function healthText(bee) {
  const m = { active: '🍯 采蜜中', idle: '😌 休闲', error: '😿 异常' }
  return m[beeHealth(bee)] || '未知'
}
function hiveLabel(id) {
  const h = store.hives.find(h => h.hiveId === id)
  return h ? (h.name || id) : id
}

const busyCount  = computed(() => activeBees.value.filter(b => beeHealth(b) === 'active').length)
const idleCount  = computed(() => activeBees.value.filter(b => beeHealth(b) === 'idle').length)
const errorCount = computed(() => activeBees.value.filter(b => beeHealth(b) === 'error').length)

function onImgError(e) { e.target.src = '/assets/bees/worker.png' }

// ── 装饰 ───────────────────────────────────────────────────────
const clouds = [
  { id:1, e:'☁️', style:'left:8%;top:10%;animation:cloud-drift 18s ease-in-out infinite alternate;font-size:2rem;opacity:.55' },
  { id:2, e:'⛅', style:'left:40%;top:6%;animation:cloud-drift 24s ease-in-out infinite alternate-reverse;font-size:1.5rem;opacity:.45' },
  { id:3, e:'☁️', style:'left:70%;top:12%;animation:cloud-drift 20s ease-in-out infinite alternate;font-size:1.8rem;opacity:.40' },
]

function grassStyle(g) {
  return { left:`${((g-1)/27)*100}%`, animationDelay:`${(g*0.19)%2}s`, fontSize:`${0.85+(g%3)*0.15}rem` }
}
function fireflyStyle(i) {
  const s = i * 137.5
  return {
    left:`${(s*7.3)%100}%`, top:`${(s*3.7)%85}%`,
    animationDelay:`${(s*0.17)%6}s`, animationDuration:`${4+(s*0.11)%5}s`,
    '--fly-x':`${((s*2.3)%80)-40}px`, '--fly-y':`${((s*1.9)%80)-40}px`,
  }
}

// ── 工具 ───────────────────────────────────────────────────────
function rand(arr) { return arr[Math.floor(Math.random() * arr.length)] }
function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)) }
</script>

<style scoped>
/* ═══ 根节点 ════════════════════════════════════════════════ */
#panel-habitat {
  position: relative;
  overflow: hidden;
  /* background 由 :style 动态注入 */
  transition: background 4s ease;
  cursor: default;
  user-select: none;
}

/* ═══ 天空 ══════════════════════════════════════════════════ */
.sky-layer {
  position: absolute; inset: 0 0 35% 0;
  pointer-events: none; z-index: 0;
}
.sun {
  position: absolute;
  transition: left 60s linear, bottom 60s linear;
}
@keyframes sun-spin { to { transform: rotate(360deg); } }
.cloud {
  position: absolute;
  transition: opacity 3s ease;
}
@keyframes cloud-drift {
  from { transform: translateX(-12px); }
  to   { transform: translateX(12px);  }
}

/* ═══ 星星（夜晚）══════════════════════════════════════════ */
.star-layer { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 3px rgba(255,255,255,.8);
  animation: star-twinkle var(--dur, 2s) ease-in-out infinite alternate;
}
@keyframes star-twinkle {
  from { opacity: .2; transform: scale(.7); }
  to   { opacity: 1;  transform: scale(1.2); }
}

/* ═══ 天气覆盖层 ════════════════════════════════════════════ */
.weather-overlay {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 1;
  transition: background 3s ease;
}

/* ═══ 雨层 ══════════════════════════════════════════════════ */
.rain-layer { position: absolute; inset: 0; pointer-events: none; z-index: 2; overflow: hidden; }
.raindrop {
  position: absolute;
  top: -20px;
  width: 1.5px;
  background: linear-gradient(to bottom, transparent, rgba(130,175,210,0.65));
  border-radius: 2px;
  animation: rain-fall linear infinite;
}
@keyframes rain-fall {
  from { transform: translateY(-20px); }
  to   { transform: translateY(110vh); }
}

/* ═══ 雪层 ══════════════════════════════════════════════════ */
.snow-layer { position: absolute; inset: 0; pointer-events: none; z-index: 2; overflow: hidden; }
.snowflake {
  position: absolute;
  top: -30px;
  color: rgba(220,235,255,0.85);
  animation: snow-fall linear infinite;
  text-shadow: 0 0 4px rgba(180,210,255,.6);
}
@keyframes snow-fall {
  from { transform: translateY(-30px) rotate(0deg); }
  to   { transform: translateY(110vh)  rotate(360deg); }
}

/* ═══ 城市标签 ═════════════════════════════════════════════ */
.fh-city {
  font-size: .58rem;
  color: rgba(255,255,255,.65);
  background: rgba(0,0,0,.18);
  border-radius: 99px;
  padding: 1px 7px;
  backdrop-filter: blur(4px);
}
.fh-wicon { font-size: 1rem; }

/* ═══ 大地装饰 ══════════════════════════════════════════════ */
.ground-deco {
  position: absolute; inset: 0; pointer-events: none; z-index: 1;
}
.deco-item {
  position: absolute;
  animation: deco-sway 3s ease-in-out infinite alternate;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,.15));
}
@keyframes deco-sway {
  from { transform: rotate(-5deg) scale(1); }
  to   { transform: rotate(5deg)  scale(1.06); }
}
.grass-strip {
  position: absolute; bottom: 0; left: 0; right: 0;
  height: 48px; display: flex; align-items: flex-end;
  background: linear-gradient(to top, #5a9e1a 0%, transparent 100%);
}
.grass {
  position: absolute; bottom: 2px;
  animation: grass-wave 2s ease-in-out infinite alternate;
}
@keyframes grass-wave {
  from { transform: rotate(-6deg); }
  to   { transform: rotate(6deg);  }
}

/* ═══ 萤火虫 ════════════════════════════════════════════════ */
.firefly-layer { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.firefly {
  position: absolute; width: 5px; height: 5px; border-radius: 50%;
  background: radial-gradient(circle,#fffac0 0%,#f9d71c 55%,transparent 80%);
  box-shadow: 0 0 7px 3px rgba(249,215,28,.5);
  animation: ff-drift var(--fly-dur,5s) ease-in-out infinite alternate;
}
@keyframes ff-drift {
  0%   { transform: translate(0,0) scale(1); opacity:.8; }
  50%  { opacity:.2; }
  100% { transform: translate(var(--fly-x,30px),var(--fly-y,-20px)) scale(.6); opacity:.7; }
}

/* ═══ Header ════════════════════════════════════════════════ */
.field-header {
  position: absolute; top: 0; left: 0; right: 0; z-index: 10;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 20px;
  background: rgba(255,255,255,.45); backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255,255,255,.55);
}
.fh-glyph  { font-size: 1.4rem; filter: drop-shadow(0 2px 5px rgba(248,187,208,.7)); }
.fh-title  { font-size: .88rem; font-weight: 800; color: #2e5416; }
.fh-count  { font-size: .70rem; color: #6a994e; font-weight: 700;
             background: rgba(106,153,78,.15); border-radius: 99px; padding: 1px 9px; }
.fh-weather { display: flex; align-items: center; gap: 6px; margin-left: auto; margin-right: 8px; }
.fh-wtext  { font-size: .62rem; color: #3a5820; font-weight: 600; font-style: italic; }
.fh-resonate { padding: 5px 12px; font-size: .68rem; flex-shrink: 0; }

/* ═══ Footer ════════════════════════════════════════════════ */
.field-footer {
  position: absolute; bottom: 0; left: 0; right: 0; z-index: 10;
  display: flex; align-items: center; gap: 12px;
  padding: 8px 18px;
  background: rgba(255,255,255,.50); backdrop-filter: blur(12px);
  border-top: 1px solid rgba(255,255,255,.60);
}
.ff-stat { display: flex; align-items: center; gap: 4px; font-size: .68rem; color: #4a6741; }
.hsd { width: 7px; height: 7px; border-radius: 50%; }
.hsd.active { background:#43a047; box-shadow:0 0 5px #43a047; animation:led 2s ease-in-out infinite; }
.hsd.idle   { background:#90caf9; box-shadow:0 0 5px #90caf9; }
.hsd.error  { background:#ef5350; box-shadow:0 0 5px #ef5350; }
@keyframes led { 0%,100%{opacity:1;} 50%{opacity:.35;} }
.ff-motto { flex:1; text-align:center; font-size:.60rem; color:#6a994e; font-style:italic; }
.fab-btn {
  display: flex; align-items: center; gap: 4px;
  border-radius: 99px; border: none; cursor: pointer;
  font-size: .72rem; font-weight: 800; padding: 6px 14px;
  transition: transform .18s cubic-bezier(.175,.885,.32,1.275), box-shadow .18s;
  flex-shrink: 0;
}
.fab-btn:hover { transform: translateY(-2px) scale(1.06); }
.fab-resonate {
  background: linear-gradient(135deg,#e8d5ff,#c084fc);
  color: #fff; box-shadow: 0 3px 12px rgba(168,85,247,.30);
}
.fab-resonate:hover { box-shadow: 0 6px 20px rgba(168,85,247,.50); }
.fab-spawn {
  background: linear-gradient(135deg,#fff9c4,#ffe082);
  color: #4a3728; box-shadow: 0 3px 12px rgba(249,168,37,.30);
}
.fab-spawn:hover { box-shadow: 0 6px 20px rgba(249,168,37,.50); }
.egg-icon { display:inline-block; animation:egg-wobble 2s ease-in-out infinite; }
@keyframes egg-wobble { 0%,100%{transform:rotate(-8deg);}50%{transform:rotate(8deg);} }

/* ═══ 蜜蜂实体 ══════════════════════════════════════════════ */
.bee-entity {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 以中心点定位 */
  transform: translate(-50%, -50%);
  cursor: grab;
  z-index: 5;
  transition: left 1.8s cubic-bezier(.45,.05,.55,.95),
              top  1.8s cubic-bezier(.45,.05,.55,.95);
}
.bee-entity.no-transition {
  transition: none !important;
}
.bee-entity.is-selected { z-index: 20; }
.bee-entity.is-dragging { cursor: grabbing; z-index: 30; }

/* 蜜蜂图片 */
.bee-sprite {
  width: 216px; height: 216px;
  object-fit: contain;
  -webkit-user-drag: none;
  filter: drop-shadow(0 8px 10px rgba(0,0,0,.18));
  transition: transform .2s, filter .2s;
}

/* ── 状态动画 ── */
/* idle: 轻柔漂浮 */
.state-idle .bee-sprite {
  animation: float-idle 3.5s ease-in-out infinite;
}
@keyframes float-idle {
  0%,100% { transform: translateY(0) rotate(-2deg) scale(1); }
  40%     { transform: translateY(-10px) rotate(2deg) scale(1.02); }
  70%     { transform: translateY(-14px) rotate(-1deg) scale(1.025); }
}

/* wandering / resting-walk: 横向摇摆飞行 */
.state-wandering .bee-sprite,
.state-resting-walk .bee-sprite {
  animation: float-walk 1.2s ease-in-out infinite;
}
@keyframes float-walk {
  0%,100% { transform: translateY(0) rotate(-3deg) scale(1); }
  50%     { transform: translateY(-8px) rotate(3deg) scale(1.03); }
}

/* resting: 趴着睡 */
.state-resting .bee-sprite {
  animation: float-rest 5s ease-in-out infinite;
  filter: drop-shadow(0 4px 6px rgba(0,0,0,.12)) brightness(.92);
}
@keyframes float-rest {
  0%,100% { transform: translateY(0) rotate(-4deg) scale(.96); }
  50%     { transform: translateY(3px) rotate(4deg) scale(.96); }
}

/* playing: 快乐旋转跳跃 */
.state-playing .bee-sprite {
  animation: float-play 0.7s ease-in-out infinite;
}
@keyframes float-play {
  0%   { transform: translateY(0) rotate(-8deg) scale(1); }
  25%  { transform: translateY(-16px) rotate(6deg) scale(1.08); }
  50%  { transform: translateY(-20px) rotate(-4deg) scale(1.1); }
  75%  { transform: translateY(-10px) rotate(8deg) scale(1.05); }
  100% { transform: translateY(0) rotate(-8deg) scale(1); }
}

/* busy: 高速飞翔发光 */
.state-busy .bee-sprite {
  animation: float-busy 1s ease-in-out infinite;
  filter: drop-shadow(0 0 12px var(--glow, #f9a825)) drop-shadow(0 8px 8px rgba(0,0,0,.15));
}
@keyframes float-busy {
  0%,100% { transform: translateY(0) rotate(-5deg) scale(1); }
  50%     { transform: translateY(-18px) rotate(5deg) scale(1.08); }
}

/* hover 效果 */
.bee-entity:not(.is-dragging):hover .bee-sprite {
  transform: scale(1.15) translateY(-8px) !important;
  filter: drop-shadow(0 0 16px var(--glow, #f9a825)) drop-shadow(0 12px 10px rgba(0,0,0,.12)) !important;
  animation-play-state: paused !important;
}

/* 孵化动画 */
.bee-entering .bee-sprite {
  animation: bee-hatch .8s cubic-bezier(.175,.885,.32,1.275) both !important;
}
@keyframes bee-hatch {
  0%   { transform: scale(0) rotate(-20deg); opacity: 0; }
  65%  { transform: scale(1.2) rotate(6deg); opacity: 1; }
  85%  { transform: scale(.9); }
  100% { transform: scale(1) rotate(0); }
}

/* ── 影子 ── */
.bee-shadow {
  width: 48px; height: 10px; border-radius: 50%;
  background: radial-gradient(ellipse, rgba(0,0,0,.22) 0%, transparent 70%);
  margin-top: -6px;
  transition: transform .3s, opacity .3s;
}
.state-idle       .bee-shadow { transform: scaleX(1);    opacity: .7; }
.state-wandering  .bee-shadow,
.state-resting-walk .bee-shadow { transform: scaleX(.8); opacity: .5; }
.state-resting    .bee-shadow { transform: scaleX(1.1);  opacity: .85; }
.state-playing    .bee-shadow { transform: scaleX(.6);   opacity: .3; }
.state-busy       .bee-shadow { transform: scaleX(.5);   opacity: .25; }

/* ── 对话气泡 ── */
.speech-bubble {
  position: absolute;
  bottom: calc(100% - 8px);
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255,255,255,.92);
  backdrop-filter: blur(10px);
  border: 1.5px solid rgba(255,255,255,.9);
  border-radius: 16px 16px 16px 4px;
  padding: 7px 12px;
  font-size: .70rem;
  font-weight: 600;
  color: #3a3028;
  white-space: nowrap;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: 0 4px 18px rgba(0,0,0,.12);
  z-index: 20;
  pointer-events: none;
}
.speech-bubble::after {
  content: '';
  position: absolute;
  bottom: -7px; left: 16px;
  border: 5px solid transparent;
  border-top-color: rgba(255,255,255,.92);
}

.bubble-pop-enter-active { animation: bubble-in .25s cubic-bezier(.175,.885,.32,1.275); }
.bubble-pop-leave-active { animation: bubble-in .2s ease-in reverse; }
@keyframes bubble-in {
  from { opacity: 0; transform: translateX(-50%) scale(.7) translateY(8px); }
  to   { opacity: 1; transform: translateX(-50%) scale(1) translateY(0); }
}

/* ── 状态角标 ── */
.bee-state-badge {
  position: absolute;
  top: -4px; right: -4px;
  font-size: .85rem;
  filter: drop-shadow(0 1px 3px rgba(0,0,0,.2));
  pointer-events: none;
}

/* ── 名牌 ── */
.bee-nametag {
  display: flex; align-items: center; gap: 4px;
  margin-top: 4px;
  background: rgba(255,255,255,.80);
  backdrop-filter: blur(8px);
  border-radius: 99px; padding: 2px 9px;
  box-shadow: 0 2px 8px rgba(0,0,0,.10);
  pointer-events: none;
}
.nametag-dot {
  width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0;
}
.dot-active { background: #43a047; box-shadow: 0 0 4px #43a047; animation: led 2s ease-in-out infinite; }
.dot-idle   { background: #90caf9; }
.dot-error  { background: #ef5350; }
.nametag-text {
  font-size: .58rem; font-weight: 800; color: #3a5a28;
  max-width: 72px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ═══ 空状态 ══════════════════════════════════════════════ */
.field-empty {
  position: absolute; inset: 0; z-index: 8;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  pointer-events: none;
}
.field-empty-inner { display: flex; flex-direction: column; align-items: center; gap: 10px; pointer-events: all; }
.field-empty-egg  { font-size: 3rem; animation: egg-wobble 2.5s ease-in-out infinite; }
.field-empty-title { font-size: 1rem; font-weight: 800; color: #2e5416; }
.field-empty-sub   { font-size: .75rem; color: #6a994e; }
.field-empty-btn {
  background: linear-gradient(135deg,#fff9c4,#ffe082);
  border: 1.5px solid rgba(249,168,37,.55); border-radius: 99px;
  padding: 8px 22px; font-size: .78rem; font-weight: 800; color: #4a3728;
  cursor: pointer; box-shadow: 0 4px 14px rgba(249,168,37,.35);
  transition: transform .2s, box-shadow .2s;
  pointer-events: all;
}
.field-empty-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(249,168,37,.55); }
.field-fade-enter-active, .field-fade-leave-active { transition: opacity .35s; }
.field-fade-enter-from, .field-fade-leave-to { opacity: 0; }

/* ═══ 详情弹窗 ══════════════════════════════════════════════ */
.modal-overlay {
  position: fixed; inset: 0; z-index: 999;
  background: rgba(0,0,0,.32); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
}
.modal-card {
  position: relative; width: 290px; max-width: 90vw;
  background: rgba(255,255,255,.78);
  border: 1.5px solid rgba(255,255,255,.90);
  border-radius: 28px; backdrop-filter: blur(28px);
  box-shadow: 0 24px 60px rgba(0,0,0,.18), 0 0 40px color-mix(in srgb, var(--sp-glow,#f9a825) 18%, transparent);
  padding: 28px 24px 22px;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
}
.modal-close {
  position: absolute; top: 14px; right: 16px;
  background: rgba(0,0,0,.06); border: none; border-radius: 50%;
  width: 28px; height: 28px; font-size: .8rem; cursor: pointer; color: #8d6e63;
  display: flex; align-items: center; justify-content: center; transition: background .15s;
}
.modal-close:hover { background: rgba(0,0,0,.12); }
.modal-img-wrap {
  position: relative; width: 100px; height: 100px;
  display: flex; align-items: center; justify-content: center;
}
.modal-bee-img {
  width: 100px; height: 100px; object-fit: contain; position: relative; z-index: 1;
  animation: modal-float 3s ease-in-out infinite;
  filter: drop-shadow(0 10px 8px rgba(0,0,0,.14));
}
@keyframes modal-float {
  0%,100% { transform: translateY(0) rotate(-2deg); }
  50%     { transform: translateY(-10px) rotate(2deg); }
}
.modal-glow-ring {
  position: absolute; inset: -8px; border-radius: 50%;
  background: radial-gradient(circle, var(--sp-glow,#f9a825) 0%, transparent 70%);
  opacity: .22; animation: ring-pulse 2.5s ease-in-out infinite;
}
@keyframes ring-pulse {
  0%,100%{opacity:.18;transform:scale(1);}50%{opacity:.32;transform:scale(1.12);}
}
.modal-name { font-size: 1.05rem; font-weight: 800; color: #4a3728; }
.modal-tag  {
  font-size: .66rem; font-weight: 700; padding: 2px 12px;
  background: color-mix(in srgb, var(--sp-glow,#f9a825) 15%, transparent);
  color: var(--sp-glow,#f9a825);
  border: 1px solid color-mix(in srgb, var(--sp-glow,#f9a825) 30%, transparent);
  border-radius: 99px; letter-spacing: .08em;
}
.modal-info-grid { width: 100%; display: flex; flex-direction: column; gap: 5px; }
.mig-row { display: flex; justify-content: space-between; align-items: center; font-size: .72rem; }
.mig-k   { color: #a1887f; font-weight: 600; }
.mig-v   { color: #4a3728; font-weight: 700; }
.mig-v.mono { font-family: monospace; }
.sv-active { color: #e65100; }
.sv-error  { color: #9e9e9e; }
.modal-actions { display: flex; gap: 8px; width: 100%; margin-top: 4px; }
.modal-btn {
  flex: 1; padding: 9px 0; border-radius: 14px; border: none;
  font-size: .76rem; font-weight: 800; cursor: pointer; letter-spacing: .04em;
  transition: transform .18s cubic-bezier(.175,.885,.32,1.275), box-shadow .18s;
}
.modal-btn:hover { transform: translateY(-2px) scale(1.04); }
.chat-btn { background: linear-gradient(135deg,#f48fb1,#f06292); color:#fff; box-shadow: 0 4px 14px rgba(244,143,177,.40); }
.chat-btn:hover { box-shadow: 0 6px 20px rgba(244,143,177,.60); }
.will-btn { background: linear-gradient(135deg,#ffe082,#f9a825); color:#4a3728; box-shadow: 0 4px 14px rgba(249,168,37,.35); }
.will-btn:hover { box-shadow: 0 6px 20px rgba(249,168,37,.55); }
.modal-fade-enter-active { animation: modal-in .32s cubic-bezier(.175,.885,.32,1.275); }
.modal-fade-leave-active { animation: modal-in .22s ease-in reverse; }
@keyframes modal-in {
  from { opacity:0; transform:scale(.80) translateY(20px); }
  to   { opacity:1; transform:scale(1) translateY(0); }
}
</style>
