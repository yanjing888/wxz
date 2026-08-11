<template>
  <StagePanel layout="single" title="实验概览" desc="来自本实验讲义，进实验室前先建立整体印象。">
    <section class="card">
      <h3 class="card-title">操作环节</h3>
      <ol v-if="steps.length" class="step-list">
        <li v-for="(label, index) in steps" :key="index" class="step-row">
          <span class="step-no">{{ index + 1 }}</span>
          <span class="step-label">{{ label }}</span>
        </li>
      </ol>
      <p v-else class="empty-text">本实验未配置步骤概览。</p>
    </section>

    <section v-if="knowledge.length" class="card">
      <h3 class="card-title">教材要点</h3>
      <ul class="bullet-list">
        <li v-for="(item, index) in knowledge" :key="index">{{ item }}</li>
      </ul>
    </section>

    <section class="next-card">
      <div class="min-w-0">
        <p class="next-title">先完成预习任务，再进实验台</p>
        <p class="next-desc">核对器材、看预习要点、做完自测；文书在「实验文书」里撰写。</p>
      </div>
      <button type="button" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold shrink-0" @click="$emit('next', 'equipment')">
        去核对器材
      </button>
    </section>
  </StagePanel>
</template>

<script setup>
import { computed } from 'vue'
import StagePanel from './StagePanel.vue'

const props = defineProps({
  config: { type: Object, default: null }
})
defineEmits(['next'])

const steps = computed(() => props.config?.menuLabels || [])
const knowledge = computed(() => props.config?.reportKnowledge || [])
</script>

<style scoped>
.card { @apply rounded-2xl border border-line-soft bg-white p-5; }
.card-title { @apply text-[14px] font-bold text-ink-strong mb-3; }
.empty-text { @apply text-[13px] text-ink-faint; }

.step-list { @apply space-y-2; }
.step-row { @apply flex items-center gap-3; }
.step-no {
  @apply w-6 h-6 rounded-lg bg-surface-muted text-ink-muted text-[12px] font-bold
    flex items-center justify-center shrink-0;
}
.step-label { @apply text-[14px] text-ink-base; }

.bullet-list { @apply space-y-1.5 text-[13.5px] text-ink-base leading-relaxed list-disc pl-5; }

.next-card { @apply flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-brand-50 border border-brand-100 p-5; }
.next-title { @apply text-[15px] font-bold text-ink-strong; }
.next-desc { @apply text-[13px] text-ink-muted mt-0.5; }
</style>
