<template>
  <header class="shrink-0 flex items-center gap-4 px-5 h-14 bg-white/85 backdrop-blur-md border-b border-line-soft relative z-30">
    <!-- 品牌区 -->
    <div class="flex items-center gap-2.5 shrink-0">
      <div class="relative">
        <div class="w-9 h-9 brand-gradient rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-brand">智</div>
        <span class="absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-white" />
      </div>
      <div class="flex flex-col leading-tight">
        <span class="text-[9px] font-mono text-ink-faint tracking-widest">WUXIAOZHI · v1.0</span>
        <span class="text-[11px] font-bold text-ink-strong">物小智实验台</span>
      </div>
    </div>

    <span class="w-px h-7 bg-line-soft shrink-0" />

    <!-- 实验选择 + 状态 -->
    <div class="flex items-center gap-3 min-w-0 flex-1">
      <ExperimentSelect
        :experiments="experiments"
        :experiment-code="experimentCode"
        :switching="switching"
        @experiment-change="(code) => $emit('experiment-change', code)"
      />
      <div class="hidden sm:flex items-center gap-2 shrink-0">
        <span class="chip shrink-0">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-500" />
          进行中
        </span>
        <span class="chip shrink-0" :class="envChipClass">
          <span class="w-1.5 h-1.5 rounded-full" :class="envDotClass" />
          环境 {{ envLevel }}
        </span>
      </div>
    </div>

    <!-- 学生身份 -->
    <div class="flex items-center gap-2.5 shrink-0 pl-3 border-l border-line-soft">
      <div class="w-8 h-8 rounded-full brand-gradient flex items-center justify-center text-white text-xs font-bold shadow-card">
        {{ studentInitial }}
      </div>
      <div class="flex flex-col leading-tight">
        <span class="text-[10px] text-ink-faint">学生</span>
        <div class="flex items-center gap-1.5">
          <span class="text-xs font-semibold text-ink-base truncate max-w-[100px]">{{ studentName }}</span>
          <span v-if="studentClass" class="text-[9px] px-1.5 py-0.5 text-brand-700 bg-brand-50 border border-brand-100 rounded">
            {{ studentClass }}
          </span>
        </div>
      </div>
    </div>

    <!-- 操作按钮组 -->
    <div class="flex items-center gap-2 shrink-0 pl-3 border-l border-line-soft">
      <button
        type="button"
        class="btn-ghost flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold btn-active-scale"
        title="实验数据速览"
        @click="$emit('quick-stats')"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
        速览
      </button>
      <button
        type="button"
        class="btn-brand flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold"
        title="结束实验并生成总结报告"
        @click="$emit('report')"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        生成报告
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import ExperimentSelect from './ExperimentSelect.vue'

const props = defineProps({
  experiments: { type: Array, default: () => [] },
  experimentCode: { type: String, default: '' },
  studentName: { type: String, default: '--' },
  studentClass: { type: String, default: '' },
  envLevel: { type: String, default: 'L0' },
  switching: { type: Boolean, default: false }
})

defineEmits(['quick-stats', 'report', 'experiment-change'])

const studentInitial = computed(() => {
  const n = (props.studentName || '').trim()
  if (!n || n === '--') return '智'
  return n.charAt(0)
})

const envChipClass = computed(() => {
  const map = {
    L0: 'text-emerald-600 border-emerald-200 bg-emerald-50',
    L1: 'text-amber-600 border-amber-200 bg-amber-50',
    L2: 'text-orange-600 border-orange-200 bg-orange-50',
    L3: 'text-red-600 border-red-200 bg-red-50'
  }
  return map[props.envLevel] || map.L0
})

const envDotClass = computed(() => {
  const map = { L0: 'bg-emerald-500', L1: 'bg-amber-500', L2: 'bg-orange-500', L3: 'bg-red-500' }
  return map[props.envLevel] || map.L0
})
</script>
