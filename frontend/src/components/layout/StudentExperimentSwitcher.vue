<template>
  <ExperimentSelect
    :experiments="lab.experiments"
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

const router = useRouter()
const route = useRoute()
const lab = useLabStore()
const switching = ref(false)

const currentCode = computed(() =>
  String(route.params.code || route.query.exp || lab.experiment?.code || '').trim()
)

onMounted(async () => {
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
