<template>
  <div class="student-app h-full flex flex-col">
    <AppTopBar title="" subtitle="" @logout="logout">
      <template #left-extra>
        <StudentExperimentSwitcher v-if="route.name !== 'my-learning'" />
      </template>
      <template #center>
        <StudentTabBar />
      </template>
    </AppTopBar>

    <main class="content-area flex-1 min-h-0">
      <router-view v-slot="{ Component }">
        <component :is="Component" v-if="Component" class="page-root" />
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { watch } from 'vue'
import { useTaskStore } from '../stores/tasks'
import AppTopBar from '../components/layout/AppTopBar.vue'
import StudentExperimentSwitcher from '../components/layout/StudentExperimentSwitcher.vue'
import StudentTabBar from '../components/layout/StudentTabBar.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const route=useRoute(),tasks=useTaskStore()
watch(()=>route.fullPath,()=>tasks.load().catch(()=>{}))

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
