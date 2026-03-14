import { defineStore } from 'pinia'
import { useI18n } from '../composables/useI18n'

function loadChatMessages() {
  try {
    const saved = JSON.parse(localStorage.getItem('openbe-chat') || '{}')
    // 过滤掉上次刷新前未完成的 thinking 气泡
    Object.keys(saved).forEach(k => {
      saved[k] = saved[k].filter(m => !m.thinking)
    })
    return saved
  } catch {
    return {}
  }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    theme: 'modern',
    lang: localStorage.getItem('openbe-lang') || 'zh',
    activePanel: localStorage.getItem('openbe-panel') || 'chat',
    activeChatBee: null,
    bees: {},           // { "type:pid": BeeObject }
    activeEditingBeeKey: '__queen__', // 意志注入面板当前编辑的蜜蜂
    hives: [],
    activeHiveId: localStorage.getItem('openbe-activeHive') || null,
    chatMessages: loadChatMessages(),  // { beeKey: [msg...] }
    pendingTasks: {},   // { taskId: { beeKey, thinkingId } }
    logCollapsed: false,
    onlineCount: 0,
  }),

  actions: {
    setLang(lang) {
      this.lang = lang
      localStorage.setItem('openbe-lang', lang)
      useI18n().setLang(lang)
    },
    setActivePanel(panel) {
      this.activePanel = panel
      localStorage.setItem('openbe-panel', panel)
    },
    activateChatBee(key) {
      this.activeChatBee = key
      if (!this.chatMessages[key]) this.chatMessages[key] = []
    },
    setActiveHive(hiveId) {
      this.activeHiveId = hiveId || null
      localStorage.setItem('openbe-activeHive', hiveId || '')
    },
    addMessage(beeKey, role, content, options = {}) {
      if (!this.chatMessages[beeKey]) this.chatMessages[beeKey] = []
      const msg = {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`,
        role,
        content,
        time: new Date().toLocaleTimeString(),
        thinking: options.thinking || false,
      }
      this.chatMessages[beeKey].push(msg)
      this._persistChatLocal()
      return msg.id
    },
    updateMessage(beeKey, msgId, content) {
      const msgs = this.chatMessages[beeKey]
      if (!msgs) return
      const msg = msgs.find(m => m.id === msgId)
      if (msg) {
        msg.content = content
        msg.thinking = false
        msg.time = new Date().toLocaleTimeString()
        this._persistChatLocal()
      }
    },
    removeMessage(beeKey, msgId) {
      const msgs = this.chatMessages[beeKey]
      if (!msgs) return
      const idx = msgs.findIndex(m => m.id === msgId)
      if (idx !== -1) msgs.splice(idx, 1)
      this._persistChatLocal()
    },
    removeThinkingMessages(beeKey) {
      const msgs = this.chatMessages[beeKey]
      if (!msgs) return
      this.chatMessages[beeKey] = msgs.filter(m => !m.thinking)
      this._persistChatLocal()
    },
    setBeesData(beesData) {
      const normalized = {}
      if (Array.isArray(beesData)) {
        beesData.forEach(b => {
          const type = (b.type || b.beeType || 'unknown').toLowerCase()
          const pid  = b.pid || Math.random().toString(36).slice(2)
          normalized[`${type}:${pid}`] = b
        })
      } else {
        Object.assign(normalized, beesData)
      }
      this.bees = normalized
      this.onlineCount = Object.values(normalized).filter(b =>
        (b.status || '').toLowerCase() !== 'offline'
      ).length
      // 迁移旧 type:pid key 下的历史消息到稳定的 bee:beeId key
      Object.values(normalized).forEach(b => {
        if (!b.beeId) return
        const newKey = `bee:${b.beeId}`
        const type = (b.type || b.beeType || 'unknown').toLowerCase()
        const pid  = b.pid || ''
        const oldKey = `${type}:${pid}`
        if (this.chatMessages[oldKey] && this.chatMessages[oldKey].length > 0) {
          if (!this.chatMessages[newKey] || this.chatMessages[newKey].length === 0) {
            this.chatMessages[newKey] = this.chatMessages[oldKey]
          }
          delete this.chatMessages[oldKey]
          this._persistChatLocal()
        }
      })
    },
    setHives(hives) {
      this.hives = hives || []
      // validate activeHiveId
      const exists = this.activeHiveId && this.hives.find(h => h.hiveId === this.activeHiveId)
      if (!exists) {
        this.activeHiveId = null
        localStorage.setItem('openbe-activeHive', '')
      }
    },
    deleteHiveChatData(hiveId) {
      // 清除蜂王聊天
      delete this.chatMessages[`queen:${hiveId}`]
      delete this.chatMessages[`hive:${hiveId}`]

      // 找出属于该蜂巢的所有蜜蜂，清除其聊天记录并从 store 移除
      const toDelete = []
      Object.entries(this.bees).forEach(([storeKey, bee]) => {
        if (bee.hiveId === hiveId || bee.hive === hiveId) {
          toDelete.push(storeKey)
          // 清聊天记录（storeKey 和 beeId 两种 key）
          delete this.chatMessages[storeKey]
          if (bee.beeId) delete this.chatMessages[`bee:${bee.beeId}`]
          // 重置当前聊天蜜蜂
          if (this.activeChatBee === storeKey ||
              this.activeChatBee === `bee:${bee.beeId}`) {
            this.activeChatBee = null
          }
        }
      })
      toDelete.forEach(k => delete this.bees[k])

      if (this.activeChatBee === `queen:${hiveId}`) this.activeChatBee = null
      if (this.activeHiveId === hiveId) this.setActiveHive(null)

      this._persistChatLocal()
    },
    removeBee(beeKey) {
      // 清除聊天记录
      delete this.chatMessages[beeKey]
      // 从 bees store 移除所有 storeKey 对应该 beeId 的条目
      const beeId = beeKey.startsWith('bee:') ? beeKey.slice(4) : null
      Object.keys(this.bees).forEach(k => {
        const b = this.bees[k]
        if (k === beeKey || (beeId && b.beeId === beeId)) {
          delete this.bees[k]
        }
      })
      if (this.activeChatBee === beeKey) this.activeChatBee = null
      this._persistChatLocal()
    },
    _persistChatLocal() {
      localStorage.setItem('openbe-chat', JSON.stringify(this.chatMessages))
    },
    getBeeStatus(beeKey) {
      if (!beeKey) return 'offline'
      const parts = beeKey.includes(':') ? beeKey.split(':') : [beeKey, null]
      const type = parts[0].toLowerCase()
      const pid  = parts[1] || null
      const entries = Object.values(this.bees).filter(b => {
        if ((b.beeType || b.type || '').toLowerCase() !== type) return false
        return pid ? (b.pid || '').toString() === pid : true
      })
      if (entries.length === 0) return 'offline'
      if (entries.some(b => (b.status || '').toLowerCase() === 'busy'))  return 'busy'
      if (entries.some(b => (b.status || '').toLowerCase() === 'online')) return 'online'
      return 'offline'
    },
    get activeHive() {
      return this.activeHiveId ? this.hives.find(h => h.hiveId === this.activeHiveId) : null
    },
  },

  getters: {
    currentHive: (state) => state.activeHiveId
      ? state.hives.find(h => h.hiveId === state.activeHiveId) || null
      : null,
    chatTabEntries: (state) => {
      const entries = []
      // 蜂王（当前蜂巢）
      if (state.activeHiveId) {
        const h = state.hives.find(hv => hv.hiveId === state.activeHiveId)
        if (h && h.bee && h.bee.model) {
          const beeName = h.bee.name || h.name || h.hiveId
          entries.push({ key: `queen:${h.hiveId}`, type: 'queen', isQueen: true, hiveId: h.hiveId, label: beeName })
        }
      }
      // 只显示当前蜂巢的工作蜂，且去重（同一 beeId 只出现一次）
      const activeHiveId = state.activeHiveId
      const seenKeys = new Set()
      Object.entries(state.bees).forEach(([storeKey, bee]) => {
        const type = (bee.beeType || bee.type || '').toLowerCase()
        if (type === 'queen') return
        // 只显示属于当前蜂巢的蜜蜂（在线蜂和离线蜂均有 hiveId）
        if (activeHiveId && bee.hiveId && bee.hiveId !== activeHiveId) return
        // 蜜蜂有 hiveId 但不属于任何已存在的蜂巢 → 跳过
        if (bee.hiveId && !state.hives.find(h => h.hiveId === bee.hiveId)) return
        const species = (bee.displaySpecies || type).toUpperCase()
        const label   = bee.beeName || species
        const status  = (bee.status || 'offline').toLowerCase()
        // 用稳定的 beeId 作为 key，避免重启后 PID 变化导致对话记录丢失
        const key = bee.beeId ? `bee:${bee.beeId}` : storeKey
        // 去重：同一 key 只推入一次（优先出现的条目保留）
        if (seenKeys.has(key)) return
        seenKeys.add(key)
        entries.push({ key, type, isQueen: false, species, label, pid: bee.pid, status, beeId: bee.beeId })
      })
      return entries
    },
  }
})
