<template>
  <StagePanel
    layout="single"
    title="本次实验"
    desc="实验课时间有限：先看步骤，尽快进实验台操作；预习与报告不挡上课。"
  >
    <section class="hero">
      <div class="min-w-0">
        <p class="hero-title">准备好就开始做实验</p>
        <p class="hero-desc">AI 指导、拍照纠错、读数识别都在实验台里，边做边用。</p>
      </div>
      <router-link :to="labRoute" class="btn-brand px-6 py-2.5 rounded-xl text-[14px] font-bold shrink-0">
        进入实验台
      </router-link>
    </section>

    <section class="card">
      <h3 class="card-title">操作步骤</h3>
      <ol v-if="steps.length" class="step-list">
        <li v-for="(label, index) in steps" :key="index" class="step-row">
          <span class="step-no">{{ index + 1 }}</span>
          <span class="step-label">{{ label }}</span>
        </li>
      </ol>
      <p v-else class="empty-text">本实验未配置步骤概览。</p>
    </section>

    <div class="quick-row">
      <button type="button" class="quick-btn" :class="{ 'quick-btn--on': panel === 'ask' }" @click="toggle('ask')">
        问 AI
      </button>
      <button type="button" class="quick-btn" :class="{ 'quick-btn--on': panel === 'materials' }" @click="toggle('materials')">
        本实验资料
      </button>
    </div>

    <div v-if="panel === 'ask'" class="panel-slot">
      <AskPanel
        :experiment-code="experimentCode"
        :experiment-name="experimentName"
        :suggestions="askSuggestions"
      />
    </div>
    <div v-else-if="panel === 'materials'" class="panel-slot">
      <ExperimentMaterialsPanel :experiment-code="experimentCode" />
    </div>

    <details class="prep-fold">
      <summary class="prep-summary">课前预习（可选，建议课前或课后完成）</summary>
      <p class="prep-hint">器材核对、预习要点、自测题不占用上课时间；需要时再展开。</p>
      <div class="prep-tabs">
        <button
          v-for="tab in prepTabs"
          :key="tab.key"
          type="button"
          class="prep-tab"
          :class="{ 'prep-tab--on': prepTab === tab.key }"
          @click="prepTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="panel-slot">
        <EquipmentPanel
          v-if="prepTab === 'equipment'"
          :experiment-code="experimentCode"
          :experiment-name="experimentName"
        />
        <BriefPanel
          v-else-if="prepTab === 'brief'"
          :experiment-code="experimentCode"
          :experiment-name="experimentName"
          :knowledge="knowledge"
        />
        <QuizPanel
          v-else
          :experiment-code="experimentCode"
          :experiment-name="experimentName"
        />
      </div>
    </details>
  </StagePanel>
</template>

<script setup>
import { computed, ref } from 'vue'
import StagePanel from './StagePanel.vue'
import AskPanel from './AskPanel.vue'
import ExperimentMaterialsPanel from './ExperimentMaterialsPanel.vue'
import EquipmentPanel from './EquipmentPanel.vue'
import BriefPanel from './BriefPanel.vue'
import QuizPanel from './QuizPanel.vue'
import { PRE_CLASS_TABS } from '../../utils/experimentFlow'

const props = defineProps({
  config: { type: Object, default: null },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  knowledge: { type: Array, default: () => [] },
  askSuggestions: { type: Array, default: () => [] }
})

const panel = ref('')
const prepTab = ref('equipment')
const prepTabs = PRE_CLASS_TABS

const steps = computed(() => props.config?.menuLabels || [])
const labRoute = computed(() => ({ name: 'lab', query: { exp: props.experimentCode } }))

function toggle(key) {
  panel.value = panel.value === key ? '' : key
}
</script>

<style scoped>
.hero {
  @apply flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-brand-600 text-white p-5 mb-4;
}
.hero-title { @apply text-[16px] font-bold; }
.hero-desc { @apply text-[13px] text-white/85 mt-1; }

.card { @apply rounded-2xl border border-line-soft bg-white p-5 mb-4; }
.card-title { @apply text-[14px] font-bold text-ink-strong mb-3; }
.empty-text { @apply text-[13px] text-ink-faint; }

.step-list { @apply space-y-2; }
.step-row { @apply flex items-center gap-3; }
.step-no {
  @apply w-6 h-6 rounded-lg bg-surface-muted text-ink-muted text-[12px] font-bold
    flex items-center justify-center shrink-0;
}
.step-label { @apply text-[14px] text-ink-base; }

.quick-row { @apply flex flex-wrap gap-2 mb-3; }
.quick-btn {
  @apply px-4 py-2 rounded-xl border border-line-soft bg-white text-[13px] font-semibold text-ink-muted
    hover:border-brand-300 hover:text-brand-700 transition-colors;
}
.quick-btn--on { @apply border-brand-400 bg-brand-50 text-brand-700; }

.panel-slot { @apply mb-4; }

.prep-fold { @apply rounded-2xl border border-dashed border-line-soft bg-surface-soft/40 p-4; }
.prep-summary { @apply text-[13.5px] font-semibold text-ink-muted cursor-pointer select-none; }
.prep-hint { @apply text-[12.5px] text-ink-faint mt-2 mb-3; }
.prep-tabs { @apply flex flex-wrap gap-2 mb-3; }
.prep-tab {
  @apply px-3 py-1.5 rounded-lg text-[12.5px] text-ink-muted border border-line-soft bg-white;
}
.prep-tab--on { @apply bg-brand-50 border-brand-300 text-brand-700 font-semibold; }
</style>
