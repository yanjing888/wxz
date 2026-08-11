<template>
  <div class="after-panel flex-1 min-h-0 overflow-y-auto custom-scroll bg-white px-5 py-5">
    <header class="mb-5">
      <h3 class="text-[15px] font-bold text-ink-strong">课后整理</h3>
      <p class="text-[12.5px] text-ink-muted mt-1 leading-relaxed">
        实验课里优先在「指导纠错」「实验数据」完成操作；下课前后在这里写报告、复盘并归档资料。
      </p>
    </header>

    <ol class="action-list">
      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">当堂小结</p>
          <p class="action-desc">根据本次操作记录生成课堂小结，便于课后写报告。</p>
        </div>
        <button type="button" class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0" @click="$emit('open-summary')">
          生成小结
        </button>
      </li>

      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">实验报告</p>
          <p class="action-desc">结合 session 数据与 AI 辅助撰写，可导出并存入资料库。</p>
        </div>
        <router-link
          v-if="experimentCode"
          :to="{ name: 'after-report', params: { code: experimentCode } }"
          class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0 border border-line-soft"
        >
          写报告
        </router-link>
      </li>

      <li class="action-card">
        <div class="min-w-0">
          <p class="action-title">评价复盘</p>
          <p class="action-desc">回顾薄弱步骤，梳理思考题作答思路。</p>
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
          <p class="action-title">实验资料</p>
          <p class="action-desc">下载照片、数据表、报告等，交给教师或留档。</p>
        </div>
        <router-link
          v-if="experimentCode"
          :to="{ name: 'files', query: { exp: experimentCode } }"
          class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0 border border-line-soft"
        >
          查看资料
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
.action-title { @apply text-[14px] font-bold text-ink-strong; }
.action-desc { @apply text-[12.5px] text-ink-muted mt-0.5; }
.done-tip { @apply mt-5 text-[12.5px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-xl px-4 py-3; }
</style>
