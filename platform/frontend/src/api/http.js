import axios from 'axios'
import { Capacitor } from '@capacitor/core'
import { apiBaseUrl } from './runtime'

const http = axios.create({
  baseURL: Capacitor.isNativePlatform() ? apiBaseUrl : '',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wxz_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      ;['wxz_token', 'wxz_userId', 'wxz_username', 'wxz_displayName', 'wxz_class'].forEach((key) =>
        localStorage.removeItem(key)
      )
      const isNative = Capacitor.isNativePlatform()
      const onLogin = isNative
        ? window.location.hash.startsWith('#/login')
        : window.location.pathname === '/login'
      if (!onLogin) {
        window.location.assign(isNative ? '/#/login' : '/login')
      }
    }
    return Promise.reject(error)
  }
)

export default http
