import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { loadPorts } from '../config/loadPorts.mjs'

const { BACKEND_PORT, FRONTEND_PORT } = loadPorts()
const backendOrigin = `http://127.0.0.1:${BACKEND_PORT}`
const cameraProxyTarget = process.env.BENCH_CAMERA_PROXY_TARGET || 'ws://192.168.0.15'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: FRONTEND_PORT,
    strictPort: true,
    proxy: {
      '/api': {
        target: backendOrigin,
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0
      },
      '/uploads': { target: backendOrigin, changeOrigin: true },
      '/ws': {
        target: cameraProxyTarget,
        changeOrigin: true,
        ws: true
      }
    }
  }
})
