<template>
  <section class="stage-panel flex flex-col flex-1 min-h-0">
    <header v-if="title || $slots.actions" class="panel-head shrink-0">
      <div class="min-w-0">
        <h3 class="panel-title">{{ title }}</h3>
        <p v-if="desc" class="panel-desc">{{ desc }}</p>
      </div>
      <div class="panel-actions">
        <span v-if="unavailable" class="panel-warn">未接入 AI 服务</span>
        <slot name="actions" />
      </div>
    </header>

    <div v-if="layout === 'split'" class="panel-body panel-body--split flex-1 min-h-0">
      <aside class="input-pane custom-scroll">
        <slot name="input" />
      </aside>
      <div class="result-pane custom-scroll">
        <slot name="result">
          <div class="result-empty">
            <p class="font-semibold text-ink-strong">{{ emptyTitle }}</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">{{ emptyHint }}</p>
          </div>
        </slot>
      </div>
    </div>

    <div v-else-if="layout === 'chat'" class="panel-body flex-1 min-h-0 flex flex-col overflow-hidden">
      <slot />
    </div>

    <div v-else class="panel-body panel-body--single flex-1 min-h-0 overflow-y-auto custom-scroll">
      <div class="single-inner">
        <slot />
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  layout: { type: String, default: 'split' },
  unavailable: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '结果将显示在这里' },
  emptyHint: { type: String, default: '在左侧填写内容后开始。' }
})
</script>

<style scoped>
.stage-panel { @apply bg-white; }

.panel-head {
  @apply flex flex-wrap items-start justify-between gap-3 px-5 py-3 border-b border-line-soft bg-white;
}
.panel-title { @apply text-[15px] font-bold text-ink-strong; }
.panel-desc { @apply text-[12.5px] text-ink-muted mt-0.5; }
.panel-actions { @apply flex items-center gap-2 shrink-0; }
.panel-warn {
  @apply text-[11px] px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100;
}

.panel-body--split {
  @apply grid grid-cols-1 lg:grid-cols-[minmax(280px,360px)_1fr] bg-line-soft gap-px;
}
.input-pane { @apply bg-white p-5 overflow-y-auto min-h-0 space-y-4; }
.result-pane { @apply bg-white p-5 overflow-y-auto min-h-[240px] lg:min-h-0; }
.result-empty { @apply flex flex-col items-center justify-center text-center min-h-[220px] py-10; }

.panel-body--single { @apply bg-surface-soft/40; }
.single-inner { @apply max-w-[900px] mx-auto w-full px-5 py-6 space-y-5; }
</style>
