<template>
  <div class="shrink-0 border-b border-[#dbe3ee] p-3 space-y-2 bg-white">
    <div class="flex items-start justify-between gap-2">
      <h3 class="text-[10px] font-bold uppercase tracking-widest text-slate-500 flex items-center gap-2 min-w-0">
        <span class="w-2 h-2 rounded-full shrink-0" :class="camUiActive ? 'bg-emerald-400' : 'bg-slate-300'" />
        <span class="truncate">实验台画面</span>
      </h3>
      <label class="flex items-center gap-2 shrink-0 cursor-pointer select-none">
        <span class="text-[9px] text-slate-600">定期检查</span>
        <input type="checkbox" class="accent-blue-500 w-4 h-4" :checked="envCheckEnabled" @change="$emit('toggle-env', $event.target.checked)" />
      </label>
    </div>
    <p class="text-[9px] text-slate-600 leading-snug line-clamp-3">
      开启<strong class="text-slate-500">定期检查</strong>后，系统<strong class="text-slate-500">约每 1 分钟</strong>自动跑一次环境等级（无需按键）；<strong class="text-slate-500">立即检查</strong>可随时补跑一次。<strong class="text-slate-500">严重（L3）</strong>记入实验报告。摄像头功能暂未接入，当前为 UI 交互演示。
    </p>

    <div class="flex gap-2 items-stretch">
      <div class="relative w-[140px] h-[79px] shrink-0 rounded-lg overflow-hidden border border-[#dbe3ee] bg-[#1e293b]">
        <div v-if="camUiActive" class="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-br from-slate-800 to-slate-950">
          <div class="w-full h-full opacity-40 bg-[repeating-linear-gradient(0deg,transparent,transparent_2px,rgba(255,255,255,.03)_2px,rgba(255,255,255,.03)_4px)]" />
          <p class="absolute bottom-1 left-1 text-[7px] text-emerald-400 font-mono">DEMO · 无真实画面</p>
        </div>
        <div v-else class="absolute inset-0 flex flex-col items-center justify-center text-center px-1 py-1">
          <p class="text-[8px] text-slate-400 leading-tight mb-1">暂无画面</p>
          <button type="button" class="px-2 py-0.5 rounded-md bg-blue-600 hover:bg-blue-500 text-[8px] font-bold text-white btn-active-scale" @click="startCamUi">开启</button>
        </div>
        <div v-if="flash" class="pointer-events-none absolute inset-0 bg-white opacity-30 transition-opacity duration-300" />
      </div>
      <div class="flex-1 flex flex-col gap-1 min-w-0 justify-between py-0.5">
        <div class="flex justify-end gap-1">
          <button v-if="camUiActive" type="button" class="px-2 py-1 rounded-lg border border-slate-300 bg-white text-slate-500 hover:bg-slate-50 text-[8px]" @click="stopCamUi">关闭</button>
          <button type="button" class="px-2 py-1 rounded-lg border border-slate-300 bg-white text-slate-600 hover:text-slate-800 hover:border-slate-400 hover:bg-slate-50 btn-active-scale text-[9px]" :disabled="envCheckRunning" @click="manualCheck">
            {{ envCheckRunning ? '检查中…' : '立即检查' }}
          </button>
        </div>
        <div class="rounded-md px-2 py-1.5 flex items-center gap-1.5 bg-[#f8fafc] border border-[#e8edf4]">
          <span class="text-[8px] text-slate-600 shrink-0">等级</span>
          <span class="text-[9px] font-bold px-1.5 py-0.5 rounded border shrink-0" :class="levelClass">{{ envLevel }}</span>
          <p class="text-[9px] text-slate-500 flex-1 min-w-0 leading-tight line-clamp-2">{{ envHint }}</p>
        </div>
        <p class="text-[8px] text-slate-600 leading-none truncate">L0合规 · L1提醒 · L2警示 · <span class="text-red-500">L3严重</span></p>
      </div>
    </div>

    <div class="rounded-lg overflow-hidden border border-[#e8edf4] bg-[#f8fafc]">
      <div class="flex items-center justify-between px-2 py-1 border-b border-[#e8edf4] bg-[#f1f5f9]">
        <span class="text-[9px] text-slate-600 font-medium">近期记录</span>
      </div>
      <div class="h-[72px] overflow-y-auto custom-scroll px-2 py-1 space-y-0.5">
        <p v-if="!envLogs.length" class="text-[9px] text-slate-400 py-2 text-center">暂无记录</p>
        <div v-for="(log, i) in envLogs" :key="i" class="text-[9px] text-slate-600 flex gap-1">
          <span class="font-mono text-slate-400 shrink-0">{{ log.time }}</span>
          <span class="font-bold shrink-0" :class="logLevelClass(log.level)">{{ log.level }}</span>
          <span class="truncate">{{ log.summary }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  envCheckEnabled: { type: Boolean, default: true },
  envLevel: { type: String, default: 'L0' },
  envHint: { type: String, default: '暂无异常' },
  envLogs: { type: Array, default: () => [] },
  envCheckRunning: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-env', 'env-check'])

const cameraEnabled = false
const camUiActive = ref(false)
const flash = ref(false)

const levelClass = computed(() => {
  const map = {
    L0: 'bg-emerald-50 text-emerald-600 border-emerald-200',
    L1: 'bg-amber-50 text-amber-600 border-amber-200',
    L2: 'bg-orange-50 text-orange-600 border-orange-200',
    L3: 'bg-red-50 text-red-600 border-red-200'
  }
  return map[props.envLevel] || map.L0
})

function logLevelClass(level) {
  if (level === 'L3') return 'text-red-500'
  if (level === 'L2') return 'text-orange-500'
  if (level === 'L1') return 'text-amber-500'
  return 'text-emerald-500'
}

function startCamUi() {
  camUiActive.value = true
}

function stopCamUi() {
  camUiActive.value = false
}

async function manualCheck() {
  flash.value = true
  setTimeout(() => { flash.value = false }, 320)
  emit('env-check')
}
</script>
