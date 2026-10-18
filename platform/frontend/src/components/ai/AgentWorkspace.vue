<template>
  <div class="agent-ws flex flex-col flex-1 min-h-0">
    <header class="ws-header shrink-0">
      <router-link to="/ai" class="back-btn" title="返回 AI 工具箱">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        工具箱
      </router-link>

      <div class="ws-title-block min-w-0">
        <div class="flex items-center gap-2 min-w-0">
          <span class="ws-icon" :class="`ws-icon--${agent?.tone || 'brand'}`">{{ agent?.icon || 'AI' }}</span>
          <h2 class="ws-title">{{ agent?.name || tool?.name || '智能体' }}</h2>
          <span v-if="tool && tool.available === false" class="ws-warn">未接入 AI 服务</span>
        </div>
        <p v-if="agent?.desc" class="ws-desc">{{ agent.desc }}</p>
      </div>

      <div class="ws-actions shrink-0">
        <select
          v-if="!hideExperiment"
          :value="experiment"
          class="exp-select"
          title="当前实验"
          @change="onExperimentChange"
        >
          <option value="">未选实验</option>
          <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
        </select>
        <slot name="actions" />
      </div>
    </header>

    <div v-if="layout === 'split'" class="ws-body ws-body--split flex-1 min-h-0">
      <aside class="input-pane custom-scroll">
        <slot name="input" />
      </aside>
      <section class="result-pane custom-scroll">
        <slot name="result">
          <div class="result-empty">
            <div class="result-empty-icon">{{ agent?.icon || 'AI' }}</div>
            <p class="font-semibold text-ink-strong">{{ emptyTitle }}</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">{{ emptyHint }}</p>
          </div>
        </slot>
      </section>
    </div>

    <div v-else-if="layout === 'chat'" class="ws-body flex-1 min-h-0 flex flex-col overflow-hidden">
      <slot />
    </div>

    <div v-else class="ws-body ws-body--single flex-1 min-h-0 overflow-y-auto custom-scroll">
      <div class="single-inner">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { experimentApi } from '../../api'
import { saveAgentExperiment } from '../../utils/agentCatalog'

const props = defineProps({
  agent: { type: Object, default: null },
  tool: { type: Object, default: null },
  experiment: { type: String, default: '' },
  layout: { type: String, default: 'split' },
  hideExperiment: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '结果将显示在这里' },
  emptyHint: { type: String, default: '在左侧填写内容后开始。' }
})

const emit = defineEmits(['update:experiment', 'experiment-change'])

const experiments = ref([])

onMounted(async () => {
  if (props.hideExperiment) return
  try {
    const { data } = await experimentApi.list()
    experiments.value = data || []
  } catch {
    experiments.value = []
  }
})

function onExperimentChange(event) {
  const code = event.target.value
  saveAgentExperiment(code)
  emit('update:experiment', code)
  emit('experiment-change', code)
}
</script>

<style scoped>
.agent-ws { @apply bg-surface-soft/40; }

.ws-header {
  @apply flex items-center gap-4 px-5 py-3 bg-white border-b border-line-soft;
}
.back-btn {
  @apply flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[13px] font-medium text-ink-muted
    hover:text-brand-600 hover:bg-brand-50 transition-colors shrink-0;
}
.ws-title-block { @apply flex-1 min-w-0; }
.ws-icon {
  @apply w-7 h-7 rounded-lg flex items-center justify-center text-[13px] font-bold text-white shrink-0;
}
.ws-icon--sky { @apply bg-sky-500; }
.ws-icon--violet { @apply bg-violet-500; }
.ws-icon--emerald { @apply bg-emerald-500; }
.ws-icon--amber { @apply bg-amber-500; }
.ws-icon--rose { @apply bg-rose-500; }
.ws-icon--brand { @apply bg-brand-600; }
.ws-title { @apply text-[16px] font-bold text-ink-strong truncate; }
.ws-warn {
  @apply text-[11px] px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100 shrink-0;
}
.ws-desc { @apply text-[12px] text-ink-muted mt-0.5 truncate; }
.ws-actions { @apply flex items-center gap-2; }
.exp-select {
  @apply rounded-lg border border-line-soft px-2.5 py-1.5 text-[13px] bg-white max-w-[180px];
}

.ws-body--split {
  @apply grid grid-cols-1 lg:grid-cols-[minmax(300px,380px)_1fr] bg-line-soft gap-px;
}
.input-pane {
  @apply bg-white p-5 overflow-y-auto min-h-0 space-y-4;
}
.result-pane {
  @apply bg-white p-5 overflow-y-auto min-h-[260px] lg:min-h-0;
}
.result-empty {
  @apply flex flex-col items-center justify-center text-center min-h-[240px] py-10;
}
.result-empty-icon {
  @apply w-12 h-12 rounded-2xl bg-surface-muted text-ink-faint flex items-center justify-center
    text-lg font-bold mb-3;
}

.ws-body--single { @apply bg-surface-soft/40; }
.single-inner { @apply max-w-[860px] mx-auto w-full px-5 py-6 space-y-5; }
</style>
