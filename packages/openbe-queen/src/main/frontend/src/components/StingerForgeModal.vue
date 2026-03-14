<template>
  <Teleport to="body">
    <Transition name="forge-fade">
      <div v-if="visible" class="forge-overlay" @click.self="$emit('close')">
        <div class="forge-card">

          <!-- ── 头部 ── -->
          <div class="forge-header">
            <div class="forge-header-left">
              <span class="forge-glyph">⚡</span>
              <div>
                <div class="forge-title">蜂刺神兵</div>
                <div class="forge-sub">锻造 · 管理 · 在线编辑</div>
              </div>
            </div>
            <!-- 标签切换 -->
            <div class="forge-tabs">
              <button class="forge-tab" :class="{ active: tab === 'forge' }" @click="tab = 'forge'">
                ⚒️ 锻造台
              </button>
              <button class="forge-tab" :class="{ active: tab === 'manage' }" @click="tab = 'manage'; loadAllStingers()">
                🗂 神兵库
              </button>
            </div>
            <button class="forge-close" @click="$emit('close')">✕</button>
          </div>

          <!-- ════════ TAB：锻造台 ════════ -->
          <div v-show="tab === 'forge'" class="forge-body">

            <!-- LEFT: 编辑器 -->
            <div class="forge-left">
              <div class="forge-field">
                <label class="forge-label">蜂刺名称</label>
                <input class="forge-input" v-model="form.name" placeholder="my_stinger" maxlength="32" :disabled="forging"/>
              </div>
              <div class="forge-row">
                <div class="forge-field" style="flex:0 0 110px">
                  <label class="forge-label">图标</label>
                  <div class="forge-icon-picker">
                    <button v-for="ic in ICONS" :key="ic" class="forge-icon-btn" :class="{ selected: form.icon === ic }" @click="form.icon = ic">{{ ic }}</button>
                  </div>
                </div>
                <div class="forge-field" style="flex:1">
                  <label class="forge-label">稀有度</label>
                  <div class="rarity-tabs">
                    <button v-for="r in RARITIES" :key="r.val" class="rarity-tab" :class="[`rtab-${r.val}`, { selected: form.rarity === r.val }]" @click="form.rarity = r.val">{{ r.label }}</button>
                  </div>
                </div>
              </div>
              <div class="forge-field">
                <label class="forge-label">描述</label>
                <input class="forge-input" v-model="form.description" placeholder="这只蜂刺能做什么…" :disabled="forging"/>
              </div>
              <div class="forge-field forge-field-grow">
                <div class="forge-code-bar">
                  <label class="forge-label" style="margin:0">Shell 脚本代码</label>
                  <span class="forge-code-hint">.sh · bash</span>
                  <button class="forge-tpl-btn" @click="insertTemplate">插入模板</button>
                </div>
                <textarea class="forge-code" v-model="form.code" placeholder="#!/bin/bash&#10;echo 'Hello from my stinger!'" spellcheck="false" :disabled="forging"></textarea>
              </div>
            </div>

            <!-- RIGHT: 预览 & 锻造 -->
            <div class="forge-right">
              <div class="stinger-preview" :class="`preview-${form.rarity}`">
                <div class="sp-aura" :class="`aura-${form.rarity}`"></div>
                <div class="sp-icon-wrap" :class="{ forging: forging }">
                  <div class="sp-icon">{{ form.icon || '⚡' }}</div>
                  <div v-if="forging" class="spark-ring">
                    <span v-for="s in 8" :key="s" class="spark" :style="sparkStyle(s)">✦</span>
                  </div>
                  <div v-if="forged" class="forge-burst">
                    <span v-for="s in 10" :key="s" class="burst-p" :style="burstStyle(s)">⚡</span>
                  </div>
                </div>
                <div class="sp-name">{{ form.name || '未命名蜂刺' }}</div>
                <div class="sp-desc">{{ form.description || '等待描述…' }}</div>
                <div class="sp-rarity-badge" :class="`badge-${form.rarity}`">{{ rarityLabel(form.rarity) }}</div>
              </div>
              <div class="forge-status" :class="statusClass">{{ statusText }}</div>
              <div class="forged-list" v-if="forgedStingers.length">
                <div class="forged-list-hd">✨ 已锻造</div>
                <div v-for="s in forgedStingers" :key="s.id" class="forged-item" :class="`fi-${s.rarity}`">
                  <span class="fi-icon">{{ s.icon }}</span>
                  <span class="fi-name">{{ s.name }}</span>
                  <button class="fi-del" @click="deleteStinger(s)" title="删除">✕</button>
                </div>
              </div>
              <button class="forge-btn" :class="{ 'forge-btn-active': forging, 'forge-btn-done': forged }" :disabled="!canForge || forging" @click="forge">
                <span class="forge-heart" :class="{ beat: forging || forged }">{{ forged ? '✅' : forging ? '⚒️' : '💛' }}</span>
                <span>{{ forged ? '锻造成功！' : forging ? '锻造中…' : '锻造蜂刺' }}</span>
              </button>
            </div>
          </div>

          <!-- ════════ TAB：神兵库（管理） ════════ -->
          <div v-show="tab === 'manage'" class="forge-body manage-body">

            <!-- LEFT: 蜂刺列表 -->
            <div class="manage-list-panel">
              <div class="manage-search-wrap">
                <input class="manage-search" v-model="search" placeholder="🔍 搜索蜂刺…" />
              </div>
              <div class="manage-list">
                <div
                  v-for="s in filteredStingers"
                  :key="s.id"
                  class="manage-item"
                  :class="{ active: editTarget?.id === s.id, 'item-dirty': editTarget?.id === s.id && isDirty }"
                  @click="selectStinger(s)"
                >
                  <span class="mi-icon">{{ s.icon || '⚡' }}</span>
                  <div class="mi-info">
                    <div class="mi-name">{{ s.name || s.id }}</div>
                    <div class="mi-file">{{ s.id }}</div>
                  </div>
                  <span class="mi-rarity" :class="`mr-${s.rarity || 'blue'}`">{{ rarityShort(s.rarity) }}</span>
                </div>
                <div v-if="filteredStingers.length === 0" class="manage-empty">
                  {{ allStingers.length === 0 ? '蜂刺库为空' : '未找到匹配蜂刺' }}
                </div>
              </div>
              <div class="manage-list-footer">共 {{ allStingers.length }} 只蜂刺</div>
            </div>

            <!-- RIGHT: 编辑面板 -->
            <div class="manage-editor-panel">
              <!-- 空态 -->
              <div v-if="!editTarget" class="manage-placeholder">
                <div class="mp-icon">🗡</div>
                <div class="mp-text">从左侧选择一只蜂刺进行编辑</div>
              </div>

              <!-- 编辑器 -->
              <template v-else>
                <div class="manage-editor-header">
                  <div class="meh-icon">{{ editMeta.icon || '⚡' }}</div>
                  <div class="meh-info">
                    <div class="meh-name">{{ editTarget.name || editTarget.id }}</div>
                    <div class="meh-file">{{ editTarget.id }}</div>
                  </div>
                  <div v-if="isDirty" class="dirty-badge">● 未保存</div>
                  <button class="meh-del" @click="confirmDelete" title="删除此蜂刺">🗑 删除</button>
                </div>

                <!-- 元数据行 -->
                <div class="meta-row">
                  <div class="meta-field">
                    <label class="forge-label">描述</label>
                    <input class="forge-input" v-model="editMeta.description" placeholder="蜂刺的功能描述…" @input="isDirty = true"/>
                  </div>
                  <div class="meta-field" style="flex:0 0 auto">
                    <label class="forge-label">图标</label>
                    <div class="forge-icon-picker compact">
                      <button v-for="ic in ICONS" :key="ic" class="forge-icon-btn" :class="{ selected: editMeta.icon === ic }" @click="editMeta.icon = ic; isDirty = true">{{ ic }}</button>
                    </div>
                  </div>
                  <div class="meta-field" style="flex:0 0 auto">
                    <label class="forge-label">稀有度</label>
                    <div class="rarity-tabs">
                      <button v-for="r in RARITIES" :key="r.val" class="rarity-tab" :class="[`rtab-${r.val}`, { selected: editMeta.rarity === r.val }]" @click="editMeta.rarity = r.val; isDirty = true">{{ r.label }}</button>
                    </div>
                  </div>
                </div>

                <!-- 代码编辑器 -->
                <div class="manage-code-wrap">
                  <div class="forge-code-bar">
                    <label class="forge-label" style="margin:0">脚本代码</label>
                    <span class="forge-code-hint">{{ editTarget.id }}</span>
                    <span v-if="loadingCode" class="code-loading">加载中…</span>
                  </div>
                  <textarea
                    class="forge-code manage-code"
                    v-model="editCode"
                    spellcheck="false"
                    :placeholder="loadingCode ? '加载中…' : '#!/bin/bash\n# 编辑你的脚本…'"
                    @input="isDirty = true"
                    @keydown.tab.prevent="insertTab"
                  ></textarea>
                </div>

                <!-- 操作栏 -->
                <div class="manage-actions">
                  <div class="save-msg" :class="{ ok: saveMsg.ok, err: !saveMsg.ok }">{{ saveMsg.text }}</div>
                  <button class="manage-btn save-btn" :class="{ active: saving }" :disabled="!isDirty || saving" @click="saveEdits">
                    {{ saving ? '保存中…' : '💾 保存修改' }}
                  </button>
                </div>
              </template>
            </div>

          </div><!-- end manage tab -->

          <!-- 删除确认弹窗 -->
          <Transition name="del-confirm">
            <div v-if="delConfirm.visible" class="del-overlay" @click.self="delConfirm.visible = false">
              <div class="del-card">
                <div class="del-icon">🗑</div>
                <div class="del-title">删除蜂刺</div>
                <div class="del-msg">确认删除「<b>{{ editTarget?.name || editTarget?.id }}</b>」？此操作不可撤销。</div>
                <div class="del-actions">
                  <button class="del-btn cancel" @click="delConfirm.visible = false">取消</button>
                  <button class="del-btn confirm" @click="doDelete">确认删除</button>
                </div>
              </div>
            </div>
          </Transition>

        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, reactive } from 'vue'
import { useApi } from '../composables/useApi.js'

const props = defineProps({ visible: Boolean })
const emit  = defineEmits(['close', 'forged'])

const api = useApi()

const ICONS    = ['⚡','🔧','🔍','📬','🎬','🔔','📄','🗂','🌐','💊','🛡','📊','🤖','🎨','🔑']
const RARITIES = [
  { val: 'blue',   label: '基础' },
  { val: 'purple', label: '史诗' },
  { val: 'gold',   label: '传说' },
]

// ── 标签 ──────────────────────────────────────────────────────────
const tab = ref('forge')

// ── 锻造台 ────────────────────────────────────────────────────────
const form = ref({ name: '', description: '', code: '', icon: '⚡', rarity: 'blue' })
const forging  = ref(false)
const forged   = ref(false)
const statusMsg  = ref('')
const statusType = ref('')
const forgedStingers = ref([])

const statusText  = computed(() => statusMsg.value || '编写你的蜂刺脚本，然后点击锻造')
const statusClass = computed(() => ({ 'status-ok': statusType.value === 'ok', 'status-err': statusType.value === 'err' }))
const canForge    = computed(() => form.value.name.trim() && form.value.code.trim())

function rarityLabel(r) {
  return { blue: '◆ 基础', purple: '◆ 史诗', gold: '◆ 传说' }[r] || r
}
function rarityShort(r) {
  return { blue: '基', purple: '诗', gold: '传' }[r] || '基'
}

function insertTemplate() {
  form.value.code = `#!/bin/bash
# 蜂刺: ${form.value.name || 'my_stinger'}
# 参数说明：$1 = 第一个参数, $2 = 第二个参数

INPUT="${'$'}{1:-默认值}"

echo "▶ 蜂刺开始执行"
echo "输入参数: $INPUT"

# 在此处编写你的逻辑

echo "✅ 执行完成"
`
}

function sparkStyle(i) {
  const angle = (i / 8) * 360
  const r = 52
  return {
    '--sx': `${Math.cos((angle * Math.PI) / 180) * r}px`,
    '--sy': `${Math.sin((angle * Math.PI) / 180) * r}px`,
    animationDelay: `${(i * 0.08).toFixed(2)}s`,
  }
}
function burstStyle(i) {
  const angle = (i / 10) * 360
  const r = 60 + Math.random() * 20
  return {
    '--bx': `${Math.cos((angle * Math.PI) / 180) * r}px`,
    '--by': `${Math.sin((angle * Math.PI) / 180) * r}px`,
    animationDelay: `${(i * 0.05).toFixed(2)}s`,
  }
}

async function loadForgedStingers() {
  try {
    const all = await api.getStingers()
    forgedStingers.value = all.filter(s => s.forged)
  } catch {}
}

async function forge() {
  if (!canForge.value) return
  forging.value = true; forged.value = false
  statusMsg.value = '熔炉点火，开始锻造…'; statusType.value = ''
  try {
    await new Promise(r => setTimeout(r, 900))
    await api.forgeStinger({
      name: form.value.name.trim(), description: form.value.description.trim(),
      code: form.value.code.trim(), icon: form.value.icon, rarity: form.value.rarity,
    })
    forged.value = true
    statusMsg.value = `「${form.value.name}」锻造成功！已加入蜂刺库`
    statusType.value = 'ok'
    await loadForgedStingers()
    emit('forged')
    setTimeout(() => { forged.value = false }, 2500)
  } catch (e) {
    statusMsg.value = `锻造失败: ${e.message}`; statusType.value = 'err'
  } finally {
    forging.value = false
  }
}

async function deleteStinger(s) {
  try {
    await api.deleteStinger(s.id)
    await loadForgedStingers()
    emit('forged')
  } catch (e) {
    statusMsg.value = `删除失败: ${e.message}`; statusType.value = 'err'
  }
}

// ── 神兵库（管理）────────────────────────────────────────────────
const allStingers  = ref([])
const search       = ref('')
const editTarget   = ref(null)   // 当前选中的蜂刺对象
const editCode     = ref('')
const editMeta     = reactive({ description: '', icon: '⚡', rarity: 'blue' })
const isDirty      = ref(false)
const loadingCode  = ref(false)
const saving       = ref(false)
const saveMsg      = reactive({ text: '', ok: true })
const delConfirm   = reactive({ visible: false })

const filteredStingers = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return allStingers.value
  return allStingers.value.filter(s =>
    (s.name || '').toLowerCase().includes(q) || s.id.toLowerCase().includes(q)
  )
})

async function loadAllStingers() {
  try {
    allStingers.value = await api.getStingers()
  } catch {}
}

async function selectStinger(s) {
  if (editTarget.value?.id === s.id) return
  if (isDirty.value) {
    if (!confirm('当前有未保存的修改，确认切换？')) return
  }
  editTarget.value = s
  isDirty.value    = false
  saveMsg.text     = ''
  editCode.value   = ''
  editMeta.description = s.description || ''
  editMeta.icon        = s.icon || '⚡'
  editMeta.rarity      = s.rarity || 'blue'

  loadingCode.value = true
  try {
    const res = await api.getStingerCode(s.id)
    editCode.value = res.code || ''
    // 从 meta sidecar 补充字段
    if (res.meta) {
      if (res.meta.description) editMeta.description = res.meta.description
      if (res.meta.icon)        editMeta.icon        = res.meta.icon
      if (res.meta.rarity)      editMeta.rarity      = res.meta.rarity
    }
  } catch {
    editCode.value = '# 加载失败，请重试'
  } finally {
    loadingCode.value = false
  }
}

async function saveEdits() {
  if (!editTarget.value || !isDirty.value) return
  saving.value = true
  saveMsg.text = ''
  try {
    await api.updateStinger(editTarget.value.id, {
      code:        editCode.value,
      description: editMeta.description,
      icon:        editMeta.icon,
      rarity:      editMeta.rarity,
    })
    isDirty.value = false
    saveMsg.text = '✅ 已保存'; saveMsg.ok = true
    // 更新列表中对应条目
    const idx = allStingers.value.findIndex(s => s.id === editTarget.value.id)
    if (idx !== -1) {
      allStingers.value[idx] = {
        ...allStingers.value[idx],
        description: editMeta.description,
        icon:        editMeta.icon,
        rarity:      editMeta.rarity,
      }
    }
    emit('forged')
    setTimeout(() => { saveMsg.text = '' }, 3000)
  } catch (e) {
    saveMsg.text = `保存失败: ${e.message}`; saveMsg.ok = false
  } finally {
    saving.value = false
  }
}

function confirmDelete() { delConfirm.visible = true }

async function doDelete() {
  delConfirm.visible = false
  try {
    await api.deleteStinger(editTarget.value.id)
    allStingers.value = allStingers.value.filter(s => s.id !== editTarget.value.id)
    editTarget.value = null; isDirty.value = false
    await loadForgedStingers()
    emit('forged')
  } catch (e) {
    saveMsg.text = `删除失败: ${e.message}`; saveMsg.ok = false
  }
}

// Tab 键插入四个空格
function insertTab(e) {
  const ta = e.target
  const start = ta.selectionStart, end = ta.selectionEnd
  editCode.value = editCode.value.substring(0, start) + '    ' + editCode.value.substring(end)
  isDirty.value = true
  setTimeout(() => { ta.selectionStart = ta.selectionEnd = start + 4 }, 0)
}

watch(() => props.visible, v => {
  if (v) {
    loadForgedStingers()
    statusMsg.value = ''; statusType.value = ''
    if (tab.value === 'manage') loadAllStingers()
  }
})
</script>

<style scoped>
/* ── 遮罩 ── */
.forge-overlay {
  position: fixed; inset: 0; z-index: 1100;
  background: rgba(0,0,0,.38); backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}

/* ── 主卡片 ── */
.forge-card {
  width: 980px; max-width: 97vw; height: 90vh; max-height: 740px;
  background: rgba(255,253,248,.94);
  border: 1.5px solid rgba(245,166,35,.30);
  border-radius: 28px; backdrop-filter: blur(32px);
  box-shadow: 0 32px 80px rgba(0,0,0,.22), 0 0 0 1px rgba(255,255,255,.45);
  display: flex; flex-direction: column; overflow: hidden;
}

/* ── 头部 ── */
.forge-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 22px 12px;
  background: rgba(255,249,235,.85);
  border-bottom: 1px solid rgba(245,166,35,.18);
  flex-shrink: 0;
}
.forge-header-left { display: flex; align-items: center; gap: 12px; }
.forge-glyph  { font-size: 1.6rem; filter: drop-shadow(0 2px 6px rgba(245,166,35,.6)); }
.forge-title  { font-size: .90rem; font-weight: 800; color: #2C2724; letter-spacing: .04em; }
.forge-sub    { font-size: .62rem; color: #B5A898; margin-top: 1px; }

/* 标签切换 */
.forge-tabs { display: flex; gap: 4px; margin-left: auto; }
.forge-tab {
  padding: 6px 14px; border-radius: 10px; border: 1.5px solid rgba(245,166,35,.20);
  background: rgba(255,255,255,.50); font-size: .74rem; font-weight: 700; color: #B5A898;
  cursor: pointer; transition: all .15s;
}
.forge-tab.active {
  background: linear-gradient(135deg,#ffe082,#F59E0B);
  color: #1a0c00; border-color: transparent;
  box-shadow: 0 2px 10px rgba(245,158,11,.35);
}
.forge-tab:not(.active):hover { border-color: rgba(245,166,35,.40); color: #7A6A5A; }

.forge-close {
  background: rgba(0,0,0,.06); border: none; border-radius: 50%;
  width: 28px; height: 28px; cursor: pointer; font-size: .8rem; color: #8d6e63;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.forge-close:hover { background: rgba(0,0,0,.12); }

/* ── 主体 ── */
.forge-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }

/* ── 锻造台左侧 ── */
.forge-left {
  flex: 1; min-width: 0; padding: 18px 20px;
  display: flex; flex-direction: column; gap: 12px;
  border-right: 1px solid rgba(245,166,35,.15); overflow-y: auto;
}
.forge-left::-webkit-scrollbar { width: 3px; }
.forge-left::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); border-radius: 3px; }

.forge-field { display: flex; flex-direction: column; gap: 5px; }
.forge-field-grow { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.forge-label { font-size: .70rem; font-weight: 700; color: #7A6A5A; }
.forge-input {
  background: rgba(255,255,255,.75); border: 1.5px solid rgba(245,166,35,.25);
  border-radius: 10px; padding: 8px 12px;
  font-size: .82rem; color: #2C2724; outline: none; font-family: inherit;
  transition: border-color .15s;
}
.forge-input:focus { border-color: rgba(245,166,35,.60); }
.forge-row { display: flex; gap: 12px; }

/* 图标选择器 */
.forge-icon-picker { display: flex; flex-wrap: wrap; gap: 5px; }
.forge-icon-picker.compact { gap: 3px; }
.forge-icon-btn {
  width: 30px; height: 30px; border-radius: 8px; border: 1.5px solid rgba(245,166,35,.18);
  background: rgba(255,255,255,.60); font-size: .9rem;
  cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all .15s;
}
.forge-icon-btn.selected { border-color: #F59E0B; background: rgba(245,158,11,.15); transform: scale(1.1); }
.forge-icon-btn:hover:not(.selected) { background: rgba(245,166,35,.08); border-color: rgba(245,166,35,.35); }

/* 稀有度 */
.rarity-tabs { display: flex; gap: 6px; }
.rarity-tab {
  flex: 1; padding: 6px 0; border-radius: 8px; border: 1.5px solid transparent;
  font-size: .74rem; font-weight: 700; cursor: pointer; transition: all .15s;
  background: rgba(255,255,255,.60);
}
.rtab-blue   { color: #3B82F6; }
.rtab-purple { color: #A855F7; }
.rtab-gold   { color: #D97706; }
.rtab-blue.selected   { border-color: #3B82F6; background: rgba(59,130,246,.10); }
.rtab-purple.selected { border-color: #A855F7; background: rgba(168,85,247,.10); }
.rtab-gold.selected   { border-color: #F59E0B; background: rgba(245,158,11,.12); }

/* 代码区 */
.forge-code-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.forge-code-hint { font-size: .62rem; color: #B5A898; font-family: monospace; }
.forge-tpl-btn {
  margin-left: auto; font-size: .64rem; font-weight: 700; color: #D97706;
  background: rgba(245,166,35,.10); border: 1px solid rgba(245,166,35,.25);
  border-radius: 6px; padding: 2px 8px; cursor: pointer; transition: background .15s;
}
.forge-tpl-btn:hover { background: rgba(245,166,35,.20); }
.forge-code {
  flex: 1; min-height: 180px; resize: none;
  background: rgba(20,18,16,.88); color: #e8d5b0;
  border: 1.5px solid rgba(245,166,35,.20);
  border-radius: 12px; padding: 12px 14px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: .76rem; line-height: 1.7; outline: none; transition: border-color .15s;
}
.forge-code:focus { border-color: rgba(245,166,35,.50); }
.forge-code::placeholder { color: rgba(232,213,176,.30); }

/* ── 锻造台右侧 ── */
.forge-right {
  width: 280px; flex-shrink: 0; padding: 20px 18px;
  display: flex; flex-direction: column; gap: 14px; align-items: center;
  background: rgba(255,249,235,.40); overflow-y: auto;
}
.forge-right::-webkit-scrollbar { width: 3px; }
.forge-right::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); border-radius: 3px; }

/* 预览卡 */
.stinger-preview {
  width: 100%; border-radius: 20px; padding: 22px 16px;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  position: relative; overflow: hidden; border: 1.5px solid; transition: box-shadow .3s;
}
.preview-blue   { background: rgba(59,130,246,.08);  border-color: rgba(59,130,246,.30); }
.preview-purple { background: rgba(168,85,247,.08);  border-color: rgba(168,85,247,.30); }
.preview-gold   { background: rgba(245,158,11,.10);  border-color: rgba(245,158,11,.40); box-shadow: 0 0 30px rgba(245,158,11,.18); }

.sp-aura { position: absolute; inset: 0; pointer-events: none; animation: aura-breathe 3s ease-in-out infinite; }
.aura-blue   { background: radial-gradient(circle at center, rgba(59,130,246,.10) 0%, transparent 65%); }
.aura-purple { background: radial-gradient(circle at center, rgba(168,85,247,.10) 0%, transparent 65%); }
.aura-gold   { background: radial-gradient(circle at center, rgba(245,158,11,.15) 0%, transparent 65%); }
@keyframes aura-breathe { 0%,100% { opacity:.6; } 50% { opacity:1; } }

.sp-icon-wrap { position: relative; width: 80px; height: 80px; display: flex; align-items: center; justify-content: center; }
.sp-icon { font-size: 2.4rem; z-index: 1; filter: drop-shadow(0 4px 10px rgba(0,0,0,.15)); animation: icon-float 4s ease-in-out infinite; }
@keyframes icon-float { 0%,100% { transform: translateY(0) scale(1); } 50% { transform: translateY(-7px) scale(1.06); } }
.sp-icon-wrap.forging .sp-icon { animation: icon-forge .4s ease-in-out infinite alternate; }
@keyframes icon-forge { from { transform: translateY(0) rotate(-8deg) scale(.95); } to { transform: translateY(-8px) rotate(8deg) scale(1.08); } }
.sp-name { font-size: .82rem; font-weight: 800; color: #2C2724; text-align: center; z-index: 1; }
.sp-desc { font-size: .66rem; color: #B5A898; text-align: center; z-index: 1; max-width: 90%; line-height: 1.5; }
.sp-rarity-badge { font-size: .64rem; font-weight: 700; padding: 2px 10px; border-radius: 99px; z-index: 1; }
.badge-blue   { background: rgba(59,130,246,.12);  color: #3B82F6; border: 1px solid rgba(59,130,246,.25); }
.badge-purple { background: rgba(168,85,247,.12);  color: #A855F7; border: 1px solid rgba(168,85,247,.25); }
.badge-gold   { background: rgba(245,158,11,.15);  color: #D97706; border: 1px solid rgba(245,158,11,.30); }

/* 火花 & 爆发 */
.spark-ring { position: absolute; inset: 0; pointer-events: none; }
.spark { position: absolute; top: 50%; left: 50%; font-size: .7rem; color: #F59E0B; animation: spark-shoot .6s ease-out infinite; }
@keyframes spark-shoot { 0% { opacity:1; transform: translate(-50%,-50%) scale(.6); } 100% { opacity:0; transform: translate(calc(-50% + var(--sx)), calc(-50% + var(--sy))) scale(1.2); } }
.forge-burst { position: absolute; inset: 0; pointer-events: none; }
.burst-p { position: absolute; top: 50%; left: 50%; font-size: .8rem; animation: burst-out .7s cubic-bezier(.17,.67,.4,1.2) forwards; }
@keyframes burst-out { 0% { opacity:1; transform: translate(-50%,-50%) scale(.4); } 100% { opacity:0; transform: translate(calc(-50% + var(--bx)), calc(-50% + var(--by))) scale(1.3); } }

/* 状态 */
.forge-status {
  font-size: .72rem; color: #B5A898; text-align: center;
  padding: 6px 12px; border-radius: 8px; background: rgba(255,255,255,.50);
  border: 1px solid rgba(245,166,35,.12); min-height: 32px;
  display: flex; align-items: center; justify-content: center; transition: all .2s; width: 100%;
}
.status-ok  { color: #16a34a !important; border-color: rgba(22,163,74,.25) !important; background: rgba(22,163,74,.08) !important; }
.status-err { color: #DC2626 !important; border-color: rgba(220,38,38,.25) !important; background: rgba(220,38,38,.06) !important; }

/* 已锻造列表 */
.forged-list { width: 100%; }
.forged-list-hd { font-size: .66rem; font-weight: 700; color: #B5A898; margin-bottom: 5px; }
.forged-item { display: flex; align-items: center; gap: 6px; padding: 5px 8px; border-radius: 8px; margin-bottom: 4px; background: rgba(255,255,255,.60); border: 1px solid rgba(245,166,35,.12); }
.fi-icon { font-size: .9rem; }
.fi-name { flex: 1; font-size: .72rem; font-weight: 600; color: #2C2724; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fi-gold .fi-name { color: #D97706; }
.fi-purple .fi-name { color: #A855F7; }
.fi-blue .fi-name { color: #3B82F6; }
.fi-del { background: none; border: none; color: rgba(161,136,127,.50); cursor: pointer; font-size: .65rem; padding: 2px 4px; border-radius: 4px; flex-shrink: 0; }
.fi-del:hover { color: #DC2626; background: rgba(220,38,38,.08); }

/* 锻造按钮 */
.forge-btn {
  width: 100%; padding: 13px 0; border-radius: 16px; border: none;
  background: linear-gradient(135deg, #ffe082, #F59E0B, #D97706);
  color: #1a0c00; font-size: .88rem; font-weight: 800; cursor: pointer; letter-spacing: .05em;
  box-shadow: 0 6px 22px rgba(245,158,11,.45), inset 0 1px 0 rgba(255,255,255,.3);
  display: flex; align-items: center; justify-content: center; gap: 8px;
  transition: transform .2s cubic-bezier(.175,.885,.32,1.275), box-shadow .2s; flex-shrink: 0;
}
.forge-btn:not(:disabled):hover { transform: translateY(-3px); box-shadow: 0 10px 36px rgba(245,158,11,.65), inset 0 1px 0 rgba(255,255,255,.3); }
.forge-btn:disabled { opacity: .45; cursor: not-allowed; transform: none; }
.forge-btn-active { background: linear-gradient(135deg, #ffd54f, #ff8f00, #e65100) !important; box-shadow: 0 6px 28px rgba(230,81,0,.55) !important; animation: btn-forge-pulse .4s ease-in-out infinite alternate; }
.forge-btn-done   { background: linear-gradient(135deg, #a5d6a7, #43a047, #1b5e20) !important; color: #fff !important; }
@keyframes btn-forge-pulse { from { box-shadow: 0 6px 22px rgba(230,81,0,.55); } to { box-shadow: 0 10px 40px rgba(230,81,0,.80); } }
.forge-heart { font-size: 1.1rem; }
.forge-heart.beat { animation: heart-beat .4s ease-in-out infinite alternate; }
@keyframes heart-beat { from { transform: scale(1); } to { transform: scale(1.35); } }

/* ════ 管理标签 ════ */
.manage-body { display: flex; flex: 1; min-height: 0; }

/* 左列：列表 */
.manage-list-panel {
  width: 250px; flex-shrink: 0;
  border-right: 1px solid rgba(245,166,35,.15);
  display: flex; flex-direction: column;
  background: rgba(255,249,235,.35);
}
.manage-search-wrap { padding: 10px 12px; border-bottom: 1px solid rgba(245,166,35,.12); flex-shrink: 0; }
.manage-search {
  width: 100%; box-sizing: border-box;
  background: rgba(255,255,255,.70); border: 1.5px solid rgba(245,166,35,.22);
  border-radius: 8px; padding: 6px 10px; font-size: .76rem; color: #2C2724; outline: none;
}
.manage-search:focus { border-color: rgba(245,166,35,.55); }
.manage-search::placeholder { color: #C5B5A0; }

.manage-list { flex: 1; overflow-y: auto; padding: 6px 0; }
.manage-list::-webkit-scrollbar { width: 3px; }
.manage-list::-webkit-scrollbar-thumb { background: rgba(245,166,35,.20); border-radius: 3px; }

.manage-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; cursor: pointer;
  border-left: 3px solid transparent;
  transition: background .1s;
}
.manage-item:hover { background: rgba(245,166,35,.08); }
.manage-item.active { background: rgba(245,166,35,.14); border-left-color: #D97706; }
.manage-item.item-dirty { border-left-color: #F59E0B; }

.mi-icon { font-size: 1.1rem; flex-shrink: 0; }
.mi-info { flex: 1; min-width: 0; }
.mi-name { font-size: .74rem; font-weight: 700; color: #2C2724; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mi-file { font-size: .60rem; color: #B5A898; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mi-rarity {
  font-size: .55rem; font-weight: 800; padding: 1px 5px; border-radius: 4px; flex-shrink: 0;
}
.mr-blue   { background: rgba(59,130,246,.12);  color: #3B82F6; }
.mr-purple { background: rgba(168,85,247,.12);  color: #A855F7; }
.mr-gold   { background: rgba(245,158,11,.15);  color: #D97706; }

.manage-empty { padding: 24px 0; text-align: center; font-size: .76rem; color: #C5B5A0; }
.manage-list-footer {
  padding: 6px 14px; font-size: .62rem; color: #C5B5A0;
  border-top: 1px solid rgba(245,166,35,.12); flex-shrink: 0;
}

/* 右列：编辑器 */
.manage-editor-panel {
  flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden;
}

.manage-placeholder {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  background: rgba(255,249,235,.30);
}
.mp-icon { font-size: 2.8rem; opacity: .2; }
.mp-text { font-size: .78rem; color: #C5B5A0; }

/* 编辑器头部 */
.manage-editor-header {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 18px; border-bottom: 1px solid rgba(245,166,35,.15);
  background: rgba(255,249,235,.65); flex-shrink: 0;
}
.meh-icon { font-size: 1.5rem; }
.meh-info { flex: 1; min-width: 0; }
.meh-name { font-size: .84rem; font-weight: 800; color: #2C2724; }
.meh-file { font-size: .62rem; color: #B5A898; font-family: monospace; }
.dirty-badge { font-size: .60rem; font-weight: 700; color: #D97706; background: rgba(245,158,11,.12); border: 1px solid rgba(245,158,11,.25); border-radius: 6px; padding: 2px 7px; flex-shrink: 0; }
.meh-del { background: rgba(220,38,38,.07); border: 1px solid rgba(220,38,38,.20); color: #DC2626; border-radius: 8px; padding: 5px 12px; font-size: .72rem; font-weight: 700; cursor: pointer; flex-shrink: 0; transition: background .15s; }
.meh-del:hover { background: rgba(220,38,38,.15); }

/* 元数据行 */
.meta-row {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 10px 18px 0; flex-shrink: 0; flex-wrap: wrap;
}
.meta-field { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 120px; }

/* 代码编辑器 */
.manage-code-wrap {
  flex: 1; min-height: 0; display: flex; flex-direction: column;
  padding: 10px 18px 0;
}
.manage-code {
  flex: 1; min-height: 0; height: 100%;
  min-height: 180px;
}
.code-loading { font-size: .62rem; color: #B5A898; margin-left: auto; font-style: italic; }

/* 操作栏 */
.manage-actions {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 18px; border-top: 1px solid rgba(245,166,35,.12);
  flex-shrink: 0; background: rgba(255,249,235,.50);
}
.save-msg {
  flex: 1; font-size: .72rem;
  &.ok  { color: #16a34a; }
  &.err { color: #DC2626; }
}
.manage-btn {
  padding: 8px 20px; border-radius: 10px; border: none;
  font-size: .78rem; font-weight: 800; cursor: pointer; transition: all .2s; flex-shrink: 0;
}
.save-btn {
  background: linear-gradient(135deg,#ffe082,#F59E0B);
  color: #1a0c00; box-shadow: 0 3px 12px rgba(245,158,11,.30);
}
.save-btn:not(:disabled):hover { transform: translateY(-1px); box-shadow: 0 5px 18px rgba(245,158,11,.50); }
.save-btn:disabled { opacity: .40; cursor: not-allowed; transform: none; }
.save-btn.active { animation: btn-forge-pulse .4s ease-in-out infinite alternate; }

/* ── 删除确认弹窗 ── */
.del-overlay {
  position: absolute; inset: 0; z-index: 20;
  background: rgba(0,0,0,.32); backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center; border-radius: 28px;
}
.del-card {
  background: rgba(255,252,244,.97); border: 1px solid rgba(245,166,35,.25);
  border-radius: 18px; box-shadow: 0 16px 40px rgba(0,0,0,.20);
  padding: 28px 24px 22px; width: 320px; max-width: 90%;
  display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center;
}
.del-icon { font-size: 2rem; }
.del-title { font-size: .95rem; font-weight: 800; color: #2C2724; }
.del-msg { font-size: .78rem; color: #7A6A5A; line-height: 1.55; }
.del-msg b { color: #2C2724; }
.del-actions { display: flex; gap: 10px; width: 100%; margin-top: 6px; }
.del-btn { flex: 1; padding: 9px 0; border-radius: 10px; border: none; font-size: .78rem; font-weight: 700; cursor: pointer; transition: opacity .15s; }
.del-btn:hover { opacity: .85; }
.del-btn.cancel  { background: rgba(245,166,35,.10); color: #7A6A5A; border: 1px solid rgba(245,166,35,.25); }
.del-btn.confirm { background: linear-gradient(135deg,#DC2626,#EF4444); color: #fff; }

.del-confirm-enter-active { animation: del-in .25s cubic-bezier(.175,.885,.32,1.275); }
.del-confirm-leave-active { animation: del-in .18s ease-in reverse; }
@keyframes del-in { from { opacity:0; transform:scale(.88); } }

/* ── 弹窗过渡 ── */
.forge-fade-enter-active { animation: ff-in .35s cubic-bezier(.175,.885,.32,1.275); }
.forge-fade-leave-active { animation: ff-in .25s ease-in reverse; }
@keyframes ff-in { from { opacity:0; transform: scale(.85) translateY(28px); } }
</style>
