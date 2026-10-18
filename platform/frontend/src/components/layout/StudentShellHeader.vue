<template>
  <header class="shrink-0 flex items-center gap-4 px-6 h-14 bg-white border-b border-line-soft">
    <router-link to="/" class="flex items-center gap-2.5 shrink-0">
      <div class="w-9 h-9 brand-gradient rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-brand">智</div>
      <span class="text-[14px] font-bold text-ink-strong tracking-tight">物小智</span>
    </router-link>

    <span class="w-px h-7 bg-line-soft shrink-0" />

    <p class="text-[13px] text-ink-muted truncate flex-1">大学物理实验 · 学生端</p>

    <div class="flex items-center gap-2.5 shrink-0 pl-3 border-l border-line-soft">
      <div class="w-8 h-8 rounded-full brand-gradient flex items-center justify-center text-white text-xs font-bold shadow-card">{{ userInitial }}</div>
      <span class="text-xs font-semibold text-ink-base truncate max-w-[100px]">{{ auth.displayName || auth.username }}</span>
      <button type="button" class="btn-ghost px-3 py-2 rounded-xl text-xs font-semibold" @click="logout">退出</button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const userInitial = computed(() => {
  const n = (auth.displayName || auth.username || '').trim()
  return n ? n.charAt(0) : '学'
})

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>
