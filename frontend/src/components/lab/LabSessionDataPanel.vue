<template>
  <div class="data-panel flex-1 min-h-0 flex flex-col overflow-hidden bg-white">
      <header class="panel-head shrink-0">
      <h3 class="panel-title">我的数据</h3>
      <p class="panel-desc">
        已保存的测量值。需要判断是否靠谱时，切到「数据体检」。
      </p>
      <div class="subtabs">
        <button
          type="button"
          class="subtab"
          :class="{ 'subtab--active': dataTab === 'records' }"
          @click="dataTab = 'records'"
        >
          已存数据
          <span v-if="entries.length" class="subtab-badge">{{ entries.length }}</span>
        </button>
        <button
          type="button"
          class="subtab"
          :class="{ 'subtab--active': dataTab === 'doctor' }"
          @click="dataTab = 'doctor'"
        >
          数据体检
        </button>
      </div>
      <p v-if="dataTab === 'doctor'" class="panel-doctor-hint">
        先本地检查离散度与离群值，再让 AI 判断物理上是否说得通。
      </p>
    </header>

    <div v-show="dataTab === 'records'" class="panel-body flex-1 min-h-0 overflow-y-auto custom-scroll px-5 pb-5">
      <div v-if="!sessionId" class="empty">请先开始实验会话。</div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <div v-else-if="!entries.length" class="empty-box">
        <p class="font-semibold text-ink-strong">还没有记录数据</p>
        <p class="text-[13px] text-ink-muted mt-2 leading-relaxed">
          在当前步骤用左侧工具栏读取仪器或拍照识别读数，确认后发送会保存在这里。
        </p>
        <button type="button" class="btn-ghost mt-4 px-4 py-2 rounded-xl text-[13px] border border-line-soft" @click="dataTab = 'doctor'">
          先去做数据体检
        </button>
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

    <div v-show="dataTab === 'doctor'" class="panel-body flex-1 min-h-0 overflow-hidden">
      <DataDoctorPanel
        class="doctor-embed"
        :experiment-code="experimentCode"
        :experiment-name="experimentName"
        :show-next-actions="false"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { sessionApi } from '../../api'
import DataDoctorPanel from '../stage/DataDoctorPanel.vue'

const props = defineProps({
  sessionId: { type: Number, default: 0 },
  stepTitle: { type: String, default: '' },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  readOnly: { type: Boolean, default: false }
})

const dataTab = ref('records')

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
.panel-doctor-hint { @apply text-[12px] text-ink-muted mt-2; }
.subtabs { @apply flex gap-2 mt-3; }
.subtab {
  @apply inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12.5px] font-semibold
    text-ink-muted border border-transparent hover:bg-surface-soft transition-colors;
}
.subtab--active { @apply bg-brand-50 text-brand-700 border-brand-100; }
.subtab-badge {
  @apply min-w-[1.1rem] h-[1.1rem] px-1 rounded-full bg-brand-600 text-white text-[10px]
    flex items-center justify-center tabular-nums;
}
.doctor-embed { @apply h-full min-h-0; }
.doctor-embed :deep(.stage-panel) { @apply h-full border-0 rounded-none shadow-none; }
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
