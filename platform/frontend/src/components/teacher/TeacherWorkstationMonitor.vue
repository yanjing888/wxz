<template>
  <section class="workstation-monitor" :class="{ 'compact-monitor': !management }">
    <div v-if="management" class="monitor-heading"><div class="heading-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4M7 9h3m4 0h3M7 13h3m4 0h3"/></svg></div><div><h1>{{ management ? '实验室与工位' : '课堂巡视' }}</h1><p>{{ management ? '统一维护工位与摄像头配置' : '查看实验现场，了解每个工位的学生情况' }}</p></div><span class="heading-label">{{ management ? '设备管理' : '实验教学' }}</span></div>
    <div class="station-toolbar">
      <div class="room-picker"><label>实验室 <select v-model="roomId" :disabled="busy" aria-label="选择实验室"><option v-for="room in roomOptions" :key="room.id" :value="room.id">{{ room.name }}</option></select></label><span class="muted">{{ stations.length }} 个工位</span></div>
      <div class="toolbar-actions">
        <button v-if="management" :disabled="busy" @click="createRoom">新增实验室</button>
        <button v-if="management && roomId" :disabled="busy" @click="renameRoom">修改实验室名称</button>
        <button v-if="management" class="primary" :disabled="busy || !roomId" @click="editStation()">新增工位</button>
        <button v-if="!management" :disabled="busy || !boundCount" @click="clearRoomBindings">清空学生绑定</button>
        <button :disabled="busy || loading" @click="load">刷新</button>
      </div>
    </div>
    <div v-if="!management" class="monitor-stats"><div><span class="stat-symbol purple">▦</span><span>实验工位<strong>{{ stations.length }}<small>个</small></strong></span></div><div><span class="stat-symbol green">◉</span><span>已配置摄像头<strong>{{ configuredCount }}<small>个</small></strong></span></div><div><span class="stat-symbol blue">♙</span><span>已绑定学生<strong>{{ boundCount }}<small>人</small></strong></span></div><div><span class="stat-symbol amber">◇</span><span>待绑定工位<strong>{{ stations.filter(s => !s.bindingCount).length }}<small>个</small></strong></span></div></div>
    <p class="intro">{{ management ? '登记工位并填写摄像头视频流地址或 IP，保存后可测试画面。' : '课前核对或绑定学生，课后清空绑定；点击画面下方按钮可放大查看。摄像头已配置不代表设备在线。' }}</p>
    <p v-if="error" class="station-error" role="alert">{{ error }}</p>
    <div v-if="!stations.length" class="station-empty">{{ loading ? '加载工位…' : management ? '尚未配置工位，点击“新增工位”添加摄像头。' : '尚未配置工位，请联系管理员添加摄像头。' }}</div>
    <div class="station-grid">
      <article v-for="station in stationItems" :key="station.id" class="station-card">
        <header><div class="station-title"><span class="station-number">{{ station.code }}</span><div><strong>{{ station.name }}</strong><p>{{ station.roomName }}</p></div></div><span class="status-tag" :class="{ configured: station.enabled && (station.streamConfigured || station.cameraType === 'USB') }">{{ !station.enabled ? '已停用' : station.streamConfigured || station.cameraType === 'USB' ? '已配置' : '待配置' }}</span></header>
        <WorkstationPreview v-if="!management" :station="station" :live="active && !pageHidden && !dialog" />
        <p v-else class="muted">{{ station.streamConfigured || station.cameraType === 'USB' ? '摄像头已配置，可测试画面。' : '尚未填写摄像头地址。' }}</p>
        <div v-if="!management" class="station-members"><div class="members-heading"><span>当前学生</span><span>{{ station.bindingCount || 0 }} 人</span></div><div v-if="station.members?.length" class="member-tags"><span v-for="member in station.members" :key="member.id" :title="member.className + ' · ' + member.username">{{ member.name }}<small>{{ member.className }}</small></span></div><p v-else>{{ station.bindingCount ? '其他教学班已绑定' : '暂未绑定学生，可直接查看画面' }}</p></div>
        <div class="card-actions">
          <button v-if="!management" :disabled="busy || station.canBind === false" @click="openBinding(station)">{{ station.bindingCount ? '调整学生' : '绑定学生' }}</button>
          <button v-if="management" :disabled="busy" @click="editStation(station)">编辑配置</button>
          <button class="primary" :disabled="!station.enabled || (!station.streamConfigured && station.cameraType !== 'USB')" @click="openLive(station)">{{ management ? '测试画面' : '放大查看' }}</button>
        </div>
      </article>
    </div>
    <ListPagination v-model:page="stationPage" v-model:page-size="stationSize" :total="stationTotal" />
    <div v-if="dialog" class="station-overlay" @click.self="dialog = ''" @keydown.esc="dialog = ''">
      <section class="station-dialog" :class="{ 'video-dialog': dialog === 'live' }" role="dialog" aria-modal="true" :aria-label="dialogTitle">
        <header><h2>{{ dialogTitle }}</h2><button aria-label="关闭" @click="dialog = ''">关闭</button></header>
        <p v-if="error" class="station-error" role="alert">{{ error }}</p>
        <WorkstationPreview v-if="dialog === 'live' && liveStation" :station="liveStation" :live="active && !pageHidden" />
        <form v-if="dialog === 'binding'" class="binding-form" @submit.prevent="saveBinding">
          <p class="muted">选择当前在该工位实验的学生，可多人同组。换班时请清空或调整；无需学生扫码。</p>
          <label>教学班<select v-model="bindingClass" :disabled="busy" @change="bindingIds = []"><option value="" disabled>请选择教学班</option><option v-for="name in bindingRoster.classes" :key="name" :value="name">{{ name }}</option></select></label>
          <input v-model.trim="bindingSearch" type="search" placeholder="搜索姓名或学号" aria-label="搜索绑定学生" />
          <div class="binding-list"><label v-for="student in bindingItems" :key="student.id" :class="{ unavailable: student.stationId && student.stationId !== selectedId }"><input v-model="bindingIds" type="checkbox" :value="student.id" :disabled="busy || !!(student.stationId && student.stationId !== selectedId)" /><span><strong>{{ student.name }}</strong><small>{{ student.username }}</small></span><em v-if="student.stationId && student.stationId !== selectedId">已绑定其他工位</em></label><p v-if="!bindingCandidates.length" class="muted">{{ bindingClass ? '没有符合条件的学生，请先在学生管理中维护名单。' : '请先选择教学班' }}</p></div>
          <ListPagination v-model:page="bindingPage" v-model:page-size="bindingSize" :total="bindingTotal" :disabled="busy" />
          <div class="binding-footer"><span>已选 {{ bindingIds.length }} 人 / 最多 8 人</span><button type="button" :disabled="busy" @click="bindingIds = []">取消全选</button><button class="primary" :disabled="busy || bindingIds.length > 8">{{ busy ? '保存中…' : '保存绑定' }}</button></div>
        </form>
        <form v-if="dialog === 'edit'" class="device-form" @submit.prevent="save">
          <label>工位编号<input v-model.trim="form.code" required maxlength="32" placeholder="例如 01" /></label>
          <label>工位名称（选填）<input v-model.trim="form.name" maxlength="80" placeholder="例如 1 号实验台" /></label>
          <label class="wide">视频流地址或摄像头 IP<input v-model.trim="form.address" maxlength="1024" placeholder="ws://摄像头地址/实际路径.flv 或 192.168.1.20" /></label>
          <p class="muted wide">填写 IP 时，使用部署配置中的该型号播放路径。其他型号请填写完整 FLV 视频流地址。暂未接入设备可先留空。</p>
          <label class="check-label wide"><input v-model="form.enabled" type="checkbox" />启用工位</label>
          <button class="primary wide" :disabled="busy">{{ busy ? '保存中…' : '保存配置' }}</button>
        </form>
      </section>
    </div>
  </section>
</template>
<script setup>
import ListPagination from '../common/ListPagination.vue'
import { usePagination } from '../../composables/usePagination'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import WorkstationPreview from '../monitor/WorkstationPreview.vue'
import { workstationApi, workstationError } from '../../api/workstations'
const props = defineProps({ active: Boolean, management: Boolean })
const dashboard = ref({ rooms: [], stations: [] }), roomId = ref(null), loading = ref(false), busy = ref(false), error = ref('')
const dialog = ref(''), form = ref({}), selectedId = ref(null), pageHidden = ref(document.hidden)
const bindingRoster = ref({ classes: [], students: [] }), bindingClass = ref(''), bindingIds = ref([]), bindingSearch = ref('')
const configuredCount = computed(() => stations.value.filter(s => s.enabled && (s.streamConfigured || s.cameraType === 'USB')).length)
const boundCount = computed(() => stations.value.reduce((total, s) => total + (s.bindingCount || 0), 0))
const bindingCandidates = computed(() => bindingRoster.value.students.filter(s => s.classes.includes(bindingClass.value) && (!bindingSearch.value || (s.name + ' ' + s.username).toLowerCase().includes(bindingSearch.value.toLowerCase()))))
let timer, sequence = 0
const roomOptions = computed(() => props.management ? dashboard.value.rooms : dashboard.value.rooms.filter(room => dashboard.value.stations.some(station => station.roomId === room.id)))
const stations = computed(() => dashboard.value.stations.filter(station => station.roomId === roomId.value))
const liveStation = computed(() => stations.value.find(station => station.id === selectedId.value))
const dialogTitle = computed(() => dialog.value === 'binding' ? (liveStation.value?.name || '工位') + ' · 绑定学生' : dialog.value === 'live' ? (liveStation.value?.name || '工位') + ' · 实时画面' : form.value.id ? '编辑工位配置' : '新增工位')
async function openBinding(station) {
  if (busy.value) return
  busy.value = true; error.value = ''; const currentRoom = roomId.value; const run = sequence
  try {
    const { data } = await workstationApi.bindingStudents(currentRoom)
    if (run !== sequence || roomId.value !== currentRoom || !props.active) return
    bindingRoster.value = data; selectedId.value = station.id
    bindingClass.value = station.members?.[0]?.className || data.classes[0] || ''
    bindingIds.value = (station.members || []).map(m => m.id); bindingSearch.value = ''; dialog.value = 'binding'
  } catch (e) { error.value = workstationError(e) } finally { busy.value = false }
}
async function saveBinding() {
  if (await act(() => workstationApi.bindStudents(selectedId.value, bindingClass.value, bindingIds.value))) dialog.value = ''
}
async function clearRoomBindings() {
  if (!window.confirm('清空当前实验室的学生绑定？下节课可重新选择学生。')) return
  await act(() => workstationApi.clearBindings(roomId.value))
}
async function load() {
  const run = ++sequence; loading.value = true
  try {
    const { data } = await workstationApi.overview()
    if (run !== sequence) return
    dashboard.value = data; error.value = ''
    if (!roomOptions.value.some(room => room.id === roomId.value)) {
      const remembered = Number(localStorage.getItem('wxz_lab_room_id'))
      roomId.value = roomOptions.value.find(room => room.id === remembered)?.id || roomOptions.value.find(room => data.stations.some(station => station.roomId === room.id))?.id || roomOptions.value[0]?.id || null
    }
    if (dialog.value === 'live' && !liveStation.value) dialog.value = ''
  } catch (e) { if (run === sequence) error.value = workstationError(e) }
  finally { if (run === sequence) loading.value = false }
}
async function act(operation) {
  if (busy.value) return false
  busy.value = true; error.value = ''
  try { await operation(); await load(); return true } catch (e) { error.value = workstationError(e); return false } finally { busy.value = false }
}
async function createRoom() {
  const name = window.prompt('实验室名称，例如物理楼 302'); if (!name?.trim()) return
  await act(async () => { const { data } = await workstationApi.createRoom(name.trim()); roomId.value = data.id })
}
async function renameRoom() {
  const room = dashboard.value.rooms.find(item => item.id === roomId.value)
  const name = window.prompt('实验室名称', room?.name || ''); if (!name?.trim()) return
  await act(() => workstationApi.renameRoom(undefined, name.trim(), roomId.value))
}
async function editStation(station) {
  error.value = ''; const run = sequence
  const next = station ? { id: station.id, code: station.code, name: station.name, enabled: station.enabled, address: '' } : { code: '', name: '', enabled: true, address: '' }
  if (station) {
    try { const { data } = await workstationApi.config(station.id); if (run !== sequence) return; next.address = data.streamUrl || '' }
    catch (e) { error.value = workstationError(e); return }
  }
  form.value = next; dialog.value = 'edit'
}
async function save() {
  const data = { code: form.value.code.trim(), name: form.value.name.trim() || form.value.code.trim() + ' 号工位', address: form.value.address.trim(), enabled: form.value.enabled }
  if (await act(() => workstationApi.saveSimple(roomId.value, data, form.value.id))) dialog.value = ''
}
function openLive(station) { selectedId.value = station.id; error.value = ''; dialog.value = 'live' }
function updateVisibility() { pageHidden.value = document.hidden }
watch(roomId, value => { if (value) localStorage.setItem('wxz_lab_room_id', String(value)); dialog.value = '' })
watch(() => props.active, active => {
  clearInterval(timer); sequence++; dialog.value = ''
  if (active) { load(); timer = setInterval(() => { if (!busy.value && !document.hidden && !['edit', 'binding'].includes(dialog.value)) load() }, 10000) }
}, { immediate: true })
onMounted(() => document.addEventListener('visibilitychange', updateVisibility))
onBeforeUnmount(() => { clearInterval(timer); sequence++; document.removeEventListener('visibilitychange', updateVisibility) })
const { page: stationPage, pageSize: stationSize, total: stationTotal, items: stationItems } = usePagination(stations, [roomId])
const { page: bindingPage, pageSize: bindingSize, total: bindingTotal, items: bindingItems } = usePagination(bindingCandidates, [bindingClass, bindingSearch, selectedId])
</script>
<style scoped>
.workstation-monitor{height:100%;overflow:auto;padding:18px;background:#f5f7fb;color:#253047;font-size:13px}.station-toolbar,.room-picker,.toolbar-actions,.card-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.station-toolbar{justify-content:space-between;margin-bottom:12px}.room-picker label{display:flex;align-items:center;gap:10px}.intro,.muted{font-size:12px;color:#748196;line-height:1.7}.intro{margin:0 0 18px}.station-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:16px}.station-card{background:white;border:1px solid #e2e7f0;border-radius:12px;padding:14px}.station-card header,.station-dialog>header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px}.station-card header p{font-size:11px;color:#8390a2;margin:5px 0 0}.status-tag{font-size:11px;color:#758196;background:#f1f4f8;border-radius:5px;padding:4px 8px}.card-actions{justify-content:flex-end;margin-top:12px}button,input,select{font:inherit}button{border:1px solid #dbe1ec;background:white;color:#475569;border-radius:7px;padding:7px 12px;cursor:pointer}button:disabled{opacity:.45;cursor:default}button:hover:not(:disabled){border-color:#9384ee;color:#6453ce}.primary{background:#6c5bda;color:white;border-color:#6c5bda}.primary:hover:not(:disabled){color:white}input:not([type=checkbox]),select{min-width:0;border:1px solid #dbe1ec;border-radius:7px;padding:8px 10px;color:#253047;background:white}.station-empty{padding:48px;text-align:center;color:#8490a2;background:white;border-radius:12px}.station-error{padding:10px 12px;color:#b54c43;background:#fff0ee;border-radius:7px}.station-overlay{position:fixed;inset:0;z-index:100;display:flex;align-items:center;justify-content:center;padding:22px;background:#15203966}.station-dialog{width:100%;max-width:580px;max-height:90vh;overflow:auto;padding:24px;background:white;border-radius:14px;box-shadow:0 15px 70px #15203933}.station-dialog.video-dialog{max-width:1000px}.station-dialog h2{font-size:17px;margin:0}.device-form{display:grid;grid-template-columns:1fr 1fr;gap:16px}.device-form label{display:flex;flex-direction:column;gap:8px}.device-form .wide{grid-column:1/-1}.device-form .check-label{flex-direction:row;align-items:center}.device-form p{margin:0}@media(max-width:600px){.station-grid,.device-form{grid-template-columns:1fr}.station-dialog{padding:18px}}
.workstation-monitor{box-sizing:border-box;background:#f8fafc;padding:20px 24px;color:#334155}
.monitor-heading{display:flex;align-items:center;gap:12px;margin-bottom:20px}.heading-icon{display:grid;place-items:center;width:46px;height:46px;border-radius:12px;background:linear-gradient(135deg,#818cf8,#6366f1);color:white;box-shadow:0 4px 12px #6366f126}.heading-icon svg{width:27px;height:27px}.monitor-heading h1{margin:0;color:#0f172a;font-size:20px;font-weight:700}.monitor-heading p{margin:5px 0 0;color:#64748b;font-size:12px}.heading-label{margin-left:auto;border:1px solid #e0e7ff;border-radius:6px;padding:5px 10px;background:#eef2ff;color:#6366f1;font-size:12px}
.station-toolbar{padding:12px 16px;border:1px solid #e4e9f3;border-radius:10px;background:#fff;margin-bottom:16px}.room-picker label{font-weight:600;color:#475569}.room-picker select{max-width:min(430px,65vw);font-weight:400}.monitor-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:16px}.monitor-stats>div{display:flex;align-items:center;gap:12px;background:#fff;border:1px solid #e4e9f3;border-radius:10px;padding:16px}.monitor-stats>div>span:last-child{font-size:12px;color:#64748b}.monitor-stats strong{display:block;margin-top:6px;font-size:24px;color:#0f172a;font-weight:700;font-variant-numeric:tabular-nums}.monitor-stats small{font-size:11px;font-weight:400;color:#94a3b8;margin-left:7px}.stat-symbol{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;font-size:23px}.purple{color:#6366f1;background:#eef2ff}.green{color:#059669;background:#ecfdf5}.blue{color:#0284c7;background:#eff6ff}.amber{color:#d97706;background:#fffbeb}
.intro{padding:10px 13px;border-radius:8px;background:#eef2ff;color:#6366f1;margin-bottom:18px}.station-grid{grid-template-columns:repeat(auto-fill,minmax(min(100%,350px),1fr));align-items:start;gap:18px}.station-card{min-width:0;padding:0;overflow:hidden;border-color:#e4e9f3;box-shadow:0 2px 8px #0f172a04}.station-card:hover{border-color:#c7d2fe;box-shadow:0 4px 16px #6366f10c}.station-card header{padding:15px 16px;margin:0}.station-title{display:flex;align-items:center;gap:10px;min-width:0}.station-title>div{min-width:0}.station-title strong{font-size:14px;color:#1e293b}.station-number{display:grid;place-items:center;min-width:36px;height:36px;padding:0 6px;box-sizing:border-box;background:#eef2ff;color:#6366f1;font-size:16px;font-weight:700;border-radius:8px}.status-tag{white-space:nowrap}.status-tag.configured{background:#ecfdf5;color:#047857}.station-card>:deep(.station-preview){border-radius:0}.station-card>.muted{padding:0 16px}.station-members{padding:14px 16px;min-height:78px;box-sizing:border-box}.members-heading{display:flex;justify-content:space-between;color:#64748b;font-size:11px;margin-bottom:10px}.members-heading span:last-child{color:#6366f1}.station-members p{margin:0;font-size:12px;color:#94a3b8}.member-tags{display:flex;gap:7px;flex-wrap:wrap}.member-tags>span{padding:5px 8px;background:#f5f3ff;color:#4f46e5;border:1px solid #ede9fe;border-radius:6px;font-size:12px}.member-tags small{margin-left:6px;font-size:10px;color:#818cf8}.card-actions{margin:0;padding:12px 16px;border-top:1px solid #eef2f7;background:#fbfcfe}.primary{background:#6366f1;border-color:#6366f1}.primary:hover:not(:disabled){background:#4f46e5;border-color:#4f46e5}.station-dialog>header{padding-bottom:15px;border-bottom:1px solid #e4e9f3}.binding-form{display:flex;flex-direction:column;gap:14px}.binding-form>label{display:flex;align-items:center;gap:12px}.binding-form>label select{flex:1}.binding-form>.muted{margin:0}.binding-list{max-height:320px;overflow:auto;border:1px solid #e4e9f3;border-radius:8px;padding:4px 12px;min-height:100px}.binding-list>label{display:flex;align-items:center;gap:10px;padding:12px 0;border-bottom:1px solid #f1f5f9;cursor:pointer}.binding-list>label:last-child{border:0}.binding-list strong{font-size:13px;font-weight:600}.binding-list small{display:block;color:#94a3b8;font-size:11px;margin-top:4px}.binding-list em{font-size:11px;font-style:normal;color:#94a3b8;margin-left:auto}.binding-list .unavailable{opacity:.6;cursor:default}.binding-footer{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.binding-footer>span{margin-right:auto;font-size:12px;color:#64748b}input[type=checkbox]{accent-color:#6366f1;width:15px;height:15px}button,input,select{transition:border-color .15s,background .15s}:is(button,input,select):focus-visible{outline:2px solid #a5b4fc;outline-offset:2px}
@media(min-width:1500px){.station-grid{grid-template-columns:repeat(auto-fill,minmax(390px,1fr))}}@media(max-width:900px){.monitor-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.workstation-monitor{padding:16px}}@media(max-width:600px){.monitor-stats{gap:8px}.monitor-stats>div{padding:12px;gap:8px}.heading-label{display:none}.monitor-heading h1{font-size:18px}.station-toolbar{padding:12px}.room-picker{width:100%}.room-picker select{max-width:65vw}.binding-footer>span{width:100%}}
.compact-monitor .station-grid{grid-template-columns:repeat(auto-fill,minmax(min(100%,280px),1fr));gap:14px}
.compact-monitor .station-card{max-width:320px;width:100%;box-sizing:border-box}
.compact-monitor .station-card header{padding:12px;gap:8px}.compact-monitor .station-title{gap:8px}.compact-monitor .station-title strong{font-size:13px}.compact-monitor .station-title p{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:190px}.compact-monitor .station-number{min-width:30px;height:30px;font-size:14px;border-radius:7px}
.compact-monitor .station-members{padding:11px 12px;min-height:68px}.compact-monitor .members-heading{margin-bottom:8px}.compact-monitor .card-actions{padding:10px 12px;gap:8px}.compact-monitor .card-actions button{padding:6px 10px;font-size:12px}
</style>
