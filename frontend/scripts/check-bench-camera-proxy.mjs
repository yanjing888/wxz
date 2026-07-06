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
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:/bench-camera-proxy/ws/hdl/hlsram/live0.flv}',
  'default browser stream URL should use the laptop same-origin proxy path'
)
assertNotContains(
  appConfig,
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:ws://192.168.0.15',
  'default browser stream URL should not point tablets directly at the camera IP'
)

assertContains(viteConfig, 'cameraProxyTarget', 'vite should define a camera proxy target')
assertContains(viteConfig, "'/bench-camera-proxy'", 'vite should expose the same-origin camera proxy path')
assertContains(viteConfig, 'ws: true', 'camera proxy should support WebSocket streaming')
assertContains(viteConfig, "rewrite: (path) => path.replace(/^\\/bench-camera-proxy/, '')", 'camera proxy should strip the frontend proxy prefix')

assertContains(panel, 'resolveBrowserStreamUrl', 'camera panel should normalize proxy URLs before flv.js playback')
assertContains(panel, "url.startsWith('/bench-camera-proxy')", 'camera panel should recognize same-origin camera proxy URLs')
assertContains(panel, 'window.location.protocol === \'https:\' ? \'wss\' : \'ws\'', 'camera panel should choose ws or wss from the page protocol')
assertContains(panel, 'window.location.host', 'camera panel should use the tablet-visible frontend host for proxied streams')

console.log('bench camera proxy wiring present')
