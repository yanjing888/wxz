<template>
  <main class="login-page">
    <section class="login-card" :class="{ 'login-card--wide': tabletHomeVisible }">
      <div class="brand-row">
        <img src="/images/jyd-logo.png" alt="JYD" class="brand-logo" />
        <div class="min-w-0">
          <h1>物小智</h1>
          <p class="truncate">{{ tabletHomeVisible ? '平板实验入口' : '大学物理实验智能助教' }}</p>
        </div>
      </div>

      <!-- 平板模式 -->
      <template v-if="tabletHomeVisible">
        <div class="status-row" :class="{ ok: tabletStatus === 'ok', bad: tabletStatus === 'bad' }">
          <span>{{ tabletStatusText }}</span>
          <button type="button" @click="openTabletHome">重新检测</button>
        </div>

        <div class="info-grid">
          <div>
            <span>当前账号</span>
            <strong>{{ auth.displayName || auth.username || '-' }}</strong>
          </div>
          <div>
            <span>后端地址</span>
            <strong>{{ apiBaseUrl }}</strong>
          </div>
        </div>

        <div class="experiment-list">
          <button
            v-for="item in experiments"
            :key="item.code"
            type="button"
            :class="{ active: selectedCode === item.code }"
            @click="selectedCode = item.code"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.code }}</span>
          </button>
        </div>

        <p v-if="tip" class="login-tip">{{ tip }}</p>

        <div class="actions">
          <button type="button" class="primary" :disabled="loading || !selectedCode" @click="enterLab">
            {{ loading ? '正在进入...' : '进入完整实验台' }}
          </button>
          <button type="button" class="ghost" @click="logout">退出登录</button>
        </div>
      </template>

      <!-- 登录模式 -->
      <form v-else-if="mode === 'login'" class="auth-form" @submit.prevent="submitLogin">
        <div class="auth-header">
          <h2>账号登录</h2>
          <p>请输入账号和密码登录系统</p>
        </div>

        <div class="field-group">
          <label class="auth-field">
            <span class="field-label">账号</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <input v-model.trim="loginForm.username" name="username" autocomplete="username" placeholder="请输入账号" />
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">密码</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              <input
                v-model="loginForm.password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <button type="button" class="field-toggle" :title="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                <svg v-if="showPassword" class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88L3.121 3.121m6.758 6.758L3.12 3.12M21 3l-18 18" />
                </svg>
              </button>
            </div>
          </label>
        </div>

        <div class="auth-options">
          <label class="check-row">
            <input v-model="rememberMe" type="checkbox" />
            <span>记住账号</span>
          </label>
          <button type="button" class="link-btn" @click="mode = 'reset'">忘记密码？</button>
        </div>

        <p v-if="tip" class="auth-tip">{{ tip }}</p>

        <button type="submit" class="primary-btn" :disabled="loading">
          <span v-if="loading" class="btn-spinner" />
          {{ loading ? '登录中...' : '登 录' }}
        </button>

        <div class="auth-footer">
          还没有账号？
          <button type="button" class="link-btn" @click="mode = 'register'">立即注册</button>
        </div>
      </form>

      <!-- 注册模式 -->
      <form v-else-if="mode === 'register'" class="auth-form" @submit.prevent="submitRegister">
        <div class="auth-header">
          <h2>新用户注册</h2>
          <p>填写信息创建学生账号</p>
        </div>

        <div class="field-group">
          <label class="auth-field">
            <span class="field-label">账号</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <input v-model.trim="registerForm.username" autocomplete="username" placeholder="3-64 位账号" />
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">姓名</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7 7h.01M7 3v4M3 7h4M10 7h.01M10 3v4M6 11h12M6 15h12M6 19h12" />
              </svg>
              <input v-model.trim="registerForm.displayName" placeholder="真实姓名" />
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">班级 <em>选填</em></span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 14l9-5-9-5-9 5 9 5zm0 0v6m0-6l-9-5v6l9 5v-6z" />
              </svg>
              <input v-model.trim="registerForm.studentClass" placeholder="如：物理 2401 班" />
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">密码</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              <input
                v-model="registerForm.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="至少 6 位密码"
              />
              <button type="button" class="field-toggle" @click="showPassword = !showPassword">
                <svg v-if="showPassword" class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88L3.121 3.121m6.758 6.758L3.12 3.12M21 3l-18 18" />
                </svg>
              </button>
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">确认密码</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <input
                v-model="registerForm.confirmPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="再次输入密码"
              />
            </div>
          </label>
        </div>

        <p v-if="tip" class="auth-tip">{{ tip }}</p>

        <button type="submit" class="primary-btn" :disabled="loading">
          <span v-if="loading" class="btn-spinner" />
          {{ loading ? '注册中...' : '注 册' }}
        </button>

        <div class="auth-footer">
          已有账号？
          <button type="button" class="link-btn" @click="mode = 'login'">返回登录</button>
        </div>
      </form>

      <!-- 忘记密码模式 -->
      <form v-else-if="mode === 'reset'" class="auth-form" @submit.prevent="submitReset">
        <div class="auth-header">
          <h2>重置密码</h2>
          <p>输入账号和新密码</p>
        </div>

        <div class="field-group">
          <label class="auth-field">
            <span class="field-label">账号</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <input v-model.trim="resetForm.username" autocomplete="username" placeholder="请输入账号" />
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">新密码</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              <input
                v-model="resetForm.newPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="至少 6 位新密码"
              />
              <button type="button" class="field-toggle" @click="showPassword = !showPassword">
                <svg v-if="showPassword" class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88L3.121 3.121m6.758 6.758L3.12 3.12M21 3l-18 18" />
                </svg>
              </button>
            </div>
          </label>

          <label class="auth-field">
            <span class="field-label">确认新密码</span>
            <div class="field-input-wrap">
              <svg class="field-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <input
                v-model="resetForm.confirmPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="再次输入新密码"
              />
            </div>
          </label>
        </div>

        <p v-if="tip" class="auth-tip">{{ tip }}</p>

        <button type="submit" class="primary-btn" :disabled="loading">
          <span v-if="loading" class="btn-spinner" />
          {{ loading ? '重置中...' : '重置密码' }}
        </button>

        <div class="auth-footer">
          想起来了？
          <button type="button" class="link-btn" @click="mode = 'login'">返回登录</button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useLabStore } from '../stores/lab'
import { experimentApi, systemApi } from '../api'
import { apiBaseUrl } from '../api/runtime'

const auth = useAuthStore()
const lab = useLabStore()
const router = useRouter()

const loading = ref(false)
const tip = ref('')
const showPassword = ref(false)
const mode = ref('login')
const rememberMe = ref(false)
const tabletHomeVisible = ref(false)
const tabletStatus = ref('checking')
const experiments = ref([])
const selectedCode = ref('')

const loginForm = reactive({
  username: localStorage.getItem('wxz_remember_user') || '',
  password: ''
})

const registerForm = reactive({
  username: '',
  displayName: '',
  studentClass: '',
  password: '',
  confirmPassword: ''
})

const resetForm = reactive({
  username: '',
  newPassword: '',
  confirmPassword: ''
})

const tabletStatusText = computed(() => {
  if (tabletStatus.value === 'ok') return '登录成功，后端连接正常'
  if (tabletStatus.value === 'bad') return '后端连接失败'
  return '正在检测后端连接...'
})

let tipTimer

function showTip(message) {
  tip.value = message
  window.clearTimeout(tipTimer)
  tipTimer = window.setTimeout(() => {
    tip.value = ''
  }, 3500)
}

watch(mode, () => {
  tip.value = ''
})

async function submitLogin() {
  tip.value = ''
  if (!loginForm.username) return showTip('请输入账号')
  if (!loginForm.password) return showTip('请输入密码')

  loading.value = true
  try {
    await auth.login({ username: loginForm.username, password: loginForm.password })
    lab.$reset()
    if (rememberMe.value) {
      localStorage.setItem('wxz_remember_user', loginForm.username)
    } else {
      localStorage.removeItem('wxz_remember_user')
    }
    if (Capacitor.isNativePlatform() && auth.isStudent) {
      await enterDefaultNativeLab()
      return
    }
    await router.replace(auth.homeRoute())
  } catch (e) {
    showTip(e.response?.data?.message || e.message || '账号或密码错误')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  tip.value = ''
  if (!registerForm.username) return showTip('请输入账号')
  if (registerForm.username.length < 3) return showTip('账号至少 3 个字符')
  if (!registerForm.displayName) return showTip('请输入姓名')
  if (!registerForm.password) return showTip('请输入密码')
  if (registerForm.password.length < 6) return showTip('密码至少 6 位')
  if (registerForm.password !== registerForm.confirmPassword) return showTip('两次密码不一致')

  loading.value = true
  try {
    await auth.register({
      username: registerForm.username,
      password: registerForm.password,
      displayName: registerForm.displayName,
      studentClass: registerForm.studentClass
    })
    lab.$reset()
    await router.replace(auth.homeRoute())
  } catch (e) {
    showTip(e.response?.data?.message || e.message || '注册失败')
  } finally {
    loading.value = false
  }
}

async function submitReset() {
  tip.value = ''
  if (!resetForm.username) return showTip('请输入账号')
  if (!resetForm.newPassword) return showTip('请输入新密码')
  if (resetForm.newPassword.length < 6) return showTip('密码至少 6 位')
  if (resetForm.newPassword !== resetForm.confirmPassword) return showTip('两次密码不一致')

  loading.value = true
  try {
    await auth.resetPassword({
      username: resetForm.username,
      newPassword: resetForm.newPassword
    })
    showTip('密码重置成功，请使用新密码登录')
    loginForm.username = resetForm.username
    loginForm.password = ''
    resetForm.newPassword = ''
    resetForm.confirmPassword = ''
    setTimeout(() => { mode.value = 'login' }, 800)
  } catch (e) {
    showTip(e.response?.data?.message || e.message || '重置失败')
  } finally {
    loading.value = false
  }
}

async function openTabletHome() {
  tabletHomeVisible.value = true
  tabletStatus.value = 'checking'
  tip.value = ''
  try {
    await systemApi.difyStatus()
    const { data } = await experimentApi.list()
    experiments.value = data || []
    selectedCode.value = selectedCode.value || experiments.value[0]?.code || ''
    tabletStatus.value = 'ok'
    if (!experiments.value.length) {
      tip.value = '当前账号还没有分配实验，请先在教师端分配实验。'
    }
  } catch (e) {
    tabletStatus.value = 'bad'
    tip.value = e.response?.data?.message || e.message || `无法连接后端：${apiBaseUrl}`
  }
}

async function enterDefaultNativeLab() {
  const { data } = await experimentApi.list()
  const items = data || []
  const saved = localStorage.getItem('wxz_exp')
  const code = items.some((item) => item.code === saved)
    ? saved
    : items[0]?.code
  if (!code) {
    showTip('当前账号还没有分配实验，请先在教师端分配实验。')
    return
  }
  await router.replace({ name: 'lab', query: { exp: code } })
}

async function enterLab() {
  if (!selectedCode.value) return
  loading.value = true
  tip.value = ''
  try {
    await router.push({ name: 'lab', query: { exp: selectedCode.value, restart: '1' } })
  } catch (e) {
    tip.value = e.message || '进入实验台失败'
  } finally {
    loading.value = false
  }
}

function logout() {
  auth.logout()
  lab.$reset()
  tabletHomeVisible.value = false
  experiments.value = []
  selectedCode.value = ''
  loginForm.password = ''
  tip.value = ''
}

onMounted(() => {
  if (Capacitor.isNativePlatform() && auth.token && auth.isStudent) {
    enterDefaultNativeLab()
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  width: 100vw;
  display: flex;
  align-items: center;
  padding: 40px;
  background: #eef4ff url('/images/login-lab-bg.png') center / cover no-repeat;
}
.login-card {
  width: min(440px, 100%);
  margin-left: clamp(2rem, 8vw, 10rem);
  border: 1px solid rgba(255, 255, 255, 0.84);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  padding: 32px 28px;
  box-shadow: 0 30px 86px rgba(44, 62, 118, 0.22);
  backdrop-filter: blur(22px);
}
.login-card--wide {
  width: min(760px, 100%);
}
.brand-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}
.brand-logo {
  height: 42px;
  width: auto;
  flex-shrink: 0;
}
h1, h2, p { margin: 0; }
h1 {
  font-size: 24px;
  font-weight: 900;
  color: #4338ca;
}
.brand-row p {
  margin-top: 3px;
  color: #64748b;
  font-size: 13px;
}

/* 认证表单 */
.auth-header {
  margin-bottom: 22px;
}
.auth-header h2 {
  font-size: 20px;
  font-weight: 900;
  color: #1e293b;
}
.auth-header p {
  margin-top: 4px;
  font-size: 13px;
  color: #94a3b8;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.auth-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-label {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}
.field-label em {
  font-style: normal;
  font-weight: 400;
  color: #cbd5e1;
  margin-left: 4px;
}
.field-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.field-icon {
  position: absolute;
  left: 12px;
  width: 18px;
  height: 18px;
  color: #94a3b8;
  flex-shrink: 0;
  pointer-events: none;
}
.field-input-wrap input {
  width: 100%;
  height: 44px;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
  padding: 0 44px 0 40px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}
.field-input-wrap input::placeholder {
  color: #cbd5e1;
}
.field-input-wrap input:focus {
  border-color: #818cf8;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.field-toggle {
  position: absolute;
  right: 8px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.field-toggle:hover {
  background: #f1f5f9;
  color: #475569;
}

/* 选项行 */
.auth-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 14px;
}
.check-row {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.check-row input {
  width: 15px;
  height: 15px;
  accent-color: #6366f1;
  cursor: pointer;
}
.link-btn {
  border: none;
  background: transparent;
  color: #6366f1;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.15s;
  padding: 0;
}
.link-btn:hover {
  color: #4f46e5;
  text-decoration: underline;
}

/* 提示 */
.auth-tip {
  border: 1px solid rgba(248, 113, 113, 0.28);
  border-radius: 10px;
  padding: 9px 12px;
  color: #dc2626;
  background: rgba(254, 242, 242, 0.86);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

/* 主按钮 */
.primary-btn {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.05em;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.15s, transform 0.1s;
  margin-top: 4px;
}
.primary-btn:disabled {
  opacity: 0.55;
}
.primary-btn:not(:disabled):active {
  transform: scale(0.98);
}
.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 底部 */
.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #64748b;
  margin-top: 16px;
}

/* 平板模式样式 */
.primary, .ghost, .status-row button {
  height: 46px;
  border-radius: 12px;
  font-weight: 900;
}
.primary {
  border: none;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  color: #fff;
}
.primary:disabled { opacity: 0.55; }
.ghost, .status-row button {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  padding: 0 16px;
}
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  color: #64748b;
  background: #f8fafc;
  font-weight: 900;
}
.status-row.ok { color: #047857; border-color: #bbf7d0; background: #f0fdf4; }
.status-row.bad { color: #dc2626; border-color: #fecaca; background: #fef2f2; }
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.info-grid div, .experiment-list button {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  background: #f8fafc;
}
.info-grid span, .experiment-list span {
  display: block;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 800;
}
.info-grid strong, .experiment-list strong {
  display: block;
  margin-top: 5px;
  overflow-wrap: anywhere;
}
.experiment-list {
  display: grid;
  gap: 10px;
  margin: 16px 0;
}
.experiment-list button { text-align: left; }
.experiment-list button.active { border-color: #a5b4fc; background: #eef2ff; }
.actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}
.actions .primary { flex: 1; }

@media (max-width: 720px) {
  .login-page {
    justify-content: center;
    padding: 20px;
  }
  .login-card {
    margin-left: 0;
  }
  .info-grid, .actions {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}
</style>
