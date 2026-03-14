<script setup>
import { computed } from 'vue';

const props = defineProps({
  species: { type: String, default: 'queen' },
  seed:    { type: String, default: ''      },
  size:    { type: Number, default: 160     },
});

const imageMap = {
  'queen':      '/bee/queen_extracted.png',
  'Queen':      '/bee/queen_extracted.png',
  'nurse':      '/bee/nurse_extracted.png',
  'Nurse':      '/bee/nurse_extracted.png',
  'worker':     '/bee/worker_extracted.png',
  'Worker':     '/bee/worker_extracted.png',
  'soldier':    '/bee/soldier_extracted.png',
  'Soldier':    '/bee/soldier_extracted.png',
  'scout':      '/bee/scout_extracted.png',
  'Scout':      '/bee/scout_extracted.png',
  'medic':      '/bee/medic_extracted.png',
  'Medic':      '/bee/medic_extracted.png',
  'scribe':     '/bee/scribe_extracted.png',
  'Scribe':     '/bee/scribe_extracted.png',
  'painter':    '/bee/painter_extracted.png',
  'Painter':    '/bee/painter_extracted.png',
  'editor':     '/bee/editor_extracted.png',
  'Editor':     '/bee/editor_extracted.png',
  'sentinel':   '/bee/sentinel_extracted.png',
  'Sentinel':   '/bee/sentinel_extracted.png',
  'influencer': '/bee/queen_extracted.png',
  'Influencer': '/bee/queen_extracted.png',
};

const imageSrc = computed(() => imageMap[props.species] || '/bee/queen_extracted.png');
</script>

<template>
  <div
    style="
      width: 280px;
      height: 280px;
      display: flex;
      justify-content: center;
      align-items: center;
      background: transparent !important;
      background-color: transparent !important;
      box-shadow: none !important;
    "
  >
    <img
      :src="imageSrc"
      :alt="props.species"
      style="
        width: 100%;
        height: 100%;
        object-fit: contain;
        mix-blend-mode: multiply;
        filter: drop-shadow(0 0 12px #F5A623) drop-shadow(0 0 35px rgba(245, 166, 35, 0.6));
        animation: bee-breathe 4s ease-in-out infinite alternate;
      "
    />
  </div>
</template>

<style>
/* 全局（非 scoped）确保动画帧可访问 */
@keyframes bee-breathe {
  0%   {
    transform: scale(0.98) translateY(2px);
    filter: drop-shadow(0 0 8px #F5A623) drop-shadow(0 0 20px rgba(245, 166, 35, 0.4));
  }
  100% {
    transform: scale(1.02) translateY(-2px);
    filter: drop-shadow(0 0 15px #F5A623) drop-shadow(0 0 45px rgba(245, 166, 35, 0.8));
  }
}
</style>
