<template>
  <AiToolShell :tool="tool">
    <div class="tool-page custom-scroll">
      <section class="panel space-y-4">
        <div v-for="section in sections" :key="section.key">
          <label class="field-label">{{ section.label }}</label>
          <textarea v-model="form[section.key]" class="field-textarea" rows="4" />
        </div>
        <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="loading || !hasContent" @click="review">
          {{ loading ? '批改中…' : '提交批改' }}
        </button>
      </section>

      <section v-if="resultText" class="panel mt-5 result-panel">
        <div class="chat-md" v-html="renderMd(resultText)" />
      </section>
    </div>
  </AiToolShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import AiToolShell from '../../components/ai/AiToolShell.vue'
import { aiToolApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({ tool: { type: Object, required: true } })

const sections = [
  { key: 'purpose', label: '实验目的' },
  { key: 'principle', label: '实验原理' },
  { key: 'data', label: '数据与处理' },
  { key: 'conclusion', label: '实验结论' },
  { key: 'discussion', label: '讨论与拓展' }
]

const form = reactive({
  purpose: '',
  principle: '',
  data: '',
  conclusion: '',
  discussion: ''
})

const loading = ref(false)
const resultText = ref('')

const hasContent = computed(() => Object.values(form).some((value) => String(value || '').trim()))

async function review() {
  loading.value = true
  try {
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'review',
      inputs: { ...form }
    })
    resultText.value = data.text || ''
  } finally {
    loading.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.tool-page { @apply max-w-[960px] mx-auto w-full px-6 py-6 overflow-y-auto h-full; }
.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.field-label { @apply block text-[13px] font-semibold text-ink-muted mb-2; }
.field-textarea { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.result-panel { @apply bg-surface-soft/40; }
</style>
