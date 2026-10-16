<template>
  <ExperimentSelect
    :experiments="availableExperiments"
    :experiment-code="currentCode"
    :switching="switching"
    @experiment-change="onSwitch"
  />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useLabStore } from '../../stores/lab'
import { studentExperimentApi } from '../../api'
import { labEntryBlockedRoute } from '../../utils/prepSimulation'
import ExperimentSelect from './ExperimentSelect.vue'
import { useTaskStore } from '../../stores/tasks'

const router = useRouter()
const route = useRoute()
const lab = useLabStore()
const tasks = useTaskStore()
const availableExperiments = computed(() => tasks.loaded
  ? lab.experiments.filter(experiment => tasks.items.some(item => item.experimentCode === experiment.code && (item.currentTask || item.labCompleted)))
  : lab.experiments)
const switching = ref(false)

const currentCode = computed(() =>
  String(route.params.code || route.query.exp || tasks.current?.experimentCode || lab.experiment?.code || '').trim()
)

onMounted(async () => {
  await tasks.load().catch(() => {})
  if (!lab.experiments.length) {
    try {
      await lab.loadExperiments()
    } catch {
      // 下拉为空时由 ExperimentSelect 禁用
    }
  }
})

async function onSwitch(code) {
  if (!code || code === currentCode.value) return
  switching.value = true
  try {
    await lab.loadExperiment(code)
    const { data: progress } = await studentExperimentApi.getProgress(code)
    const block = labEntryBlockedRoute(progress, code)
    if (block) {
      await router.replace(block)
      return
    }
    await router.replace({ name: 'lab', query: { exp: code } })
  } catch {
    // ExperimentSelect 会回退显示
  } finally {
    switching.value = false
  }
}
</script>
