<template>
  <main class="login-page fixed inset-0 w-screen h-screen flex items-center px-6 py-10">
    <section class="login-card w-full max-w-[440px] rounded-2xl p-8">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-12 h-12 brand-gradient rounded-2xl flex items-center justify-center text-white font-black shadow-brand">
          智
        </div>
        <h1 class="text-2xl font-black text-ink-strong tracking-tight">物小智实验台</h1>
      </div>

      <h2 class="text-lg font-black text-ink-strong mb-5">{{ mode === 'login' ? '账号登录' : '重置密码' }}</h2>

      <form class="space-y-3" @submit.prevent="submit">
        <div class="login-field">
          <svg class="login-field-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
            <circle cx="12" cy="7" r="4" />
          </svg>
          <input
            v-model.trim="form.username"
            class="login-input"
            autocomplete="username"
            placeholder="请输入账号"
          />
        </div>

        <div class="login-field">
          <svg class="login-field-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
          <input
            v-model="form.password"
            class="login-input"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            :placeholder="mode === 'login' ? '请输入密码' : '请输入新密码，至少 6 位'"
          />
          <button type="button" class="login-field-toggle" @click="showPassword = !showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'">
            <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a21.77 21.77 0 0 1 5.06-6.06" />
              <path d="M9.9 4.24A10.94 10.94 0 0 1 12 4c7 0 11 8 11 8a21.86 21.86 0 0 1-3.17 4.19" />
              <path d="M14.12 14.12A3 3 0 1 1 9.88 9.88" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          </button>
        </div>

        <p v-if="tip" class="login-tip">
          {{ tip }}
        </p>

        <button type="submit" class="btn-brand w-full h-12 rounded-xl text-sm font-black mt-2" :disabled="loading">
          {{ loading ? '处理中...' : mode === 'login' ? '登录' : '确认重置并进入实验台' }}
        </button>

        <button type="button" class="w-full h-9 text-xs font-bold text-brand-700" @click="toggleMode">
          {{ mode === 'login' ? '忘记密码？重置密码' : '返回登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const tip = ref('')
const showPassword = ref(false)
const form = reactive({
  username: '',
  password: ''
})

let tipTimer

function showTip(message) {
  tip.value = message
  window.clearTimeout(tipTimer)
  tipTimer = window.setTimeout(() => {
    tip.value = ''
  }, 2200)
}

async function submit() {
  tip.value = ''
  if (!form.username) {
    showTip('请输入账号')
    return
  }
  if (!form.password) {
    showTip(mode.value === 'login' ? '请输入密码' : '请输入新密码')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login({ username: form.username, password: form.password })
    } else {
      await auth.resetPassword({
        username: form.username,
        newPassword: form.password
      })
    }
    router.replace(route.query.redirect || '/')
  } catch (e) {
    showTip(mode.value === 'login' ? '账号或密码错误' : '重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function toggleMode() {
  mode.value = mode.value === 'login' ? 'reset' : 'login'
  tip.value = ''
  form.password = ''
  showPassword.value = false
}
</script>

<style scoped>
.login-page {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  min-height: 100dvh;
  border-radius: 0;
  background-image: url('/images/login-lab-bg.png');
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  background-color: #eef4ff;
}

.login-card {
  position: relative;
  z-index: 2;
  margin-left: clamp(2rem, 8vw, 10rem);
  border: 1px solid rgba(255, 255, 255, 0.84);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.9)),
    rgba(255, 255, 255, 0.92);
  box-shadow:
    0 30px 86px rgba(44, 62, 118, 0.22),
    0 0 42px rgba(99, 102, 241, 0.11),
    0 0 0 1px rgba(105, 101, 255, 0.1);
  backdrop-filter: blur(26px) saturate(1.2);
}

.login-field {
  position: relative;
  display: flex;
  align-items: center;
}

.login-field-icon {
  position: absolute;
  left: 14px;
  width: 18px;
  height: 18px;
  color: #94a3b8;
  pointer-events: none;
}

.login-field-toggle {
  position: absolute;
  right: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #94a3b8;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease;
}

.login-field-toggle:hover {
  color: #6750e5;
  background: rgba(103, 80, 229, 0.08);
}

.login-field-toggle svg {
  width: 18px;
  height: 18px;
}

.login-input {
  width: 100%;
  height: 46px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 0 14px 0 42px;
  font-size: 14px;
  color: var(--ink-strong);
  background: #f8fafc;
  outline: none;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.login-field:has(.login-field-toggle) .login-input {
  padding-right: 48px;
}

.login-input::placeholder {
  color: #b6bccb;
}

.login-input:focus {
  border-color: rgba(103, 80, 229, 0.55);
  background: white;
  box-shadow: 0 0 0 3px rgba(103, 80, 229, 0.12);
}

.login-tip {
  min-height: 34px;
  border: 1px solid rgba(248, 113, 113, 0.18);
  border-radius: 12px;
  padding: 8px 12px;
  color: #dc2626;
  background: rgba(254, 242, 242, 0.78);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  box-shadow: 0 10px 28px rgba(248, 113, 113, 0.08);
}

@media (max-width: 720px) {
  .login-page {
    justify-content: center;
    background-position: 62% center;
  }

  .login-card {
    margin-left: 0;
  }
}
</style>
