<template>
  <div class="relative w-full h-full min-h-[140px] bg-white rounded-lg border border-line-soft overflow-hidden">
    <svg v-if="points.length" class="w-full h-full" viewBox="0 0 320 160" preserveAspectRatio="none">
      <line x1="36" y1="8" x2="36" y2="148" stroke="#e4e9f3" stroke-width="1" />
      <line x1="36" y1="148" x2="312" y2="148" stroke="#e4e9f3" stroke-width="1" />
      <polyline
        :points="polyline"
        fill="none"
        stroke="#4f46e5"
        stroke-width="2"
        stroke-linejoin="round"
      />
    </svg>
    <div v-else class="absolute inset-0 flex items-center justify-center text-[10px] text-ink-faint">
      等待试验机数据…
    </div>
    <div class="absolute left-1 bottom-1 text-[8px] text-ink-faint">ε / %</div>
    <div class="absolute left-1 top-1 text-[8px] text-ink-faint -rotate-90 origin-top-left translate-y-8">σ / MPa</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] }
})

const polyline = computed(() => {
  const pts = props.points
  if (!pts.length) return ''
  const maxX = Math.max(...pts.map((p) => p.strainPct), 1)
  const maxY = Math.max(...pts.map((p) => p.stressMpa), 1)
  const padX = 36
  const padY = 8
  const w = 276
  const h = 140
  return pts
    .map((p) => {
      const x = padX + (p.strainPct / maxX) * w
      const y = padY + h - (p.stressMpa / maxY) * h
      return `${x},${y}`
    })
    .join(' ')
})
</script>
