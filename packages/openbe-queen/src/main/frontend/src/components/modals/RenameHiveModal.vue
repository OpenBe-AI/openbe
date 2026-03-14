<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box">
      <div class="modal-title">{{ t('rename_hive_title') }}</div>
      <div class="form-group">
        <label class="form-label">{{ t('new_name_label') }}</label>
        <input
          class="form-control"
          v-model="name"
          type="text"
          :placeholder="t('hive_name_placeholder2')"
          ref="nameInput"
          @keydown.enter="confirm"
          @keydown.escape="close"
        >
      </div>
      <div class="modal-actions">
        <button class="btn btn-ghost" @click="close">{{ t('cancel') }}</button>
        <button class="btn btn-primary" @click="confirm">{{ t('confirm_save_btn') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useI18n } from '../../composables/useI18n.js'

const { t } = useI18n()

const props = defineProps({
  visible: Boolean,
  initialName: { type: String, default: '' }
})
const emit = defineEmits(['close', 'confirm'])

const name = ref('')
const nameInput = ref(null)

watch(() => props.visible, async (v) => {
  if (v) {
    name.value = props.initialName
    await nextTick()
    nameInput.value?.select()
  }
})

function close() { emit('close') }
function confirm() {
  if (!name.value.trim()) return
  emit('confirm', name.value.trim())
}
</script>
