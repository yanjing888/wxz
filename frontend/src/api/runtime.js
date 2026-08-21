import { Capacitor } from '@capacitor/core'

const DEV_BACKEND = 'http://127.0.0.1:8082'
const NATIVE_BACKEND = 'http://188.18.55.230:8082'

function nativeBackend() {
  try {
    const stored = localStorage.getItem('wxz_backend_url')
    if (stored) return stored.replace(/\/+$/, '')
  } catch { /* ignore */ }
  return NATIVE_BACKEND
}

function origin() {
  if (typeof window === 'undefined') return DEV_BACKEND
  if (Capacitor.isNativePlatform()) return nativeBackend()
  return window.location.origin
}

export function apiUrl(path) {
  if (Capacitor.isNativePlatform()) return nativeBackend() + path
  return path
}

export function mediaUrl(url) {
  if (!url) return url
  if (/^https?:\/\//.test(url)) return url
  if (Capacitor.isNativePlatform()) return nativeBackend() + url
  return url
}

export const apiBaseUrl = import.meta.env?.DEV ? DEV_BACKEND : origin()
