/** @param {Record<string, unknown> | null | undefined} camera */
export function benchCameraStreamCandidates(camera) {
  if (!camera?.enabled) return []
  const primary = String(camera.browserStreamUrl || '').trim()
  const direct = String(camera.browserStreamUrlDirect || '').trim()
  const mode = String(camera.connectionMode || 'auto').toLowerCase()

  if (mode === 'lab') return primary ? [primary] : []
  if (mode === 'direct') return direct ? [direct] : primary ? [primary] : []

  const urls = []
  if (primary) urls.push(primary)
  if (direct && direct !== primary) urls.push(direct)
  return urls
}

export function resolveBenchStreamUrl(url) {
  if (!url) return ''
  if (url.startsWith('/ws-direct/') || url.startsWith('/ws/')) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}${url}`
  }
  return url
}
