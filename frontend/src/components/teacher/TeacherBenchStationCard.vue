<template>
  <article class="bench-card">
    <header class="bench-head">
      <div>
        <strong>{{ benchLabel }}</strong>
        <span>{{ station.studentName }}</span>
        <span class="bench-meta">{{ station.studentClass || '未分班' }} · {{ statusText }}</span>
      </div>
      <span v-if="station.cameraActive" class="bench-tag live">学生监控中</span>
    </header>

    <TeacherCameraCardPreview
      :live="streamLive && cameraConfigured"
      :bench-camera="benchCamera"
      :browser-stream-url="streamUrl"
      :camera-configured="cameraConfigured"
      class="bench-preview"
    />

    <div class="bench-controls">
      <button
        type="button"
        class="env-toggle"
        role="switch"
        :aria-checked="envCheckEnabled"
        :disabled="!canInspect"
        @click="$emit('toggle-env', !envCheckEnabled)"
      >
        <span>自动巡检</span>
        <span class="env-switch" :class="{ on: envCheckEnabled }">
          <span class="env-knob" />
        </span>
      </button>
      <button
        type="button"
        class="bench-check-btn"
        :disabled="!canInspect || checking"
        @click="$emit('manual-check')"
      >
        {{ checking ? '检查中…' : '立即检查' }}
      </button>
    </div>

    <div class="bench-logs">
      <div class="bench-logs-head">
        <span>巡检记录</span>
        <span v-if="latestLevel" class="env-level" :class="levelClass(latestLevel)">{{ formatLevel(latestLevel) }}</span>
        <span class="bench-logs-count">{{ logs.length }} 条</span>
      </div>
      <p v-if="!logs.length" class="bench-logs-empty">暂无记录</p>
      <ul v-else class="bench-logs-list">
        <li v-for="log in visibleLogs" :key="log.id || log.createdAt">
          <span class="log-time">{{ formatTime(log.createdAt) }}</span>
          <span class="log-level" :class="levelClass(log.level)">{{ formatLevel(log.level) }}</span>
          <span class="log-text">{{ brief(log.summary) }}</span>
        </li>
      </ul>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import TeacherCameraCardPreview from '../monitor/TeacherCameraCardPreview.vue'

const props = defineProps({
  station: { type: Object, required: true },
  benchLabel: { type: String, required: true },
  streamLive: { type: Boolean, default: false },
  streamUrl: { type: String, default: '' },
  benchCamera: { type: Object, default: null },
  cameraConfigured: { type: Boolean, default: false },
  logs: { type: Array, default: () => [] },
  envCheckEnabled: { type: Boolean, default: false },
  checking: { type: Boolean, default: false },
  envCheckAvailable: { type: Boolean, default: true }
})

defineEmits(['toggle-env', 'manual-check'])

const canInspect = computed(() => props.envCheckAvailable && !!props.station.sessionId)

const statusText = computed(() => {
  if (props.station.status === 'ACTIVE') return props.station.stepTitle || '实验中'
  if (props.station.status === 'FINISHED') return '已完成'
  return '未开始'
})

const latestLevel = computed(() => props.logs[0]?.level || '')
const visibleLogs = computed(() => props.logs.slice(0, 4))

function levelClass(level) {
  if (level === 'L2' || level === 'L3') return 'danger'
  if (level === 'L1') return 'warn'
  if (level === 'NA') return 'na'
  return 'ok'
}

function formatLevel(level) {
  if (!level) return '—'
  return level === 'NA' ? '不可用' : level
}

function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', month: '2-digit', day: '2-digit' })
}

function brief(text, max = 48) {
  if (!text) return '暂无描述'
  const plain = String(text).replace(/[#*_>`[\]()]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length <= max ? plain : `${plain.slice(0, max)}…`
}
</script>

<style scoped>
.bench-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e4e9f3;
  border-radius: 12px;
  background: #fff;
  min-height: 0;
}
.bench-head strong {
  display: block;
  font-size: 13px;
  font-weight: 800;
  color: #4338ca;
}
.bench-head span {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.bench-meta {
  font-size: 11px !important;
  font-weight: 500 !important;
  color: #94a3b8 !important;
}
.bench-tag {
  float: right;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}
.bench-tag.live { background: #dcfce7; color: #15803d; }
.bench-preview :deep(.cam-preview-mini) {
  aspect-ratio: 16 / 10;
  border-radius: 10px;
}
.bench-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.env-toggle {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}
.env-toggle:disabled { opacity: 0.45; cursor: default; }
.env-switch {
  position: relative;
  width: 34px;
  height: 18px;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.15s;
}
.env-switch.on { background: #4f46e5; }
.env-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s;
}
.env-switch.on .env-knob { transform: translateX(16px); }
.bench-check-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 700;
}
.bench-check-btn:disabled { opacity: 0.45; cursor: default; }
.bench-logs {
  border-top: 1px solid #f1f5f9;
  padding-top: 8px;
}
.bench-logs-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}
.bench-logs-count {
  margin-left: auto;
  font-size: 10px;
  color: #94a3b8;
}
.env-level {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
}
.env-level.ok, .log-level.ok { background: #ecfdf5; color: #047857; }
.env-level.warn, .log-level.warn { background: #fffbeb; color: #b45309; }
.env-level.danger, .log-level.danger { background: #fef2f2; color: #b91c1c; }
.env-level.na, .log-level.na { background: #f1f5f9; color: #64748b; }
.bench-logs-empty {
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
  padding: 8px 0;
}
.bench-logs-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 120px;
  overflow-y: auto;
}
.bench-logs-list li {
  display: grid;
  grid-template-columns: auto auto 1fr;
  gap: 6px;
  align-items: start;
  font-size: 10px;
  line-height: 1.4;
}
.log-time { color: #94a3b8; font-family: ui-monospace, monospace; white-space: nowrap; }
.log-level {
  padding: 0 4px;
  border-radius: 4px;
  font-weight: 700;
  white-space: nowrap;
}
.log-text { color: #64748b; }
</style>
