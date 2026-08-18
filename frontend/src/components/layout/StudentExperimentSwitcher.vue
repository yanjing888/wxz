<template>
  <ExperimentSelect
    :experiments="lab.experiments"
    :experiment-code="lab.experiment?.code || ''"
    :switching="lab.switchingExperiment"
    @experiment-change="onSwitch"
  />
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useLabStore } from '../../stores/lab'
import ExperimentSelect from './ExperimentSelect.vue'

const router = useRouter()
const route = useRoute()
const lab = useLabStore()

async function onSwitch(code) {
  try {
    await lab.switchExperiment(code)
    router.replace({ name: 'lab', query: { ...route.query, exp: code } })
  } catch (e) {
    // 静默失败，ExperimentSelect 会回退显示
  }
}
</script>
