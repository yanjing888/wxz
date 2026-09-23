<template>
  <nav class="tab-bar" aria-label="功能导航">
    <router-link
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      class="tab-item"
      :class="{ 'tab-item--active': isActive(tab) }"
      :style="{ '--icon-color': tab.color }"
    >
      <svg class="tab-icon" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" :d="tab.icon" />
        <path v-if="tab.iconFill" :fill="tab.color" :d="tab.iconFill" stroke="none" opacity="0.12" />
      </svg>
      <span class="tab-label">{{ tab.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { studentExperimentApi } from '../../api'
import { lastExperiment } from '../../utils/experimentFlow'
import { requiresSimulation, simulationPrepRoute } from '../../utils/prepSimulation'

const route = useRoute()

const currentCode = computed(() =>
  String(route.query.exp || route.params.code || '').trim() || lastExperiment() || ''
)

const simulationLocked = ref(false)

async function refreshSimulationState(code) {
  if (!code) {
    simulationLocked.value = false
    return
  }
  try {
    const { data } = await studentExperimentApi.getProgress(code)
    simulationLocked.value = requiresSimulation(data)
  } catch {
    simulationLocked.value = false
  }
}

watch(currentCode, refreshSimulationState, { immediate: true })

function onSimulationUpdated(event) {
  const code = event.detail?.code
  if (!code || code === currentCode.value) {
    refreshSimulationState(currentCode.value)
  }
}

onMounted(() => {
  window.addEventListener('wxz-simulation-updated', onSimulationUpdated)
})
onUnmounted(() => {
  window.removeEventListener('wxz-simulation-updated', onSimulationUpdated)
})

const simulationTab = (code) => ({
  key: 'simulation',
  label: '仿真预习',
  color: '#3b82f6',
  icon: 'M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z',
  iconFill: 'M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z',
  to: simulationPrepRoute(code)
})

const tabs = computed(() => {
  const code = currentCode.value
  const labTo = code ? { name: 'lab', query: { exp: code } } : { name: 'lab' }
  const reportTo = code
    ? { name: 'after-center', params: { code }, query: { tab: 'report' } }
    : labTo
  const monitorTo = code
    ? { name: 'lab-monitor', query: { exp: code } }
    : { name: 'lab-monitor' }

  if (simulationLocked.value && code) {
    return [simulationTab(code)]
  }

  const items = []
  if (code) {
    items.push(simulationTab(code))
  }

  items.push(
    {
      key: 'lab',
      label: '实验台',
      color: '#06b6d4',
      icon: 'M4 4h16a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V6a2 2 0 012-2zM3 19h18M6 11l2-3 2 4 2-2 2 1 2-3',
      iconFill: 'M4 4h16a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V6a2 2 0 012-2z',
      to: labTo
    },
    {
      key: 'monitor',
      label: '监控',
      color: '#f59e0b',
      icon: 'M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z',
      iconFill: 'M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z',
      to: monitorTo
    },
    {
      key: 'report',
      label: '实验报告',
      color: '#8b5cf6',
      icon: 'M14 3v4a1 1 0 001 1h4M9 13h6m-6 4h6M7 21h10a2 2 0 002-2V8l-5-5H7a2 2 0 00-2 2v14a2 2 0 002 2z',
      iconFill: 'M7 3a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5H7z',
      to: reportTo
    }
  )

  return items
})

const LAB_ROUTES = ['lab']
const SIMULATION_ROUTES = ['prep-simulation']
const MONITOR_ROUTES = ['lab-monitor']
const REPORT_ROUTES = ['after-center', 'after-report', 'after-review']

function isActive(tab) {
  if (tab.key === 'simulation') return SIMULATION_ROUTES.includes(route.name)
  if (tab.key === 'lab') return LAB_ROUTES.includes(route.name)
  if (tab.key === 'monitor') return MONITOR_ROUTES.includes(route.name)
  if (tab.key === 'report') return REPORT_ROUTES.includes(route.name)
  return false
}
</script>

<style scoped>
.tab-bar {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 4px;
  height: 100%;
}
.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 4px 18px;
  position: relative;
  color: #94a3b8;
  white-space: nowrap;
  transition: all 0.2s;
  border-radius: 8px 8px 0 0;
}
.tab-item:hover {
  color: #64748b;
  background: rgba(0, 0, 0, 0.03);
}
.tab-item:hover .tab-icon {
  color: #94a3b8;
}
.tab-item--active .tab-icon {
  color: var(--icon-color, #4f46e5);
}
.tab-item--active .tab-label {
  color: #0f172a;
  font-weight: 700;
}
.tab-item--active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 18px;
  right: 18px;
  height: 2px;
  background: var(--icon-color, #4f46e5);
  border-radius: 999px;
}
.tab-icon {
  width: 26px;
  height: 26px;
  color: #cbd5e1;
  transition: color 0.2s;
}
.tab-label {
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

@media (max-width: 980px), (max-height: 760px) {
  .tab-bar {
    justify-content: center;
  }
  .tab-item {
    padding: 3px 12px;
  }
  .tab-icon {
    width: 22px;
    height: 22px;
  }
  .tab-item--active::after {
    left: 12px;
    right: 12px;
  }
}
</style>
