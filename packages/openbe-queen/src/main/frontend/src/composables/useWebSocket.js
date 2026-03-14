import { ref } from 'vue'

const status = ref('disconnected') // connecting | connected | disconnected
const logs = ref([])
const taskCallbacks = new Map() // taskId => callback(answer)
let wsInstance = null
let reconnectTimer = null

function addLog(cssClass, text) {
  logs.value.push({ cssClass, text: `[${new Date().toLocaleTimeString()}] ${text}` })
  if (logs.value.length > 200) logs.value.shift()
}

function connect() {
  if (wsInstance && (wsInstance.readyState === WebSocket.OPEN || wsInstance.readyState === WebSocket.CONNECTING)) return

  const wsUrl = `ws://${location.host}/api/logs/stream`
  status.value = 'connecting'
  wsInstance = new WebSocket(wsUrl)

  wsInstance.addEventListener('open', () => {
    status.value = 'connected'
    addLog('green', `[WS] Connected to ${wsUrl}`)
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  })

  wsInstance.addEventListener('message', (event) => {
    let data
    try {
      data = JSON.parse(event.data)
    } catch {
      addLog('sys', event.data)
      return
    }

    // Chat result
    if (data.type === 'result' && data.taskId && taskCallbacks.has(data.taskId)) {
      const cb = taskCallbacks.get(data.taskId)
      taskCallbacks.delete(data.taskId)
      cb(data.answer || '')
      addLog('green', `[CHAT] Task ${data.taskId} resolved`)
      return
    }

    const level = (data.level || 'sys').toLowerCase()
    const lineClass = level === 'info' ? 'green'
      : level === 'warn' || level === 'warning' ? 'yellow'
      : level === 'error' ? 'red'
      : 'sys'
    const text = data.message || data.text || JSON.stringify(data)
    addLog(lineClass, `[${data.source || 'SYS'}] ${text}`)
  })

  wsInstance.addEventListener('close', () => {
    status.value = 'disconnected'
    addLog('red', '[WS] Disconnected — reconnecting in 5s...')
    reconnectTimer = setTimeout(connect, 5000)
  })

  wsInstance.addEventListener('error', () => {
    status.value = 'disconnected'
  })
}

function onTaskComplete(taskId, callback) {
  taskCallbacks.set(taskId, callback)
}

function removeTask(taskId) {
  taskCallbacks.delete(taskId)
}

export function useWebSocket() {
  return { status, logs, connect, addLog, onTaskComplete, removeTask }
}
