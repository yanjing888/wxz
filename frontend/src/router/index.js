import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', name: 'lab', component: () => import('../views/LabView.vue'), meta: { requiresAuth: true } },
  { path: '/lab', redirect: '/' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return { name: 'lab' }
  }
  return true
})

export default router
