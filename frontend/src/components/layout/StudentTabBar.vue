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
import { useRoute } from 'vue-router'

const route = useRoute()

const tabs = [
  { key: 'experiments', label: '实验台', to: '/experiments' },
  { key: 'files', label: '实验资料', to: '/files' }
]

const EXPERIMENT_ROUTES = ['experiments', 'lab', 'after-report', 'after-review']

function isActive(tab) {
  if (tab.key === 'experiments') return EXPERIMENT_ROUTES.includes(route.name)
  return route.name === tab.key
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
