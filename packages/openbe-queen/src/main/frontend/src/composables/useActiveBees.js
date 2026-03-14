/**
 * useActiveBees.js
 * Shared composable that returns a computed list of non-queen, non-offline bees.
 * Previously duplicated as `nonQueenBees` in BeesPanel.vue and WillPanel.vue.
 */
import { computed } from 'vue'
import { useAppStore } from '../stores/app.js'

export function useActiveBees() {
  const store = useAppStore()

  const activeBees = computed(() => {
    const activeHiveId = store.activeHiveId
    return Object.entries(store.bees)
      .filter(([, b]) => {
        const type = (b.beeType || b.type || '').toLowerCase()
        if (type === 'queen') return false
        if ((b.status || '').toLowerCase() === 'offline') return false
        // 只显示当前蜂巢的蜜蜂
        if (activeHiveId && b.hiveId && b.hiveId !== activeHiveId) return false
        return true
      })
      .map(([key, b]) => ({ ...b, _key: key }))
  })

  return { activeBees }
}
