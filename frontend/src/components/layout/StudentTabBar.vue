<template>
  <nav class="tab-bar" aria-label="功能导航">
    <router-link
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      class="tab-item"
      :class="{ 'tab-item--active': isActive(tab) }"
    >
      {{ tab.label }}
    </router-link>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { lastExperiment } from '../../utils/experimentFlow'

const route = useRoute()

const lastCode = computed(() => lastExperiment() || '')

const tabs = computed(() => {
  const afterTo = lastCode.value
    ? { name: 'after-report', params: { code: lastCode.value } }
    : { name: 'experiments' }
  return [
    { key: 'lab', label: '实验台', to: '/experiments' },
    { key: 'after', label: '课后', to: afterTo }
  ]
})

const LAB_ROUTES = ['experiments', 'prep-ready', 'lab']
const AFTER_ROUTES = ['after-report', 'after-review', 'agents', 'agent-workspace']

function isActive(tab) {
  if (tab.key === 'lab') return LAB_ROUTES.includes(route.name)
  if (tab.key === 'after') return AFTER_ROUTES.includes(route.name)
  return false
}
</script>

<style scoped>
.tab-bar {
  @apply flex items-center gap-1 -mx-6 px-6 border-t border-line-soft overflow-x-auto;
}
.tab-item {
  @apply relative inline-flex items-center px-4 py-3 text-[14px] text-ink-muted whitespace-nowrap
    transition-colors hover:text-ink-strong;
}
.tab-item--active {
  @apply text-brand-600 font-semibold;
}
.tab-item--active::after {
  content: '';
  @apply absolute bottom-0 left-4 right-4 h-0.5 bg-brand-600 rounded-full;
}
</style>
