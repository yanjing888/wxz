<template>
  <div v-if="loading" class="page-root flex items-center justify-center text-sm text-ink-faint">加载中…</div>
  <div v-else-if="error" class="page-root flex items-center justify-center text-sm text-red-600 px-6 text-center">{{ error }}</div>
  <component v-else-if="tool && viewComponent" :is="viewComponent" :tool="tool" />
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiToolApi } from '../../api'
import DataLabToolView from './DataLabToolView.vue'
import ReportAssistToolView from './ReportAssistToolView.vue'
import LabRecapToolView from './LabRecapToolView.vue'

const ALLOWED_CODES = new Set(['data-lab', 'report-assist', 'lab-recap'])

const route = useRoute()
const router = useRouter()
const tool = ref(null)
const loading = ref(true)
const error = ref('')

const viewMap = {
  'data-lab': DataLabToolView,
  'report-assist': ReportAssistToolView,
  recap: LabRecapToolView
}

const viewComponent = computed(() => {
  if (!tool.value) return null
  return viewMap[tool.value.pageType] || viewMap[tool.value.code] || null
})

async function loadTool() {
  const code = route.params.code
  if (!ALLOWED_CODES.has(code)) {
    router.replace('/home')
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await aiToolApi.tools()
    tool.value = (data || []).find((item) => item.code === code)
    if (!tool.value) error.value = '功能不存在或未启用'
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadTool)
watch(() => route.params.code, loadTool)
</script>
