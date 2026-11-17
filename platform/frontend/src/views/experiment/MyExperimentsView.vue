<template>
  <div class="overview custom-scroll">
    <div class="overview-inner">
      <h1 class="sr-only">我的实验</h1>
      <div v-if="tasks.error" class="notice" role="alert">{{ tasks.error }} <button type="button" @click="load">重新加载</button></div>
      <p v-if="!tasks.loaded && tasks.loading" class="empty" role="status">正在加载已分配的实验…</p>
      <template v-else-if="tasks.loaded">
        <div class="toolbar">
          <div class="filters" aria-label="按实验状态筛选">
            <button v-for="filter in EXPERIMENT_FILTERS" :key="filter.key" type="button" :aria-pressed="selected === filter.key" :class="{ selected: selected === filter.key }" @click="selected = filter.key">{{ filter.label }} <span>{{ counts[filter.key] }}</span></button>
          </div>
          <div class="toolbar-actions">
            <label class="search"><span class="sr-only">搜索实验名称</span><input v-model="search" type="search" placeholder="搜索实验名称" /></label>
            <button class="refresh" type="button" :disabled="tasks.loading" @click="load">{{ tasks.loading ? '刷新中…' : '刷新列表' }}</button>
          </div>
        </div>
        <p v-if="!tasks.items.length" class="empty">暂无已分配的实验，请联系任课教师分配。</p>
        <p v-else-if="!visible.length" class="empty">没有符合条件的实验。<button type="button" @click="selected = 'all'; search = ''">查看全部实验</button></p>
        <ul v-else class="experiment-grid">
          <li v-for="item in visible" :key="item.experimentCode" class="experiment-card" :class="{ 'is-current': experimentState(item) === 'current' }">
            <div class="card-top"><span class="state" :class="`state-${experimentState(item)}`">{{ stateLabel(item) }}</span><span class="class-name">{{ item.className || '未标注教学班' }}</span></div>
            <h2>{{ item.experimentName }}</h2><p class="card-description">{{ description(item) }}</p>
            <ol class="milestones" aria-label="实验进度">
              <li :class="{ complete: item.preLabCompleted || item.simulationCompleted }">预习</li>
              <li :class="{ complete: item.labCompleted, active: item.currentTask }">实操</li>
              <li :class="{ complete: item.reportCompleted }">报告</li><li :class="{ complete: item.gradingCompleted }">评价</li>
            </ol>
            <p v-if="item.reportDueAt" class="due">报告截止：{{ dateText(item.reportDueAt) }}</p>
            <details v-if="item.instructions" class="instructions"><summary>老师的补充要求</summary><p>{{ item.instructions }}</p></details>
            <div class="card-footer">
              <span v-if="item.gradingCompleted" class="footer-hint">报告成绩 <strong>{{ item.teacherScore ?? '—' }}</strong></span>
              <span v-else class="footer-hint">{{ item.currentTask ? '当前可以开展' : item.labCompleted ? '实操已结束' : '完成前序实操后开放' }}</span>
              <router-link v-if="experimentAction(item)" :to="experimentAction(item).to" class="card-action" :class="{ primary: item.currentTask }">{{ experimentAction(item).label }} <span aria-hidden="true">→</span></router-link>
              <button v-else type="button" class="waiting" disabled>等待开始</button>
            </div>
          </li>
        </ul>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useTaskStore } from '../../stores/tasks'
import { EXPERIMENT_FILTERS, experimentState, experimentAction, orderedExperiments } from '../../utils/experimentOverview'
const tasks = useTaskStore()
const selected = ref('all'), search = ref('')
const counts = computed(() => {
  const result = Object.fromEntries(EXPERIMENT_FILTERS.map(f => [f.key, 0]))
  result.all = tasks.items.length
  for (const item of tasks.items) result[experimentState(item)]++
  return result
})
const visible = computed(() => orderedExperiments(tasks.items).filter(item =>
  (selected.value === 'all' || selected.value === experimentState(item)) && item.experimentName.includes(search.value.trim())))
function stateLabel(item) {
  const state = experimentState(item)
  if (state === 'current') return item.activeSessionId ? '正在进行' : '当前待开始'
  return EXPERIMENT_FILTERS.find(f => f.key === state).label
}
function description(item) {
  const state = experimentState(item)
  if (state === 'current') return item.simulationRequired && !item.simulationCompleted ? '先完成仿真预习，再进入实验台开展操作。' : item.activeSessionId ? '继续上次的实验操作，按步骤记录数据并提交检查。' : '已轮到这个实验，可以进入实验台开始操作。'
  return { queued: '已分配到你的任务列表，等待前序实操完成。', report: '实操已结束，请整理数据并完成实验报告。', submitted: '报告已提交，等待老师批阅与反馈。', done: '报告已批阅，可以查看成绩、评语与实验记录。' }[state]
}
function dateText(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}
async function load() { try { await tasks.load() } catch { /* Store exposes retryable errors. */ } }
onMounted(load)
</script>

<style scoped>
.overview { flex:1; min-height:0; overflow-y:auto; background:#f5f7fb; color:#17243b; }
.overview-inner { width:100%; max-width:1320px; margin:0 auto; padding:32px 32px 48px; }
.toolbar { margin-top:0; margin-bottom:22px; }
.toolbar-actions { display:flex; align-items:center; gap:10px; }
@media(max-width:640px) { .toolbar-actions { width:100%; }.toolbar-actions .search { flex:1; min-width:0; } }
button,input,a { outline-offset:4px; }.refresh,.search input { border:1px solid #dbe1ea; background:white; border-radius:7px; padding:9px 13px; font-size:13px; }.refresh { white-space:nowrap; }.refresh:disabled { opacity:.6; }
.toolbar { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:14px; }.filters { display:flex; flex-wrap:wrap; gap:5px; }.filters button { font-size:13px; padding:8px 11px; border-radius:6px; color:#647187; }.filters .selected { background:#e5eaf2; color:#17243b; font-weight:600; }.filters span { margin-left:5px; font-variant-numeric:tabular-nums; }.search input { width:200px; max-width:100%; }
.experiment-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px; list-style:none; padding:0; }.experiment-card { min-width:0; border:1px solid #dfe5ee; border-radius:10px; background:white; padding:21px; display:flex; flex-direction:column; }.is-current { border-color:#818cf8; box-shadow:inset 0 3px #6366f1; }
.card-top { display:flex; justify-content:space-between; gap:10px; align-items:center; }.state { font-size:12px; background:#f0f2f6; color:#607086; padding:3px 8px; border-radius:4px; white-space:nowrap; }.state-current { color:#4946b7; background:#eeedff; }.state-report { color:#925514; background:#fff4df; }.state-done { color:#257155; background:#edf7f1; }.class-name { font-size:11px; color:#8490a1; overflow-wrap:anywhere; }
h2 { font-size:17px; font-weight:600; line-height:1.6; margin-top:17px; overflow-wrap:anywhere; }.card-description { font-size:12px; line-height:1.8; color:#738096; margin-top:8px; }
.milestones { display:flex; gap:5px; list-style:none; padding:0; margin:23px 0 20px; }.milestones li { flex:1; border-top:3px solid #e9edf3; padding-top:7px; color:#8792a3; font-size:11px; }.milestones .complete { border-color:#809b8e; color:#46705a; }.milestones .active { border-color:#6366f1; color:#4f46b5; }
.due { font-size:11px; color:#748095; margin-bottom:10px; }.instructions { font-size:12px; color:#657187; margin-bottom:14px; }.instructions p { white-space:pre-wrap; margin-top:8px; line-height:1.8; }.card-footer { display:flex; justify-content:space-between; align-items:center; gap:10px; border-top:1px solid #edf0f5; padding-top:15px; margin-top:auto; }.footer-hint { font-size:11px; color:#7a879b; }.footer-hint strong { color:#25364f; }.card-action { font-size:12px; font-weight:600; white-space:nowrap; padding:8px 10px; border-radius:6px; color:#4f46b5; }.card-action.primary { background:#5652cf; color:white; }.card-action:hover { text-decoration:underline; }.waiting { font-size:12px; color:#95a0b0; background:#f3f5f8; border-radius:6px; padding:8px 10px; cursor:not-allowed; }
.empty { padding:50px 20px; text-align:center; color:#68768b; font-size:14px; }.empty button,.notice button { text-decoration:underline; margin-left:10px; }.notice { padding:14px; background:#fff1ee; color:#a54731; font-size:13px; border-radius:7px; margin-bottom:16px; }
@media(max-width:1100px) { .experiment-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:640px) { .overview-inner { padding:22px 16px 32px; }.experiment-grid { grid-template-columns:minmax(0,1fr); }.search,.search input { width:100%; }.experiment-card { padding:18px; } }
</style>
