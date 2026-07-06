import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const projectRoot = resolve(root, '..')

function readFromFrontend(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

function readFromProject(path) {
  return readFileSync(resolve(projectRoot, path), 'utf8')
}

function assertContains(body, text, message) {
  if (!body.includes(text)) {
    throw new Error(message)
  }
}

function assertNotContains(body, text, message) {
  if (body.includes(text)) {
    throw new Error(message)
  }
}

const appConfig = readFromProject('backend/src/main/resources/application.yml')
const viteConfig = readFromFrontend('vite.config.js')
const panel = readFromFrontend('src/components/monitor/BenchCameraPanel.vue')

assertContains(
  appConfig,
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:/ws/hdl/hlsram/live0.flv}',
  'default browser stream URL should use the camera WebSocket path through the laptop origin'
)
assertNotContains(
  appConfig,
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:ws://192.168.0.15',
  'default browser stream URL should not point tablets directly at the camera IP'
)
assertNotContains(
  appConfig,
  '/bench-camera-proxy/ws/hdl/hlsram/live0.flv',
  'default browser stream URL should avoid a rewritten WebSocket proxy prefix'
)

assertContains(viteConfig, 'cameraProxyTarget', 'vite should define a camera proxy target')
assertContains(viteConfig, "'/ws'", 'vite should expose the camera WebSocket path at the same origin')
assertContains(viteConfig, 'ws: true', 'camera proxy should support WebSocket streaming')
assertNotContains(viteConfig, "rewrite: (path) => path.replace(/^\\/bench-camera-proxy/, '')", 'camera WebSocket proxy should not depend on upgrade path rewriting')

assertContains(panel, 'resolveBrowserStreamUrl', 'camera panel should normalize proxy URLs before flv.js playback')
assertContains(panel, "url.startsWith('/ws/')", 'camera panel should recognize same-origin camera WebSocket URLs')
assertContains(panel, 'window.location.protocol === \'https:\' ? \'wss\' : \'ws\'', 'camera panel should choose ws or wss from the page protocol')
assertContains(panel, 'window.location.host', 'camera panel should use the tablet-visible frontend host for proxied streams')

console.log('bench camera proxy wiring present')
