<template>

  <header class="shrink-0 flex items-center gap-3 px-5 h-12 bg-white border-b border-line-soft relative z-30">

    <div class="flex items-center gap-3 min-w-0 flex-1">

      <ExperimentSelect

        :experiments="experiments"

        :experiment-code="experimentCode"

        :switching="switching"

        @experiment-change="(code) => $emit('experiment-change', code)"

      />

      <div class="hidden sm:flex items-center gap-2 shrink-0">

        <span class="chip shrink-0" :class="envChipClass">

          <span class="w-1.5 h-1.5 rounded-full" :class="envDotClass" />

          环境 {{ envLevelLabel }}

        </span>

        <span

          class="chip shrink-0"

          :class="difyChipClass"

          :title="difyStatusTitle"

        >

          <span class="w-1.5 h-1.5 rounded-full" :class="difyDotClass" />

          AI {{ difyAvailable ? '在线' : '离线' }}

        </span>

      </div>

    </div>



    <div class="flex items-center gap-1.5 shrink-0">

      <button
        v-if="experimentCode"
        type="button"
        class="btn-ghost px-2.5 py-2 rounded-xl text-xs font-semibold hidden md:inline-flex relative"
        title="暂未开放"
        @click="showComingSoon"
      >
        资料
        <span class="coming-soon-badge">暂未开放</span>
      </button>

      <button
        v-if="experimentCode"
        type="button"
        class="btn-ghost px-2.5 py-2 rounded-xl text-xs font-semibold hidden lg:inline-flex relative"
        title="暂未开放"
        @click="showComingSoon"
      >
        复盘
        <span class="coming-soon-badge">暂未开放</span>
      </button>

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

        title="结束本次实验并生成当堂小结"

        @click="$emit('report')"

      >

        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">

          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />

        </svg>

        当堂小结

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

  envLevel: { type: String, default: 'L0' },

  difyStatus: { type: Object, default: null },

  difyStatusLoading: { type: Boolean, default: false },

  switching: { type: Boolean, default: false }

})



defineEmits(['quick-stats', 'report', 'experiment-change'])

function showComingSoon(e) {
  const btn = e.currentTarget
  const badge = btn.querySelector('.coming-soon-badge')
  if (!badge) return
  const rect = btn.getBoundingClientRect()
  badge.style.top = `${rect.bottom + 8}px`
  badge.style.left = `${rect.left + rect.width / 2}px`
  badge.classList.add('show')
  clearTimeout(btn._comingSoonTimer)
  btn._comingSoonTimer = setTimeout(() => {
    badge.classList.remove('show')
  }, 1800)
}



const envLevelLabel = computed(() => (props.envLevel === 'NA' ? '不可用' : props.envLevel))



const envChipClass = computed(() => {

  const map = {

    NA: 'text-slate-500 border-slate-200 bg-slate-50',

    L0: 'text-emerald-600 border-emerald-200 bg-emerald-50',

    L1: 'text-amber-600 border-amber-200 bg-amber-50',

    L2: 'text-red-600 border-red-200 bg-red-50',

    L3: 'text-red-600 border-red-200 bg-red-50'

  }

  return map[props.envLevel] || map.L0

})



const envDotClass = computed(() => {

  const map = { NA: 'bg-slate-400', L0: 'bg-emerald-500', L1: 'bg-amber-500', L2: 'bg-red-500', L3: 'bg-red-500' }

  return map[props.envLevel] || map.L0

})



const difyAvailable = computed(() => props.difyStatus?.available === true)



const difyChipClass = computed(() =>

  difyAvailable.value

    ? 'text-emerald-600 border-emerald-200 bg-emerald-50'

    : 'text-red-600 border-red-200 bg-red-50'

)



const difyDotClass = computed(() => {

  if (props.difyStatusLoading || !props.difyStatus) return 'bg-slate-300'

  return difyAvailable.value ? 'bg-emerald-500' : 'bg-red-500'

})



const difyStatusTitle = computed(() => {

  if (props.difyStatusLoading) return '正在检查 Dify 服务状态'

  if (!props.difyStatus) return 'Dify 服务状态未知'

  return difyAvailable.value ? 'Dify 服务可用' : 'Dify 服务不可用'

})

</script>

<style scoped>
.coming-soon-badge {
  position: fixed;
  z-index: 9999;
  white-space: nowrap;
  background: #1e293b;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 5px 12px;
  border-radius: 6px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  transform: translateX(-50%);
}
.coming-soon-badge.show {
  opacity: 1;
}
.coming-soon-badge::after {
  content: '';
  position: absolute;
  top: -5px;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-bottom-color: #1e293b;
}
</style>
