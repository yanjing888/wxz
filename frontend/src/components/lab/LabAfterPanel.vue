<template>
  <div class="after-panel flex-1 min-h-0 overflow-y-auto custom-scroll bg-white px-5 py-5">
    <header class="mb-5">
      <h3 class="text-[15px] font-bold text-ink-strong">课后整理</h3>
      <p class="text-[12.5px] text-ink-muted mt-1 leading-relaxed">
        课上时间留给操作。下课用今天的真实记录写报告、做复盘。
      </p>
    </header>

    <ol class="action-list">
      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">用今天的数据写报告</p>
          <p class="action-desc">基于本次测量与纠错记录起稿、润色、查缺漏。</p>
        </div>
        <router-link
          v-if="experimentCode"
          :to="{ name: 'after-report', params: { code: experimentCode } }"
          class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0"
        >
          写报告
        </router-link>
      </li>

      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">个性复盘</p>
          <p class="action-desc">3 条薄弱点、1 条误差假设、1 条下次行动。</p>
        </div>
        <router-link
          v-if="experimentCode"
          :to="{ name: 'after-review', params: { code: experimentCode } }"
          class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0 border border-line-soft"
        >
          去复盘
        </router-link>
      </li>

      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">当堂小结</p>
          <p class="action-desc">根据本次操作记录生成小结，方便写报告。</p>
        </div>
        <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0 border border-line-soft" @click="$emit('open-summary')">
          生成小结
        </button>
      </li>

      <li class="action-card action-card--muted">
        <div class="min-w-0">
          <p class="action-title">课余更多能力</p>
          <p class="action-desc">预习自测、器材核对、原理答疑等（课上不必打开）。</p>
        </div>
        <router-link
          :to="{ name: 'agents' }"
          class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0 border border-line-soft"
        >
          打开
        </router-link>
      </li>
    </ol>

    <p v-if="sessionFinished" class="done-tip">本次实验会话已结束，建议完成报告与复盘。</p>
  </div>
</template>

<script setup>
defineProps({
  experimentCode: { type: String, default: '' },
  sessionFinished: { type: Boolean, default: false }
})
defineEmits(['open-summary'])
</script>

<style scoped>
.action-list { @apply space-y-3; }
.action-card {
  @apply flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line-soft
    bg-surface-soft/40 p-4;
}
.action-card--muted { @apply bg-white; }
.action-title { @apply text-[14px] font-bold text-ink-strong; }
.action-desc { @apply text-[12.5px] text-ink-muted mt-0.5; }
.done-tip { @apply mt-5 text-[12.5px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-xl px-4 py-3; }
</style>
