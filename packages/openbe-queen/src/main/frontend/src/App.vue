<template>
  <div id="app">
    <!-- Toast container -->
    <div id="toast-container">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="toast.type"
      >
        <span style="font-weight:800">{{ toastIcon(toast.type) }}</span>
        <span>{{ toast.message }}</span>
      </div>
    </div>

    <!-- Top Bar -->
    <TopBar @emergency-stop="triggerEmergencyStop" />

    <!-- Body Row -->
    <div id="body-row">
      <!-- Sidebar -->
      <Sidebar
        @new-hive="showNewHiveModal = true"
        @rename-hive="openRenameHive"
        @delete-hive="confirmDeleteHive"
        @switch-hive="doSwitchHive"
      />

      <!-- Main Content -->
      <div id="main">
        <ChatPanel ref="chatPanelRef" />
        <BeesPanel
          @toast="showToast"
          @edit-hive-bee="openAddBeeModal"
          @refresh-hives="pollAndRefresh"
        />
        <WillPanel @toast="showToast" @refresh-hives="pollAndRefresh" ref="willPanelRef" />
        <SkillsPanel @toast="showToast" ref="skillsPanelRef" />
        <HabitatPanel />
        <WingsPanel />
        <SettingsPanel @toast="showToast" @emergency-stop="triggerEmergencyStop" ref="settingsPanelRef" />
      </div>

    </div>

    <!-- Modals -->
    <NewHiveModal
      :visible="showNewHiveModal"
      :existingNames="store.hives.map(h => h.name || h.hiveId)"
      @close="showNewHiveModal = false"
      @confirm="createHive"
    />
    <RenameHiveModal
      :visible="showRenameModal"
      :initialName="renamingHive?.name || ''"
      @close="showRenameModal = false"
      @confirm="doRenameHive"
    />
    <AddBeeModal
      :visible="showAddBeeModal"
      :hiveId="addBeeHiveId"
      :bee="addBeeBee"
      :title="addBeeTitle"
      @close="showAddBeeModal = false"
      @confirm="saveBeeConfig"
    />
    <QueenConfigModal
      :visible="showQueenConfigModal"
      @close="showQueenConfigModal = false"
      @confirm="showQueenConfigModal = false"
      @toast="showToast"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useAppStore } from './stores/app.js'
import { useApi } from './composables/useApi.js'
import { useWebSocket } from './composables/useWebSocket.js'
import { useI18n } from './composables/useI18n.js'

import TopBar from './components/TopBar.vue'
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/panels/ChatPanel.vue'
import BeesPanel from './components/panels/BeesPanel.vue'
import WillPanel from './components/panels/WillPanel.vue'
import SkillsPanel from './components/panels/SkillsPanel.vue'
import SettingsPanel from './components/panels/SettingsPanel.vue'
import HabitatPanel from './components/panels/HabitatPanel.vue'
import WingsPanel from './components/panels/WingsPanel.vue'
import NewHiveModal from './components/modals/NewHiveModal.vue'
import RenameHiveModal from './components/modals/RenameHiveModal.vue'
import AddBeeModal from './components/modals/AddBeeModal.vue'
import QueenConfigModal from './components/modals/QueenConfigModal.vue'

const store = useAppStore()
const api = useApi()
const { connect, addLog } = useWebSocket()
const { currentLang } = useI18n()

// Panel refs
const chatPanelRef = ref(null)
const willPanelRef = ref(null)
const skillsPanelRef = ref(null)
const settingsPanelRef = ref(null)

// Modal state
const showNewHiveModal = ref(false)
const showRenameModal = ref(false)
const renamingHive = ref(null)
const showAddBeeModal = ref(false)
const addBeeHiveId = ref(null)
const addBeeBee = ref({})
const addBeeTitle = ref('配置蜂王')
const showQueenConfigModal = ref(false)

// Toast
const toasts = ref([])
let toastCounter = 0

function showToast(evt) {
  const { message, type = 'info', duration = 3500 } = typeof evt === 'object' ? evt : { message: evt }
  const id = ++toastCounter
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }, duration)
}

function toastIcon(type) {
  return type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ'
}

// ── Init ──────────────────────────────────────────────────

onMounted(async () => {
  // Start WebSocket
  connect()
  addLog('sys', '[SYS] OpenBe console initialized 🐝')

  // Load initial data
  await loadHives()
  startPolling()

  // Load messages for active bee
  if (store.activeChatBee && chatPanelRef.value) {
    chatPanelRef.value.loadMessagesFromServer(store.activeChatBee)
  }
})

// Watch activePanel to trigger panel-specific loads
watch(() => store.activePanel, (panel) => {
  if (panel === 'will'     && willPanelRef.value)     willPanelRef.value.load()
  if (panel === 'skills'   && skillsPanelRef.value)   skillsPanelRef.value.load()
  if (panel === 'settings' && settingsPanelRef.value) settingsPanelRef.value.load()
})

// ── Hive Management ───────────────────────────────────────

async function loadHives() {
  try {
    const hives = await api.getHives()
    store.setHives(hives)
    // After hive loading, sync chat tabs
    syncChatBeeOnHiveLoad()
  } catch {
    // silently fail
  }
}

function syncChatBeeOnHiveLoad() {
  const entries = store.chatTabEntries
  if (!store.activeChatBee && entries.length > 0) {
    store.activateChatBee(entries[0].key)
  } else if (store.activeChatBee && !entries.find(e => e.key === store.activeChatBee)) {
    if (entries.length > 0) {
      store.activateChatBee(entries[0].key)
    } else {
      store.activeChatBee = null
    }
  }
}

async function doSwitchHive(hiveId) {
  store.setActiveHive(hiveId)
  await loadHives()

  if (store.activePanel === 'will'     && willPanelRef.value)     willPanelRef.value.load()
  if (store.activePanel === 'skills'   && skillsPanelRef.value)   skillsPanelRef.value.load()
  if (store.activePanel === 'settings' && settingsPanelRef.value) settingsPanelRef.value.load()

  if (hiveId) {
    store.activateChatBee(`queen:${hiveId}`)
    store.setActivePanel('chat')
    if (chatPanelRef.value) chatPanelRef.value.loadMessagesFromServer(`queen:${hiveId}`)
  }
}

function openRenameHive(hive) {
  renamingHive.value = hive
  showRenameModal.value = true
}

async function doRenameHive(newName) {
  if (!renamingHive.value) return
  try {
    await api.renameHive(renamingHive.value.hiveId, newName)
    showRenameModal.value = false
    await loadHives()
    showToast({ message: '重命名成功', type: 'success' })
  } catch (e) {
    showToast({ message: e.message, type: 'error' })
  }
}

async function confirmDeleteHive(hive) {
  if (!confirm(`删除蜂巢「${hive.name || hive.hiveId}」？此操作不可恢复。`)) return
  try {
    await api.deleteHive(hive.hiveId)
    store.deleteHiveChatData(hive.hiveId)  // 清除蜂巢及其蜜蜂的所有本地状态
    await loadHives()
    await pollBees()                        // 立即刷新蜜蜂列表，确保 store.bees 同步
    store.setActivePanel('chat')
    showToast({ message: '蜂巢已删除', type: 'success' })
  } catch (e) {
    showToast({ message: e.message, type: 'error' })
  }
}

async function createHive({ name, desc }) {
  const hiveId = 'hive-' + Date.now().toString(36) + Math.random().toString(36).substr(2, 4)
  try {
    await api.createHive(hiveId, name, desc)
    showNewHiveModal.value = false
    await loadHives()
    await doSwitchHive(hiveId)
    showToast({ message: `蜂巢「${name}」已创建`, type: 'success' })
  } catch (e) {
    showToast({ message: e.message, type: 'error' })
  }
}

// ── Bee Config ────────────────────────────────────────────

function openAddBeeModal({ hiveId, bee }) {
  const hive = store.hives.find(h => h.hiveId === hiveId)
  addBeeHiveId.value = hiveId
  addBeeBee.value = bee || {}
  addBeeTitle.value = `配置蜂王 — ${hive ? hive.name : hiveId}`
  showAddBeeModal.value = true
}

async function saveBeeConfig({ hiveId, bee }) {
  try {
    await api.setHiveBee(hiveId, bee)
    showAddBeeModal.value = false
    await loadHives()
    showToast({ message: '蜂王配置已保存', type: 'success' })
  } catch (e) {
    showToast({ message: e.message, type: 'error' })
  }
}

// ── Polling ───────────────────────────────────────────────

async function pollBees() {
  try {
    const bees = await api.getBees()
    store.setBeesData(bees)
  } catch {
    // silently fail
  }
}

async function pollAndRefresh() {
  await pollBees()
  await loadHives()
}

function startPolling() {
  pollBees()
  setInterval(pollBees, 5000)
}

// ── Emergency Stop ────────────────────────────────────────

async function triggerEmergencyStop() {
  if (!confirm('紧急停止？')) return
  try {
    await api.emergencyStop()
    showToast({ message: 'Emergency stop triggered!', type: 'error' })
    addLog('red', '[SYS] EMERGENCY STOP TRIGGERED')
  } catch (err) {
    showToast({ message: `Emergency stop failed: ${err.message}`, type: 'error' })
  }
}
</script>
