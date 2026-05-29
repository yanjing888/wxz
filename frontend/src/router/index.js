import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'lab', component: () => import('../views/LabView.vue') },
  { path: '/lab', redirect: '/' },
  { path: '/login', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
