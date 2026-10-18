import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Capacitor } from '@capacitor/core'
import App from './App.vue'
import router from './router'
import 'katex/dist/katex.min.css'
import './styles/main.css'

if (Capacitor.isNativePlatform()) {
  const root = document.documentElement
  root.classList.add('native-shell')

  const syncNativeViewportHeight = () => {
    root.style.setProperty('--native-app-height', `${window.innerHeight}px`)
  }

  syncNativeViewportHeight()
  window.addEventListener('resize', syncNativeViewportHeight)
  window.addEventListener('orientationchange', syncNativeViewportHeight)
  window.visualViewport?.addEventListener('resize', syncNativeViewportHeight)
}

createApp(App).use(createPinia()).use(router).mount('#app')
