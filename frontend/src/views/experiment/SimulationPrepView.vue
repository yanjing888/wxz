<template>
  <div class="sim-page flex flex-col flex-1 min-h-0 bg-[#0a0e17]">
    <header class="sim-head shrink-0">
      <div class="sim-head-inner">
        <div class="min-w-0">
          <p class="eyebrow">虚拟仿真 · 预习</p>
          <h1 class="title">{{ experimentName || simulationTitle }}</h1>
          <p v-if="viewMode === 'active'" class="sub">
            完成下方全部 5 步并计算出曲率半径 R 后，系统自动标记预习完成。
            <span v-if="simulationCompleted" class="ready-pill">已完成</span>
          </p>
        </div>
        <div v-if="viewMode === 'active'" class="head-actions">
          <button
            type="button"
            class="btn-enter"
            :disabled="!simulationCompleted || saving"
            @click="enterLab"
          >
            {{ simulationCompleted ? '进入实验台' : '请先完成仿真' }}
          </button>
        </div>
      </div>
    </header>

    <div v-if="loadError" class="sim-placeholder flex-1 flex items-center justify-center px-6">
      <div class="placeholder-box">
        <p class="placeholder-title">加载失败</p>
        <p class="placeholder-text">{{ loadError }}</p>
      </div>
    </div>

    <div v-else-if="viewMode === 'unavailable'" class="sim-placeholder flex-1 flex items-center justify-center px-6">
      <div class="placeholder-box">
        <p class="placeholder-kicker">虚拟仿真</p>
        <p class="placeholder-title">{{ experimentName }} 仿真尚未开放</p>
        <p class="placeholder-text">
          该实验的虚拟仿真还在开发中。你可以先切换其他已开放仿真的实验，或直接进入真实实验台进行实操。
        </p>
        <button type="button" class="btn-enter mt-4" @click="enterLab">进入实验台</button>
      </div>
    </div>

    <iframe
      v-else
      ref="frameRef"
      class="sim-frame flex-1 min-h-0 w-full border-0"
      :src="simulationUrl"
      title="牛顿环虚拟仿真"
      allow="fullscreen"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { experimentApi, studentExperimentApi } from '../../api'
import { rememberVisit } from '../../utils/experimentFlow'
const route = useRoute()
const router = useRouter()
const code = computed(() => String(route.params.code || '').trim())
const experimentName = ref('')
const simulationUrl = ref('')
const simulationTitle = ref('牛顿环实验虚拟仿真')
const simulationCompleted = ref(false)
const viewMode = ref('loading')
const saving = ref(false)
const loadError = ref('')
const frameRef = ref(null)

onMounted(() => {
  window.addEventListener('message', onFrameMessage)
  load()
})
onUnmounted(() => {
  window.removeEventListener('message', onFrameMessage)
})
watch(code, load)

async function load() {
  if (!code.value) return
  loadError.value = ''
  viewMode.value = 'loading'
  rememberVisit(code.value, 'task')
  try {
    const [cfgRes, progRes] = await Promise.all([
      experimentApi.get(code.value),
      studentExperimentApi.getProgress(code.value)
    ])
    experimentName.value = cfgRes.data?.name || code.value
    simulationTitle.value = cfgRes.data?.simulation?.title || experimentName.value
    simulationCompleted.value = !!progRes.data?.simulationCompleted

    const sim = cfgRes.data?.simulation
    if (!sim?.url) {
      viewMode.value = 'unavailable'
      return
    }
    simulationUrl.value = sim.url
    viewMode.value = 'active'
  } catch (e) {
    loadError.value = e.response?.data?.message || e.message || '加载失败'
    viewMode.value = 'error'
  }
}

function onFrameMessage(event) {
  const data = event.data
  if (!data || data.type !== 'wxz-simulation-complete') return
  if (data.experiment && data.experiment !== code.value) return
  markSimulationComplete()
}

async function markSimulationComplete() {
  if (simulationCompleted.value || saving.value) return
  saving.value = true
  try {
    const { data } = await studentExperimentApi.completeSimulation(code.value)
    simulationCompleted.value = !!data?.simulationCompleted
    window.dispatchEvent(new CustomEvent('wxz-simulation-updated', { detail: { code: code.value } }))
  } catch (e) {
    loadError.value = e.response?.data?.message || '保存预习进度失败'
  } finally {
    saving.value = false
  }
}

function enterLab() {
  router.push({ name: 'lab', query: { exp: code.value } })
}
</script>

<style scoped>
.sim-head {
  background: linear-gradient(180deg, #1a2332, #111827);
  border-bottom: 1px solid #2d3a4f;
}
.sim-head-inner {
  max-width: none;
  padding: 10px 20px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #60a5fa;
}
.title {
  font-size: 16px;
  font-weight: 700;
  color: #e5e7eb;
  margin-top: 2px;
}
.sub {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.ready-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(16, 185, 129, 0.15);
  color: #6ee7b7;
  border: 1px solid rgba(16, 185, 129, 0.35);
}
.head-actions { flex-shrink: 0; }
.btn-enter {
  padding: 8px 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: 1px solid #3b82f6;
  transition: opacity 0.2s;
}
.btn-enter:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.sim-frame {
  background: #0a0e17;
}
.sim-placeholder {
  background: radial-gradient(ellipse at center, #111827, #0a0e17);
}
.placeholder-box {
  max-width: 420px;
  text-align: center;
  padding: 28px 24px;
  border-radius: 16px;
  border: 1px solid #2d3a4f;
  background: #16202d;
}
.placeholder-kicker {
  font-size: 11px;
  font-weight: 700;
  color: #60a5fa;
  letter-spacing: 0.04em;
}
.placeholder-title {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: #e5e7eb;
}
.placeholder-text {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #9ca3af;
}
</style>
