<template>
  <header class="lab-toolbar">
    <div class="toolbar-left">
      <span class="chip" :class="envChipClass">
        <span class="w-1.5 h-1.5 rounded-full" :class="envDotClass" />
        环境 {{ envLevelLabel }}
      </span>
      <span class="chip" :class="difyChipClass" :title="difyStatusTitle">
        <span class="w-1.5 h-1.5 rounded-full" :class="difyDotClass" />
        AI {{ difyAvailable ? '在线' : '离线' }}
      </span>
    </div>

    <div class="toolbar-right">
      <span v-if="sessionFinished" class="finished-badge">
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
        </svg>
        实验已结束
      </span>

      <template v-if="!sessionFinished">
        <button
          v-if="experimentCode"
          type="button"
          class="btn-ghost px-2.5 py-1.5 rounded-lg text-xs font-semibold relative"
          title="暂未开放"
          @click="showComingSoon"
        >
          资料
          <span class="coming-soon-badge">暂未开放</span>
        </button>

        <button
          v-if="experimentCode"
          type="button"
          class="btn-ghost px-2.5 py-1.5 rounded-lg text-xs font-semibold relative"
          title="暂未开放"
          @click="showComingSoon"
        >
          复盘
          <span class="coming-soon-badge">暂未开放</span>
        </button>

        <button
          type="button"
          class="btn-ghost flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold btn-active-scale"
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
          class="btn-brand flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold"
          title="结束本次实验并生成当堂小结"
          @click="$emit('report')"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          结束实验
        </button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  envLevel: { type: String, default: 'L0' },
  difyStatus: { type: Object, default: null },
  difyStatusLoading: { type: Boolean, default: false },
  sessionFinished: { type: Boolean, default: false }
})

defineEmits(['quick-stats', 'report'])

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
    NA: 'text-slate-500',
    L0: 'text-emerald-600',
    L1: 'text-amber-600',
    L2: 'text-red-600',
    L3: 'text-red-600'
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
    ? 'text-emerald-600'
    : 'text-red-600'
)

const difyDotClass = computed(() => {
  if (props.difyStatusLoading || !props.difyStatus) return 'bg-slate-300'
  return difyAvailable.value ? 'bg-emerald-500' : 'bg-red-500'
})

const difyStatusTitle = computed(() => {
  if (props.difyStatusLoading) return '正在检查 AI 服务状态'
  if (!props.difyStatus) return 'AI 服务状态未知'
  return difyAvailable.value ? 'AI 服务可用' : 'AI 服务不可用'
})
</script>

<style scoped>
.lab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  height: 40px;
  background: #fff;
  border-bottom: 1px solid #e4e9f3;
  flex-shrink: 0;
  position: relative;
  z-index: 20;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

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

.finished-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 6px;
  background: #ecfdf5;
  color: #059669;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .lab-toolbar {
    padding: 0 8px;
  }
  .chip {
    padding: 2px 6px;
    font-size: 10px;
  }
}
</style>
