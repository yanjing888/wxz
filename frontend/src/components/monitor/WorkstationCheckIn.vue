<template>
  <section class="check-in-bar" aria-label="本次课工位入座">
    <template v-if="seat.use">
      <div><strong>{{ seat.use.studentName }} · 已入座</strong><span>{{ seat.room.name }} · {{ seat.station.code }} 号工位</span><span>本次课：{{ seat.lesson.className }}</span></div>
      <button v-if="state.station?.id === seat.station.id" :disabled="busy" @click="createGroupCode">邀请同组学生</button>
      <button :disabled="busy" @click="leave">离座 / 换工位</button>
      <small v-if="invitation.code">入组码：<strong>{{ invitation.code }}</strong> · 10 分钟内有效，请同组学生用自己的账号输入。</small>
    </template>
    <template v-else-if="state.station">
      <div><strong>{{ auth.displayName }} · 待确认入座</strong><span>当前终端：{{ state.room.name }} · {{ state.station.code }} 号工位</span><span>{{ state.lesson ? state.lesson.className + ' · 已入座 ' + state.station.occupantCount + '/' + state.station.capacity + ' 人' : '等待教师开始本次课' }}</span></div>
      <button v-if="!state.station.occupied" :disabled="busy || finished || !state.lesson || state.lessonExperimentCode !== experimentCode || !state.station.enabled" @click="claim">确认入座</button>
      <small v-if="state.station.occupied">该工位已有学生，请输入组员提供的入组码加入，坐错工位请联系教师调整。</small>
      <small v-if="state.lesson && state.lessonExperimentCode !== experimentCode">当前实验与本次课不一致，请进入老师指定的实验项目。</small>
    </template>
    <template v-else><div><strong>{{ auth.displayName }} · 尚未入座</strong><span>在工位固定终端登录并确认入座，或输入同组学生提供的入组码。</span></div></template>
    <form v-if="!seat.use" @submit.prevent="joinGroup"><input v-model.trim="groupCode" aria-label="同组入组码" placeholder="同组入组码" maxlength="32" /><button :disabled="busy || finished || !groupCode || !sessionId">加入本组</button></form>
    <span v-if="error" role="alert" class="check-in-error">{{ error }}</span>
  </section>
</template>
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { workstationApi, workstationError } from '../../api/workstations'
const props = defineProps({ sessionId: [Number, String], experimentCode: String, finished: Boolean })
const auth = useAuthStore()
const state = ref({}), seat = ref({}), invitation = ref({}), groupCode = ref(''), error = ref(''), busy = ref(false)
let timer, sequence = 0
async function load() {
  const run = ++sequence
  try {
    const mine = await workstationApi.mySeat(); if (run !== sequence) return
    seat.value = mine.data
    if (!seat.value.use) invitation.value = {}
    if (localStorage.getItem('wxz_terminal_token')) {
      try { const { data } = await workstationApi.terminal(); if (run === sequence) state.value = data }
      catch (e) { if (run !== sequence) return; state.value = {}; if (e.response?.status === 409) localStorage.removeItem('wxz_terminal_token'); else throw e }
    } else state.value = {}
  } catch (e) { if (run === sequence) error.value = workstationError(e) }
}
async function act(operation) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await operation(); await load() } catch (e) { error.value = workstationError(e) } finally { busy.value = false }
}
async function claim() {
  if (!window.confirm(`${auth.displayName}，确认在 ${state.value.room.name} 的 ${state.value.station.code} 号工位入座？`)) return
  await act(() => workstationApi.claim(Number(props.sessionId)))
}
async function createGroupCode() { await act(async () => { invitation.value = (await workstationApi.groupCode()).data }) }
async function joinGroup() { await act(async () => { await workstationApi.joinGroup(groupCode.value, Number(props.sessionId)); groupCode.value = '' }) }
async function leave() { if (window.confirm('确认离开本工位？实验记录会保留，换座后需重新确认入座。')) await act(async () => { await workstationApi.leaveSeat(); invitation.value = {} }) }
watch(() => [props.sessionId, auth.userId], () => {
  clearInterval(timer); sequence++; state.value = {}; seat.value = {}; invitation.value = {}; error.value = ''; groupCode.value = ''
  if (props.sessionId) { load(); timer = setInterval(() => { if (!busy.value && !document.hidden) load() }, 10000) }
}, { immediate: true })
onBeforeUnmount(() => { clearInterval(timer); sequence++ })
</script>
<style scoped>
.check-in-bar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;flex-shrink:0;padding:10px 16px;border-bottom:1px solid #e3e9ef;background:#f8fafc;font-size:12px;color:#46556b}.check-in-bar>div{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.check-in-bar span,.check-in-bar small{color:#738197}.check-in-bar button{padding:5px 12px;border:1px solid #d6dcef;border-radius:6px;background:white;color:#6453c4}.check-in-bar button:disabled{opacity:.45}.check-in-bar .check-in-error{color:#bb5944}.check-in-bar form{display:flex;gap:8px}.check-in-bar input{width:140px;padding:5px 9px;border:1px solid #d6dcef;border-radius:6px}
</style>
