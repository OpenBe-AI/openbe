<template>
  <nav id="sidebar">

    <!-- 蜂巢管理 -->
    <div class="sidebar-hive-section">
      <div class="sidebar-hive-header">
        <span class="sidebar-hive-label">蜂巢</span>
        <button class="sidebar-hive-add" @click="$emit('new-hive')">＋ 新建</button>
      </div>
      <div class="sidebar-hive-list">
        <div v-if="store.hives.length === 0" class="sidebar-hive-empty">暂无蜂巢</div>
        <div
          v-for="hive in store.hives"
          :key="hive.hiveId"
          class="sidebar-hive-item"
          :class="{ active: hive.hiveId === store.activeHiveId }"
          @click="switchHive(hive.hiveId)"
        >
          <span class="sidebar-hive-dot"></span>
          <span class="sidebar-hive-name">{{ hive.name || hive.hiveId }}</span>
          <span class="sidebar-hive-acts">
            <button class="sidebar-hive-act rename" title="重命名" @click.stop="$emit('rename-hive', hive)">✎</button>
            <button class="sidebar-hive-act del"    title="删除"   @click.stop="deleteHive(hive)">✕</button>
          </span>
        </div>
      </div>
    </div>

    <!-- 导航菜单 -->
    <div class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.panel"
        class="nav-item"
        :class="{ active: store.activePanel === item.panel }"
        @click="store.setActivePanel(item.panel)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-label">{{ item.label }}</span>
      </div>
    </div>

  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '../stores/app.js'
import { useI18n } from '../composables/useI18n.js'

const store = useAppStore()
const { t } = useI18n()

const emit = defineEmits(['new-hive', 'rename-hive', 'delete-hive', 'switch-hive'])

const navItems = computed(() => [
  { panel: 'chat',     icon: '💬', label: t('nav_chat') },
  { panel: 'bees',     icon: '🐝', label: t('nav_bees') },
  { panel: 'will',     icon: '💉', label: t('nav_will') },
  { panel: 'skills',   icon: '⚔️', label: t('nav_skills') },
  { panel: 'habitat',  icon: '🌸', label: t('nav_habitat') },
  { panel: 'wings',    icon: '🪽', label: t('nav_wings') },
  { panel: 'settings', icon: '🛸', label: t('nav_settings') },
])

function switchHive(hiveId) { emit('switch-hive', hiveId) }
function deleteHive(hive)   { emit('delete-hive', hive)   }
</script>
