<template>
  <div class="after-panel flex-1 min-h-0 overflow-y-auto custom-scroll bg-white px-5 py-5">
    <header class="mb-5">
      <h3 class="text-[15px] font-bold text-ink-strong">课后整理</h3>
      <p class="text-[12.5px] text-ink-muted mt-1">完成实验报告撰写</p>
    </header>

    <ol class="action-list">
      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">实验报告</p>
          <p class="action-desc">基于实验数据撰写并提交报告</p>
        </div>
        <router-link
          v-if="experimentCode"
          :to="{ name: 'after-report', params: { code: experimentCode } }"
          class="btn-brand px-4 py-2 text-[13px] font-semibold shrink-0"
        >
          写报告
        </router-link>
      </li>
    </ol>

    <p v-if="sessionFinished" class="done-tip">本次实验会话已结束，建议完成报告。</p>
  </div>
</template>

<script setup>
defineProps({
  experimentCode: { type: String, default: '' },
  sessionFinished: { type: Boolean, default: false }
})
</script>

<style scoped>
.action-list { @apply space-y-2; }
.action-card {
  @apply flex flex-wrap items-center justify-between gap-3 border border-line-soft
    bg-white p-4;
}
.action-title { @apply text-[14px] font-bold text-ink-strong; }
.action-desc { @apply text-[12.5px] text-ink-muted mt-0.5; }
.done-tip { @apply mt-5 text-[12.5px] text-emerald-700 bg-emerald-50 border border-emerald-100 px-4 py-3; }
</style>
