<template>
  <header class="app-header">
    <div class="header-main">
      <div class="brand-block">
        <img src="/images/jyd-logo.png" alt="竞业达 JYD" class="jyd-logo" />
        <div class="brand-text">
          <h1 class="brand-title">{{ title }}</h1>
          <p v-if="subtitle" class="brand-sub">{{ subtitle }}</p>
        </div>
      </div>

      <slot name="left-extra" />

      <slot name="center">
        <p v-if="center" class="header-center">{{ center }}</p>
      </slot>

      <div class="user-area">
        <div class="user-avatar brand-gradient">{{ userInitial }}</div>
        <span class="user-name">{{ auth.displayName || auth.username }}</span>
        <span class="user-sep" aria-hidden="true" />
        <button type="button" class="logout-link" @click="emit('logout')">退出</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'

defineProps({
  title: { type: String, default: '大学物理实验智能体' },
  subtitle: { type: String, default: '' },
  center: { type: String, default: '' }
})

const emit = defineEmits(['logout'])
const auth = useAuthStore()

const userInitial = computed(() => {
  const n = (auth.displayName || auth.username || '').trim()
  return n ? n.charAt(0) : '用'
})
</script>

<style scoped>
.app-header {
  @apply shrink-0 bg-white border-b border-line-soft px-6;
}
.header-main {
  @apply flex items-center gap-4 h-14;
}
.brand-block {
  @apply flex items-center gap-3 shrink-0;
}
.jyd-logo {
  @apply h-8 w-auto shrink-0 object-contain;
}
.brand-text {
  @apply min-w-0 leading-tight;
}
.brand-title {
  @apply text-[17px] font-bold text-ink-strong truncate;
}
.brand-sub {
  @apply mt-0.5 text-[12px] text-ink-faint truncate;
}
.header-center {
  @apply flex-1 flex items-center justify-center min-w-0;
}
.user-area {
  @apply flex items-center gap-2.5 shrink-0 ml-auto;
}
.user-avatar {
  @apply w-7 h-7 rounded-full flex items-center justify-center text-white text-[11px] font-bold shrink-0;
}
.user-name {
  @apply text-[13px] text-ink-base truncate max-w-[100px];
}
.user-sep {
  @apply w-px h-3.5 bg-line-soft shrink-0;
}
.logout-link {
  @apply text-[13px] text-ink-muted hover:text-brand-600 transition-colors;
}

@media (max-width: 980px), (max-height: 760px) {
  .app-header {
    @apply px-3;
  }
  .header-main {
    @apply gap-3 h-12;
  }
  .jyd-logo {
    @apply h-7;
  }
  .brand-text {
    @apply hidden;
  }
  .user-name {
    @apply max-w-[72px];
  }
}

@media (max-width: 760px) {
  .header-main {
    @apply gap-2;
  }
  .brand-text {
    @apply hidden;
  }
}

@media (max-width: 760px) {
  .user-name,
  .user-sep {
    @apply hidden;
  }
}
</style>
