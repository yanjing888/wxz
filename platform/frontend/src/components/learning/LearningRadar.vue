<template>
  <div class="learning-radar" :class="{ 'with-bars': showBars }">
    <svg viewBox="0 0 320 280" role="img" :aria-label="description">
      <polygon v-for="level in [4, 3, 2, 1]" :key="level" :points="ring(level / 4)" class="radar-ring" />
      <line v-for="(_, index) in dimensions" :key="'axis' + index" x1="160" y1="136" :x2="position(index, 1).x" :y2="position(index, 1).y" class="radar-axis" />
      <polygon v-for="sector in sectors" :key="sector.key" :points="sector.points" class="radar-sector" />
      <path v-if="hasData" :d="outlinePath" class="radar-outline" />
      <g v-for="(dimension, index) in dimensions" :key="dimension.key">
        <circle v-if="dimension.value != null" :cx="position(index, dimension.value / 100).x" :cy="position(index, dimension.value / 100).y" r="3.5" class="radar-dot" />
        <text :x="position(index, 1.3).x" :y="position(index, 1.3).y" text-anchor="middle" dominant-baseline="middle" class="radar-label">{{ dimension.label }}</text>
      </g>
      <text v-if="!hasData" x="160" y="140" text-anchor="middle" class="radar-empty">暂无记录</text>
    </svg>
    <div class="dimension-values"><div v-for="dimension in dimensions" :key="dimension.key"><div class="dimension-value-head"><span>{{ dimension.label }}</span><strong>{{ dimension.value == null ? '—' : dimension.value }}</strong></div><div v-if="showBars" class="dimension-track" :class="{ unmeasured: dimension.value == null }" aria-hidden="true"><span v-if="dimension.value != null" :style="{ width: Math.max(0, Math.min(100, dimension.value)) + '%' }" /></div></div></div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ dimensions: { type: Array, default: () => [] }, showBars: Boolean })
const position = (index, scale) => { const angle = -Math.PI / 2 + index * 2 * Math.PI / Math.max(1, props.dimensions.length); return { x: 160 + Math.cos(angle) * 84 * scale, y: 136 + Math.sin(angle) * 84 * scale } }
const point = (index, scale) => { const p = position(index, scale); return `${p.x},${p.y}` }
const ring = scale => props.dimensions.map((_, index) => point(index, scale)).join(' ')
const hasData = computed(() => props.dimensions.some(d => d.value != null))
const complete = computed(() => props.dimensions.length > 0 && props.dimensions.every(d => d.value != null))
const measuredPoint = index => point(index, Math.max(0, Math.min(100, props.dimensions[index].value)) / 100)
const sectors = computed(() => {
  if (props.dimensions.length < 3) return []
  return props.dimensions.flatMap((dimension, index) => {
    const next = (index + 1) % props.dimensions.length
    if (dimension.value == null || props.dimensions[next].value == null || dimension.value <= 0 || props.dimensions[next].value <= 0) return []
    return [{ key: dimension.key, points: `160,136 ${measuredPoint(index)} ${measuredPoint(next)}` }]
  })
})
const hasArea = computed(() => sectors.value.length > 0)
const outlinePath = computed(() => {
  const dimensions = props.dimensions
  if (!hasData.value) return ''
  if (complete.value) return `M ${dimensions.map((_, index) => measuredPoint(index)).join(' L ')} Z`
  return dimensions.flatMap((dimension, index) => {
    const previous = (index + dimensions.length - 1) % dimensions.length
    if (dimension.value == null || dimensions[previous].value != null) return []
    const run = []
    let current = index
    while (dimensions[current].value != null) { run.push(measuredPoint(current)); current = (current + 1) % dimensions.length }
    return [`M 160,136 L ${run.join(' L ')} L 160,136 Z`]
  }).join(' ')
})
const description = computed(() => props.dimensions.map(d => `${d.label}：${d.value == null ? '暂无记录' : d.value}`).join('，'))
</script>
<style scoped>
.learning-radar svg{display:block;width:100%;max-width:340px;margin:auto}
.radar-ring{fill:#f7f8fb;stroke:#c8cedb;stroke-width:1;stroke-dasharray:5 5}.radar-ring:nth-child(even){fill:#edf0f6}.radar-axis{stroke:#bdc5d4;stroke-width:1;stroke-dasharray:5 5}
.radar-sector{fill:rgba(99,102,180,.38);stroke:none}.radar-outline{fill:none;stroke:#6265ad;stroke-width:2;stroke-linejoin:round}.radar-dot{fill:#fff;stroke:#6265ad;stroke-width:2}
.radar-label{fill:#64748b;font-size:12px}.radar-empty{fill:#94a3b8;font-size:12px}
.dimension-values{display:flex;flex-direction:column;gap:11px;padding:0 18px 18px}.dimension-value-head{display:flex;justify-content:space-between;font-size:12px;color:#64748b}.dimension-values strong{font-size:13px;color:#334155;font-variant-numeric:tabular-nums}
.with-bars svg{max-width:370px;margin:6px auto 0}.with-bars .radar-label{fill:#475569;font-size:12px;font-weight:500}.with-bars .dimension-values{padding:0 24px 24px;gap:15px}.with-bars .dimension-value-head{color:#475569}.with-bars .dimension-value-head strong{color:#4f46e5;font-size:13px;font-weight:600}
.dimension-track{height:5px;border-radius:5px;background:#eef1f8;margin-top:8px;overflow:hidden}.dimension-track>span{display:block;height:100%;border-radius:5px;background:#818cf8}.dimension-track.unmeasured{background:repeating-linear-gradient(90deg,#edf0f6 0,#edf0f6 4px,transparent 4px,transparent 7px)}
</style>
