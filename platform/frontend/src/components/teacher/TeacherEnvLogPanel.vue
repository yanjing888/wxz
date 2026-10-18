<template>
  <aside class="env-log-panel">
    <div class="env-log-head">
      <span class="env-log-title">巡检记录</span>
      <span v-if="latestLevel" class="env-level-badge" :class="levelClass(latestLevel)">
        {{ latestLevel === 'NA' ? '不可用' : latestLevel }}
      </span>
      <span class="env-log-count">{{ logs.length }} 条</span>
    </div>
    <div v-if="loading" class="env-log-empty">加载中…</div>
    <div v-else-if="!logs.length" class="env-log-empty">
      暂无巡检记录<br />学生开启监控后，可手动或自动巡检
    </div>
    <div v-else class="env-log-list custom-scroll">
      <article v-for="log in logs" :key="log.id || log.createdAt" class="env-log-item">
        <div class="env-log-meta">
          <span class="env-log-time">{{ formatTime(log.createdAt) }}</span>
          <span class="env-level-chip" :class="levelClass(log.level)">
            {{ log.level === 'NA' ? '不可用' : log.level }}
          </span>
        </div>
        <p class="env-log-summary">{{ briefSummary(log.summary) }}</p>
        <p v-if="log.suggestion" class="env-log-suggestion">{{ briefSummary(log.suggestion, 80) }}</p>
        <img
          v-if="log.snapshotUrl"
          :src="resolveSnapshot(log.snapshotUrl)"
          alt="巡检抽帧"
          class="env-log-thumb"
          loading="lazy"
        />
      </article>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  logs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const latestLevel = computed(() => props.logs[0]?.level || '')

function levelClass(level) {
  if (level === 'NA') return 'lv-na'
  if (level === 'L3' || level === 'L2') return 'lv-danger'
  if (level === 'L1') return 'lv-warn'
  return 'lv-ok'
}

function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function briefSummary(text, maxLen = 120) {
  if (!text) return '暂无描述'
  const plain = String(text).replace(/[#*_>`[\]()]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length <= maxLen ? plain : `${plain.slice(0, maxLen)}…`
}

function resolveSnapshot(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('blob:')) return url
  return url.startsWith('/') ? url : `/${url}`
}
</script>

<style scoped>
.env-log-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e4e9f3;
}
.env-log-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid #e4e9f3;
  flex-shrink: 0;
}
.env-log-title { font-size: 13px; font-weight: 700; color: #0f172a; }
.env-log-count { margin-left: auto; font-size: 11px; color: #94a3b8; font-weight: 600; }
.env-level-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}
.env-level-chip {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}
.lv-ok { background: #ecfdf5; color: #047857; }
.lv-warn { background: #fffbeb; color: #b45309; }
.lv-danger { background: #fef2f2; color: #b91c1c; }
.lv-na { background: #f1f5f9; color: #64748b; }
.env-log-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  font-size: 12px;
  line-height: 1.7;
  color: #94a3b8;
}
.env-log-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.env-log-item {
  border: 1px solid #e4e9f3;
  border-radius: 10px;
  background: #f8fafc;
  padding: 10px 12px;
}
.env-log-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.env-log-time { font-size: 11px; color: #64748b; font-family: ui-monospace, monospace; }
.env-log-summary {
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
}
.env-log-suggestion {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #64748b;
}
.env-log-thumb {
  margin-top: 8px;
  width: 100%;
  max-height: 120px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
</style>
