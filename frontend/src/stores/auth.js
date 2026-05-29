import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('wxz_token') || '',
    userId: localStorage.getItem('wxz_userId') || null,
    username: localStorage.getItem('wxz_username') || '',
    displayName: localStorage.getItem('wxz_displayName') || '',
    studentClass: localStorage.getItem('wxz_class') || ''
  }),
  actions: {
    persist(data) {
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.displayName = data.displayName
      this.studentClass = data.studentClass || ''
      localStorage.setItem('wxz_token', data.token)
      localStorage.setItem('wxz_userId', String(data.userId))
      localStorage.setItem('wxz_username', data.username)
      localStorage.setItem('wxz_displayName', data.displayName)
      localStorage.setItem('wxz_class', data.studentClass || '')
    },
    async register(form) {
      const { data } = await authApi.register(form)
      this.persist(data)
    },
    async login(form) {
      const { data } = await authApi.login(form)
      this.persist(data)
    },
    logout() {
      this.token = ''
      this.userId = null
      ;['wxz_token', 'wxz_userId', 'wxz_username', 'wxz_displayName', 'wxz_class'].forEach((k) => localStorage.removeItem(k))
    }
  }
})
