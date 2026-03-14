<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box">
      <div class="modal-title">{{ t('new_hive_title') }}</div>
      <div class="form-group">
        <label class="form-label">{{ t('name_label') }}</label>
        <input
          class="form-control"
          :class="{ 'input-error': nameError }"
          v-model="name"
          type="text"
          :placeholder="t('hive_name_placeholder')"
          ref="nameInput"
          @input="nameError = ''"
        >
        <div v-if="nameError" class="field-error">{{ nameError }}</div>
      </div>
      <div class="form-group">
        <label class="form-label">{{ t('hive_desc_label') }}</label>
        <input class="form-control" v-model="desc" type="text" :placeholder="t('hive_desc_placeholder')">
      </div>
      <div class="modal-actions">
        <button class="btn btn-ghost" @click="close">{{ t('cancel') }}</button>
        <button class="btn btn-primary" @click="confirm">{{ t('confirm_create_btn') }}</button>
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
  existingNames: { type: Array, default: () => [] },
})
const emit = defineEmits(['close', 'confirm'])

const name = ref('')
const desc = ref('')
const nameError = ref('')
const nameInput = ref(null)

watch(() => props.visible, async (v) => {
  if (v) {
    name.value = ''
    desc.value = ''
    nameError.value = ''
    await nextTick()
    nameInput.value?.focus()
  }
})

function close() { emit('close') }

function confirm() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    nameError.value = t('hive_name_required')
    return
  }
  if (props.existingNames.map(n => n.toLowerCase()).includes(trimmed.toLowerCase())) {
    nameError.value = t('hive_name_exists')
    return
  }
  emit('confirm', { name: trimmed, desc: desc.value.trim() })
}
</script>

<style scoped>
.input-error { border-color: var(--danger) !important; }
.field-error {
  font-size: 0.72rem;
  color: var(--danger);
  margin-top: 3px;
}
</style>
