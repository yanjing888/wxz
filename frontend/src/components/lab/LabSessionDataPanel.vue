<template>
  <div class="data-panel flex-1 min-h-0 flex flex-col overflow-hidden bg-white">
    <header class="panel-head shrink-0">
      <h3 class="panel-title">本次实验数据</h3>
      <p class="panel-desc">左侧点「读取仪器数据」或「拍照读数」录入；这里查看已保存的各步测量值。</p>
    </header>

    <div class="panel-body flex-1 min-h-0 overflow-y-auto custom-scroll px-5 pb-5">
      <div v-if="!sessionId" class="empty">请先开始实验会话。</div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <div v-else-if="!entries.length" class="empty-box">
        <p class="font-semibold text-ink-strong">还没有记录数据</p>
        <p class="text-[13px] text-ink-muted mt-2 leading-relaxed">
          在当前步骤用左侧工具栏读取仪器或拍照识别读数，发送后会自动保存在这里。
        </p>
      </div>

      <ul v-else class="entry-list">
        <li v-for="item in entries" :key="item.stepId" class="entry-card">
          <div class="entry-head">
            <span class="entry-step">步骤 {{ item.stepId }}</span>
            <span class="entry-name">{{ item.stepTitle || '测量' }}</span>
            <span v-if="item.createdAt" class="entry-time">{{ formatTime(item.createdAt) }}</span>
          </div>
          <dl class="value-grid">
            <template v-for="(val, key) in item.values" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ formatVal(val) }}</dd>
            </template>
          </dl>
          <p v-if="item.feedback" class="entry-feedback">{{ item.feedback }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { sessionApi } from '../../api'

const props = defineProps({
  sessionId: { type: Number, default: 0 },
  stepTitle: { type: String, default: '' },
  readOnly: { type: Boolean, default: false }
})

const loading = ref(false)
const byStep = ref({})

const entries = computed(() =>
  Object.values(byStep.value || {}).sort((a, b) => Number(a.stepId) - Number(b.stepId))
)

onMounted(load)
watch(() => props.sessionId, load)

async function load() {
  if (!props.sessionId) {
    byStep.value = {}
    return
  }
  loading.value = true
  try {
    const { data } = await sessionApi.getData(props.sessionId)
    byStep.value = data?.byStep || {}
  } catch {
    byStep.value = {}
  } finally {
    loading.value = false
  }
}

function formatVal(val) {
  if (val == null) return '—'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function formatTime(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

defineExpose({ reload: load })
</script>

<style scoped>
.panel-head { @apply px-5 pt-4 pb-3 border-b border-line-soft; }
.panel-title { @apply text-[15px] font-bold text-ink-strong; }
.panel-desc { @apply text-[12.5px] text-ink-muted mt-1; }
.empty { @apply py-12 text-center text-[13px] text-ink-muted; }
.empty-box { @apply rounded-2xl border border-dashed border-line-soft bg-surface-soft/50 px-5 py-8 text-center; }
.entry-list { @apply space-y-3; }
.entry-card { @apply rounded-xl border border-line-soft bg-surface-soft/30 p-4; }
.entry-head { @apply flex flex-wrap items-center gap-2 mb-3; }
.entry-step {
  @apply text-[11px] font-bold px-2 py-0.5 rounded-md bg-brand-50 text-brand-700;
}
.entry-name { @apply text-[13px] font-semibold text-ink-strong; }
.entry-time { @apply text-[11px] text-ink-faint ml-auto; }
.value-grid {
  @apply grid gap-x-4 gap-y-1 text-[13px];
  grid-template-columns: auto 1fr;
}
.value-grid dt { @apply text-ink-muted font-medium; }
.value-grid dd { @apply text-ink-strong font-semibold tabular-nums; }
.entry-feedback { @apply mt-3 text-[12.5px] text-ink-muted leading-relaxed border-t border-line-soft pt-2; }
</style>
