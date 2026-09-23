<template>
  <div class="student-manage">
    <header class="sm-head">
      <div>
        <h2>学生管理</h2>
        <p>{{ experimentName || experimentCode }} · 已导入 {{ filteredStudents.length }} 人</p>
      </div>
      <div class="sm-actions">
        <button type="button" class="sm-btn sm-btn--neutral" @click="downloadTemplate">下载模板</button>
        <label class="sm-btn sm-btn--import">
          导入名单
          <input type="file" accept=".csv,.txt" class="hidden-file" @change="onImportFile" />
        </label>
        <button type="button" class="sm-btn sm-btn--primary" @click="openAddModal">添加学生</button>
      </div>
    </header>

    <div class="sm-toolbar">
      <label class="sm-filter">
        <input v-model="showAllStudents" type="checkbox" />
        显示全部学生（含未加入本实验）
      </label>
      <input v-model.trim="searchQuery" class="sm-search" type="search" placeholder="搜索学号或姓名…" />
    </div>

    <div v-if="busy" class="sm-state">处理中…</div>
    <div v-else-if="!displayStudents.length" class="sm-state">
      {{ showAllStudents ? '暂无学生，请导入名单或添加学生。' : '本实验暂无学生，请导入并加入当前实验。' }}
    </div>
    <div v-else class="sm-table-wrap custom-scroll">
      <table class="sm-table">
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>已分配实验</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stu in displayStudents" :key="stu.userId">
            <td>{{ stu.username }}</td>
            <td>{{ stu.displayName }}</td>
            <td>{{ stu.studentClass || '未分班' }}</td>
            <td>
              <span v-if="!stu.assignedExperimentNames?.length" class="sm-tag muted">无</span>
              <span
                v-for="(name, idx) in stu.assignedExperimentNames"
                :key="`${stu.userId}-${idx}`"
                class="sm-tag"
                :class="{ current: stu.assignedExperimentCodes?.[idx] === experimentCode }"
              >
                {{ name }}
              </span>
            </td>
            <td class="col-actions">
              <button
                v-if="isAssignedToCurrent(stu)"
                type="button"
                class="sm-link danger"
                @click="removeFromCurrent(stu)"
              >
                移出本实验
              </button>
              <button
                v-else
                type="button"
                class="sm-link"
                @click="addToCurrent(stu)"
              >
                加入本实验
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-if="message" class="sm-message" :class="messageType">{{ message }}</p>

    <!-- 添加学生 -->
    <div v-if="addModalOpen" class="sm-modal-overlay" @click.self="closeAddModal">
      <form class="sm-modal" @submit.prevent="submitAddStudent">
        <h3>添加学生</h3>
        <p class="sm-modal-hint">添加后学生可使用学号登录，默认密码 123456（可修改）。</p>
        <label class="sm-field">
          <span>学号</span>
          <input v-model.trim="addForm.username" required placeholder="如：20240101" />
        </label>
        <label class="sm-field">
          <span>姓名</span>
          <input v-model.trim="addForm.displayName" required placeholder="如：张三" />
        </label>
        <label class="sm-field">
          <span>班级</span>
          <input v-model.trim="addForm.studentClass" placeholder="如：物理 2401 班" />
        </label>
        <label class="sm-field">
          <span>初始密码</span>
          <input v-model="addForm.password" type="password" minlength="6" placeholder="默认 123456" />
        </label>
        <label class="sm-check">
          <input v-model="addForm.joinCurrent" type="checkbox" />
          加入当前实验「{{ experimentName || experimentCode }}」
        </label>
        <p v-if="addError" class="sm-error">{{ addError }}</p>
        <div class="sm-modal-actions">
          <button type="button" class="sm-btn sm-btn--neutral" @click="closeAddModal">取消</button>
          <button type="submit" class="sm-btn sm-btn--primary" :disabled="submitting">{{ submitting ? '保存中…' : '确认添加' }}</button>
        </div>
      </form>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResultOpen" class="sm-modal-overlay" @click.self="importResultOpen = false">
      <div class="sm-modal">
        <h3>导入完成</h3>
        <ul class="sm-result-list">
          <li>新建 {{ importResult.created }} 人</li>
          <li>更新 {{ importResult.updated }} 人</li>
          <li>跳过 {{ importResult.skipped }} 人</li>
          <li v-if="importResult.assigned">已加入本实验 {{ importResult.assigned }} 人</li>
        </ul>
        <div v-if="importResult.errors?.length" class="sm-result-errors custom-scroll">
          <p v-for="(err, i) in importResult.errors" :key="i">{{ err }}</p>
        </div>
        <div class="sm-modal-actions">
          <button type="button" class="sm-btn sm-btn--primary" @click="importResultOpen = false">知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { teacherApi } from '../../api'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  students: { type: Array, default: () => [] },
  allStudents: { type: Array, default: () => [] }
})

const emit = defineEmits(['refresh'])

const showAllStudents = ref(false)
const searchQuery = ref('')
const busy = ref(false)
const message = ref('')
const messageType = ref('info')

const addModalOpen = ref(false)
const submitting = ref(false)
const addError = ref('')
const addForm = reactive({
  username: '',
  displayName: '',
  studentClass: '',
  password: '',
  joinCurrent: true
})

const importResultOpen = ref(false)
const importResult = reactive({
  created: 0,
  updated: 0,
  skipped: 0,
  assigned: 0,
  errors: []
})

const filteredStudents = computed(() => {
  const list = props.students || []
  const q = searchQuery.value.toLowerCase()
  if (!q) return list
  return list.filter((s) =>
    (s.username || '').toLowerCase().includes(q) ||
    (s.displayName || '').toLowerCase().includes(q) ||
    (s.studentClass || '').toLowerCase().includes(q)
  )
})

const displayStudents = computed(() => {
  const source = showAllStudents.value ? (props.allStudents || []) : filteredStudents.value
  const q = searchQuery.value.toLowerCase()
  if (!q || !showAllStudents.value) return source
  return source.filter((s) =>
    (s.username || '').toLowerCase().includes(q) ||
    (s.displayName || '').toLowerCase().includes(q) ||
    (s.studentClass || '').toLowerCase().includes(q)
  )
})

watch(() => props.experimentCode, () => {
  showAllStudents.value = false
  message.value = ''
})

function isAssignedToCurrent(stu) {
  return (stu.assignedExperimentCodes || []).includes(props.experimentCode)
}

function downloadTemplate() {
  const csv = 'username,displayName,password,studentClass\n20240101,张三,123456,物理2401\n'
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '学生名单模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}

async function onImportFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  busy.value = true
  message.value = ''
  try {
    const csv = await file.text()
    const { data } = await teacherApi.importStudents({
      csv,
      defaultPassword: '123456',
      experimentCodes: props.experimentCode ? [props.experimentCode] : [],
      assignMode: 'append'
    })
    importResult.created = data.created || 0
    importResult.updated = data.updated || 0
    importResult.skipped = data.skipped || 0
    importResult.assigned = data.assigned || 0
    importResult.errors = data.errors || []
    importResultOpen.value = true
    emit('refresh')
  } catch (e) {
    message.value = e.response?.data?.message || e.message || '导入失败'
    messageType.value = 'error'
  } finally {
    busy.value = false
  }
}

function openAddModal() {
  addForm.username = ''
  addForm.displayName = ''
  addForm.studentClass = ''
  addForm.password = ''
  addForm.joinCurrent = true
  addError.value = ''
  addModalOpen.value = true
}

function closeAddModal() {
  addModalOpen.value = false
}

async function submitAddStudent() {
  submitting.value = true
  addError.value = ''
  try {
    const payload = {
      username: addForm.username,
      displayName: addForm.displayName,
      studentClass: addForm.studentClass,
      appendExperiments: true
    }
    if (addForm.password.trim()) payload.password = addForm.password.trim()
    if (addForm.joinCurrent && props.experimentCode) {
      payload.experimentCodes = [props.experimentCode]
    }
    await teacherApi.createStudent(payload)
    closeAddModal()
    message.value = '学生已添加，可使用学号登录实验台。'
    messageType.value = 'success'
    emit('refresh')
  } catch (e) {
    addError.value = e.response?.data?.message || e.message || '添加失败'
  } finally {
    submitting.value = false
  }
}

async function addToCurrent(stu) {
  if (!props.experimentCode) return
  busy.value = true
  message.value = ''
  try {
    await teacherApi.bulkAssignExperiments({
      userIds: [stu.userId],
      experimentCodes: [props.experimentCode],
      mode: 'append'
    })
    message.value = `已将 ${stu.displayName} 加入本实验。`
    messageType.value = 'success'
    emit('refresh')
  } catch (e) {
    message.value = e.response?.data?.message || e.message || '操作失败'
    messageType.value = 'error'
  } finally {
    busy.value = false
  }
}

async function removeFromCurrent(stu) {
  if (!props.experimentCode) return
  if (!confirm(`确定将 ${stu.displayName} 移出「${props.experimentName || props.experimentCode}」？`)) return
  busy.value = true
  message.value = ''
  try {
    await teacherApi.unassignExperiment(stu.userId, props.experimentCode)
    message.value = `已将 ${stu.displayName} 移出本实验。`
    messageType.value = 'success'
    emit('refresh')
  } catch (e) {
    message.value = e.response?.data?.message || e.message || '操作失败'
    messageType.value = 'error'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.student-manage {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 16px;
  background: #fff;
}
.sm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  flex-shrink: 0;
}
.sm-head h2 { font-size: 17px; font-weight: 700; color: #0f172a; }
.sm-head p { margin-top: 4px; font-size: 12px; color: #94a3b8; font-weight: 600; }
.sm-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.sm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  background: #fff;
  line-height: 1;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.sm-btn--neutral {
  color: #475569;
  border-color: #cbd5e1;
}
.sm-btn--neutral:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
}
.sm-btn--import {
  color: #4338ca;
  border-color: #a5b4fc;
  background: #fafaff;
}
.sm-btn--import:hover:not(:disabled) {
  background: #eef2ff;
  border-color: #818cf8;
}
.sm-btn--primary {
  color: #fff;
  border-color: #4f46e5;
  background: #4f46e5;
}
.sm-btn--primary:hover:not(:disabled) {
  background: #4338ca;
  border-color: #4338ca;
}
.sm-btn:disabled { opacity: 0.5; cursor: default; }
.hidden-file { display: none; }
.sm-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.sm-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  white-space: nowrap;
}
.sm-search {
  flex: 1;
  max-width: 280px;
  height: 34px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
}
.sm-search:focus { outline: 2px solid #c7d2fe; border-color: #818cf8; }
.sm-table-wrap { flex: 1; min-height: 0; overflow: auto; border: 1px solid #e4e9f3; border-radius: 10px; }
.sm-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sm-table th {
  position: sticky;
  top: 0;
  background: #f8f9fc;
  text-align: left;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  border-bottom: 1px solid #e4e9f3;
}
.sm-table td { padding: 10px 12px; border-bottom: 1px solid #f1f5f9; color: #1e293b; vertical-align: middle; }
.sm-table tr:hover td { background: #fafbff; }
.col-actions { width: 110px; text-align: right; white-space: nowrap; }
.sm-tag {
  display: inline-block;
  margin: 2px 4px 2px 0;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 600;
}
.sm-tag.current { background: #dcfce7; color: #15803d; }
.sm-tag.muted { background: #f1f5f9; color: #94a3b8; }
.sm-link {
  background: none;
  border: none;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}
.sm-link:hover { text-decoration: underline; }
.sm-link.danger { color: #dc2626; }
.sm-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
}
.sm-message {
  margin-top: 10px;
  font-size: 12.5px;
  font-weight: 600;
  flex-shrink: 0;
}
.sm-message.success { color: #16a34a; }
.sm-message.error { color: #dc2626; }
.sm-message.info { color: #6366f1; }
.sm-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}
.sm-modal {
  width: 420px;
  max-width: 92vw;
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
}
.sm-modal h3 { font-size: 16px; font-weight: 700; color: #0f172a; }
.sm-modal-hint { margin-top: 6px; font-size: 12px; color: #94a3b8; line-height: 1.6; }
.sm-field { display: block; margin-top: 12px; }
.sm-field span { display: block; margin-bottom: 4px; font-size: 12px; font-weight: 600; color: #64748b; }
.sm-field input {
  width: 100%;
  height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0 10px;
  font-size: 13px;
}
.sm-field input:focus { outline: 2px solid #c7d2fe; border-color: #818cf8; }
.sm-check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 13px;
  color: #475569;
}
.sm-error { margin-top: 10px; font-size: 12px; color: #dc2626; }
.sm-modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.sm-result-list { margin-top: 12px; font-size: 13px; color: #334155; line-height: 1.8; }
.sm-result-errors {
  margin-top: 10px;
  max-height: 120px;
  overflow: auto;
  padding: 8px 10px;
  background: #fef2f2;
  border-radius: 8px;
  font-size: 12px;
  color: #b91c1c;
  line-height: 1.6;
}
</style>
