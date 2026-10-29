<template>
  <div class="admin-app">
    <AppTopBar title="系统管理" @logout="logout" />
    <div class="admin-workspace">
      <aside class="admin-sidebar">
        <nav aria-label="管理端导航">
          <button v-for="item in tabs" :key="item.key" type="button" :class="{active:tab===item.key}" :aria-current="tab===item.key ? 'page' : undefined" :aria-label="item.label" :title="item.label" @click="tab=item.key">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" :d="item.icon" /></svg>
            <span class="menu-label">{{item.label}}</span>
          </button>
        </nav>
      </aside>
    <main class="admin-main">
      <p v-if="error" class="admin-error" role="alert">{{error}}</p>
      <section v-if="tab==='users'" class="users-section">
        <div class="admin-toolbar"><div class="user-filters"><input v-model.trim="query" type="search" placeholder="搜索账号或姓名" aria-label="搜索用户" /><select v-model="role" aria-label="用户身份"><option value="">全部身份</option><option value="STUDENT">学生</option><option value="TEACHER">教师</option><option value="ADMIN">管理员</option></select><select v-model="className" aria-label="用户班级"><option value="">全部教学班</option><option v-for="cls in classes" :key="cls">{{cls}}</option></select></div><div class="toolbar-actions"><span>{{users.length}} 个账号</span><button :disabled="busy" @click="loadUsers">刷新</button><button class="primary" :disabled="busy" @click="editUser()">新增用户</button></div></div>
        <div class="user-table-scroll"><table class="user-table"><thead><tr><th>账号 / 学号</th><th>姓名</th><th>身份</th><th>班级</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td>{{user.username}}</td><td>{{user.displayName}}</td><td>{{roleName(user.role)}}</td><td>{{user.role==='STUDENT' ? (user.classes.join('、') || '待分配') : user.role==='TEACHER' ? (user.studentClass || '全部教学班') : '—'}}</td><td><span class="account-state" :class="{disabled:!user.enabled}">{{user.enabled?'启用':'停用'}}</span></td><td class="row-actions"><button :disabled="busy" @click="editUser(user)">编辑</button><button :disabled="busy" @click="openPassword(user)">重置密码</button><button :disabled="busy || user.id===Number(auth.userId)" @click="toggleUser(user)">{{user.enabled?'停用':'启用'}}</button></td></tr><tr v-if="!users.length"><td colspan="6" class="empty-state">{{loading?'加载中…':'暂无符合条件的账号'}}</td></tr></tbody></table></div>
      </section>
      <section v-else-if="tab==='devices'" class="devices-section">
        <div class="admin-toolbar"><strong>实验室设备配置</strong></div>
        <TeacherWorkstationMonitor :active="tab==='devices'" management />
      </section>
      <section v-else class="audit-section"><div class="admin-toolbar"><strong>最近操作</strong><button @click="loadAudit">刷新</button></div><div class="user-table-scroll"><table class="user-table"><thead><tr><th>时间</th><th>管理员 ID</th><th>操作</th></tr></thead><tbody><tr v-for="log in auditLogs" :key="log.id"><td>{{time(log.createdAt)}}</td><td>{{log.actorId}}</td><td>{{log.summary}}</td></tr><tr v-if="!auditLogs.length"><td colspan="3" class="empty-state">暂无操作记录</td></tr></tbody></table></div></section>
    </main>
    </div>
    <div v-if="dialog" class="admin-overlay" @click.self="dialog=''">
      <section class="admin-dialog" role="dialog" aria-modal="true" :aria-label="dialog==='user'?(form.id?'编辑用户':'新增用户'):'重置密码'"><header><h2>{{dialog==='user'?(form.id?'编辑用户':'新增用户'):'重置密码'}}</h2><button :disabled="busy" aria-label="关闭" @click="dialog=''">✕</button></header><p v-if="error" class="admin-error" role="alert">{{error}}</p>
        <form v-if="dialog==='user'" class="user-form" @submit.prevent="saveUser"><label>账号 / 学号<input v-model.trim="form.username" :disabled="!!form.id" maxlength="64" required autocomplete="off" /></label><label>姓名<input v-model.trim="form.displayName" maxlength="64" required /></label><label>身份<select v-model="form.role" :disabled="form.id===Number(auth.userId)"><option value="STUDENT">学生</option><option value="TEACHER">教师</option><option value="ADMIN">管理员</option></select></label><label class="inline-label"><input v-model="form.enabled" type="checkbox" :disabled="form.id===Number(auth.userId)" />启用账号</label><label v-if="form.role!=='ADMIN'">{{form.role==='STUDENT'?'行政班级':'任教班级（留空可查看全部）'}}<input v-model.trim="form.studentClass" maxlength="64" /></label><label v-if="form.role==='STUDENT'">其他教学班（用逗号分隔）<input v-model="form.classesText" placeholder="选填" /></label><label v-if="!form.id" class="wide">初始密码<input v-model="form.password" type="password" maxlength="128" required autocomplete="new-password" /></label><button class="primary wide" :disabled="busy">{{busy?'保存中…':'保存用户'}}</button></form>
        <form v-else class="password-form" @submit.prevent="resetPassword"><p>{{passwordUser.displayName}}（{{passwordUser.username}}）</p><label>新密码<input v-model="newPassword" type="password" maxlength="128" required autocomplete="new-password" /></label><p class="muted">重置后原登录会立即失效，需要使用新密码重新登录。</p><button class="primary" :disabled="busy || !newPassword">确认重置</button></form>
      </section>
    </div>
  </div>
</template>
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { adminApi } from '../api/admin'
import { workstationError } from '../api/workstations'
import TeacherWorkstationMonitor from '../components/teacher/TeacherWorkstationMonitor.vue'
import AppTopBar from '../components/layout/AppTopBar.vue'
const auth=useAuthStore(),router=useRouter()
const tabs=[
  {key:'users',label:'用户管理',icon:'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2m20 0v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75M13 7a4 4 0 11-8 0 4 4 0 018 0z'},
  {key:'devices',label:'实验室与工位',icon:'M3 21h18M5 21V5a2 2 0 012-2h10a2 2 0 012 2v16M9 7h1m4 0h1M9 11h1m4 0h1M10 21v-6h4v6'},
  {key:'audit',label:'操作记录',icon:'M12 8v4l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z'}
]
const tab=ref('users'),query=ref(''),role=ref(''),className=ref(''),users=ref([]),classes=ref([]),error=ref(''),busy=ref(false),loading=ref(false)
const dialog=ref(''),form=ref({}),passwordUser=ref(null),newPassword=ref(''),auditLogs=ref([])
let sequence=0,filterTimer
const roleName=value=>({STUDENT:'学生',TEACHER:'教师',ADMIN:'管理员'})[value] || value
const time=value=>new Date(value).toLocaleString('zh-CN',{hour12:false})
async function loadUsers(){const run=++sequence;loading.value=true;try{const {data}=await adminApi.users({query:query.value,role:role.value,className:className.value});if(run===sequence){users.value=data.users;classes.value=data.classes}}catch(e){if(run===sequence)error.value=workstationError(e)}finally{if(run===sequence)loading.value=false}}
async function act(operation){if(busy.value)return false;busy.value=true;error.value='';try{await operation();return true}catch(e){error.value=workstationError(e);return false}finally{busy.value=false}}
function editUser(user){error.value='';form.value=user?{...user,classesText:user.classes.filter(c=>c!==user.studentClass).join('、'),password:null}:{username:'',displayName:'',role:'STUDENT',studentClass:'',classesText:'',enabled:true,password:''};dialog.value='user'}
async function saveUser(){const data={...form.value,classes:form.value.role==='STUDENT'?form.value.classesText.split(/[,，、\n]/).map(c=>c.trim()).filter(Boolean):[]};if(await act(()=>adminApi.saveUser(data,form.value.id))){dialog.value='';await loadUsers()}}
async function toggleUser(user){if(!window.confirm(`确认${user.enabled?'停用':'启用'}账号 ${user.username}？`))return;await act(async()=>{await adminApi.saveUser({...user,enabled:!user.enabled,password:null},user.id);await loadUsers()})}
function openPassword(user){passwordUser.value=user;newPassword.value='';error.value='';dialog.value='password'}
async function resetPassword(){if(await act(()=>adminApi.resetPassword(passwordUser.value.id,newPassword.value))){newPassword.value='';dialog.value='';await loadUsers()}}
async function loadAudit(){await act(async()=>{auditLogs.value=(await adminApi.audit()).data})}
function logout(){auth.logout();router.replace('/login')}
watch([query,role,className],()=>{clearTimeout(filterTimer);filterTimer=setTimeout(loadUsers,250)})
watch(tab,value=>{error.value='';if(value==='audit')loadAudit()})
onMounted(loadUsers)
onBeforeUnmount(()=>{clearTimeout(filterTimer);sequence++})
</script>
<style scoped>

.admin-app { height:100%; display:flex; flex-direction:column; overflow:hidden; background:#f5f7fb; color: #283548; font-size: 13px; }
.admin-workspace { display:flex; flex:1; min-height:0; min-width:0; }
.admin-sidebar { width:208px; flex-shrink:0; padding:20px 12px; overflow-y:auto; background:#fff; border-right:1px solid #e1e7ef; }
.admin-sidebar nav { display:flex; flex-direction:column; gap:6px; }
.admin-sidebar nav button { display:flex; align-items:center; gap:12px; width:100%; padding:13px 16px; border:0; border-radius:8px; background:transparent; color: #718096; text-align:left; white-space:nowrap; transition:background .15s,color .15s; }
.admin-sidebar nav button:hover { background:#f6f7fb; color: #526076; }
.admin-sidebar nav button.active { background:#f0edfc; color: #6954cd; font-weight:650; }
.admin-sidebar nav button:focus-visible { outline:2px solid #a99be8; outline-offset:2px; }
.admin-sidebar nav svg { width:19px; height:19px; flex-shrink:0; }
.admin-main { flex:1; min-width:0; min-height:0; overflow:auto; padding:24px; }
.admin-toolbar{display:flex;justify-content:space-between;align-items:center;gap:15px;flex-wrap:wrap;margin-bottom:18px}.user-filters,.toolbar-actions{display:flex;align-items:center;gap:10px}.toolbar-actions span{color: #8a95a6;font-size: 12px}button,input,select{font:inherit}button{border:1px solid #dce3ed;border-radius:7px;background:white;padding:7px 12px;color: #526076;cursor:pointer}button:disabled{opacity:.45;cursor:default}button.primary{background:#6b59d4;border-color:#6b59d4;color:white}input:not([type=checkbox]),select{border:1px solid #dce3ed;border-radius:7px;background:white;padding:8px 11px;min-width:0;color: #26334a}.user-filters input{width:240px}.user-table-scroll{overflow:auto;border:1px solid #e0e6f0;border-radius:10px;background:white}.user-table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}.user-table th{background:#fafbfd;color: #7d899b;font-weight:500}.user-table th,.user-table td{padding:15px 20px;border-bottom:1px solid #eef1f6}.row-actions{display:flex;gap:12px}.row-actions button{border:0;background:none;padding:0;color: #6d5ac9}.account-state{color: #3b9568;background:#eff9f3;padding:4px 9px;border-radius:5px;font-size: 11px}.account-state.disabled{color: #8390a2;background:#f0f3f7}.empty-state{text-align:center;color: #8b95a6;padding:40px!important}.admin-error{color: #b95947;background:#fff1ed;padding:11px 14px;border-radius:8px}.devices-section{display:flex;flex-direction:column;min-height:0}.devices-section :deep(.workstation-monitor){padding:0;height:auto;min-height:400px}.devices-section>.admin-toolbar label{display:flex;align-items:center;gap:12px}.terminal-binding{background:white;border:1px solid #e0e6f0;border-radius:10px;padding:18px;margin-bottom:20px;display:flex;justify-content:space-between;gap:20px;flex-wrap:wrap}.terminal-binding p{color: #8b95a6;font-size: 12px;margin:7px 0}.terminal-binding form{display:flex;align-items:center;gap:9px}.admin-overlay{position:fixed;inset:0;background:#14203966;z-index:100;display:flex;align-items:center;justify-content:center;padding:20px}.admin-dialog{background:white;border-radius:13px;max-width:580px;width:100%;max-height:90vh;overflow:auto;padding:24px;box-shadow:0 20px 70px #15203933}.admin-dialog>header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.admin-dialog h2{font-size:17px;font-weight:650}.user-form{display:grid;grid-template-columns:1fr 1fr;gap:16px}.user-form label,.password-form label{display:flex;flex-direction:column;gap:8px}.user-form .inline-label{flex-direction:row;align-items:center}.wide{grid-column:1/-1}.password-form p{margin:12px 0}.muted{color: #8b95a6;font-size: 12px;line-height:1.8}
@media (max-width:850px) {
  .admin-sidebar { width:176px; padding:16px 10px; }
  .admin-sidebar nav button { padding:12px; gap:10px; }
  .admin-main { padding:16px; }
  .user-filters { flex-wrap:wrap; }
  .user-filters input { width:190px; }
}
@media (max-width:550px) {
  .admin-sidebar { width:56px; padding:14px 6px; }
  .admin-sidebar nav button { justify-content:center; padding:12px 0; }
  .admin-sidebar .menu-label { display:none; }
  .admin-main { padding:12px; }
  .user-form { grid-template-columns:1fr; }
  .terminal-binding form { flex-wrap:wrap; }
  .user-table th,.user-table td { padding:12px; }
}

/* Table hierarchy: labels remain distinct from the data rows. */
thead th { font-size:13px; font-weight:700; color:#334155; line-height:1.5; }
thead th button { font:inherit; color:inherit; }
tbody td { font-size:13px; line-height:1.6; }
</style>
