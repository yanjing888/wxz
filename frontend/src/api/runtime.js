const DEV_BACKEND = 'http://127.0.0.1:8082'

function origin() {
  if (typeof window === 'undefined') return DEV_BACKEND
  return window.location.origin
}

export function apiUrl(path) {
  return path
}

export function mediaUrl(url) {
  if (!url) return url
  if (/^https?:\/\//.test(url)) return url
  return url
}

export const apiBaseUrl = import.meta.env?.DEV ? DEV_BACKEND : origin()
