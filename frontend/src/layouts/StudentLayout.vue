<template>
  <div class="student-app h-full flex flex-col">
    <AppTopBar @logout="logout">
      <StudentTabBar />
    </AppTopBar>

    <main class="content-area flex-1 min-h-0">
      <router-view v-slot="{ Component }">
        <component :is="Component" v-if="Component" class="page-root" />
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppTopBar from '../components/layout/AppTopBar.vue'
import StudentTabBar from '../components/layout/StudentTabBar.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.content-area {
  @apply flex flex-col overflow-hidden;
}
:deep(.page-root) {
  @apply flex-1 min-h-0 flex flex-col;
}
</style>
