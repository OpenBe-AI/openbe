<template>
  <div id="log-panel" :class="{ collapsed: store.logCollapsed }">
    <button class="log-toggle" @click="store.logCollapsed = !store.logCollapsed" title="Toggle log">
      {{ store.logCollapsed ? '▶' : '◀' }}
    </button>
    <div class="log-header">🍯 {{ t('honey_log') }}</div>
    <div class="log-stream" ref="streamEl">
      <div
        v-for="(line, idx) in logs"
        :key="idx"
        class="log-line"
        :class="line.cssClass"
      >{{ line.text }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useAppStore } from '../stores/app.js'
import { useI18n } from '../composables/useI18n.js'
import { useWebSocket } from '../composables/useWebSocket.js'

const store = useAppStore()
const { t } = useI18n()
const { logs } = useWebSocket()
const streamEl = ref(null)

watch(logs, async () => {
  await nextTick()
  if (streamEl.value) streamEl.value.scrollTop = streamEl.value.scrollHeight
}, { deep: true })
</script>
