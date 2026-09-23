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
const benchUtil = readFromFrontend('src/utils/benchCamera.js')
const portsEnv = readFromProject('config/ports.env')

assertContains(
  appConfig,
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:/ws/hdl/hlsram/live0.flv}',
  'default browser stream URL should use the camera WebSocket path through the laptop origin'
)
assertContains(
  appConfig,
  'browser-stream-url-direct:',
  'application config should expose a direct-link browser stream URL'
)
assertContains(
  appConfig,
  'connection-mode: ${BENCH_CAMERA_MODE:auto}',
  'application config should expose bench camera connection mode'
)
assertNotContains(
  appConfig,
  'browser-stream-url: ${BENCH_CAMERA_BROWSER_STREAM_URL:ws://192.168.0.15',
  'default browser stream URL should not point tablets directly at the camera IP'
)

assertContains(viteConfig, 'cameraProxyTarget', 'vite should define a camera proxy target')
assertContains(viteConfig, 'cameraDirectProxyTarget', 'vite should define a direct camera proxy target')
assertContains(viteConfig, 'BENCH_CAMERA_IP_DIRECT', 'vite should read the direct camera IP from shared local config')
assertContains(viteConfig, "'/ws-direct'", 'vite should expose the direct camera WebSocket path')
assertContains(viteConfig, "'/ws'", 'vite should expose the camera WebSocket path at the same origin')
assertContains(viteConfig, 'ws: true', 'camera proxy should support WebSocket streaming')

assertContains(benchUtil, 'benchCameraStreamCandidates', 'bench camera util should build stream URL candidates')
assertContains(benchUtil, '/ws-direct/', 'bench camera util should support direct proxy paths')
assertContains(panel, 'benchCameraStreamCandidates', 'camera panel should try lab and direct stream URLs')
assertContains(panel, 'resolveBenchStreamUrl', 'camera panel should normalize proxy URLs before flv.js playback')
assertContains(panel, 'startLiveBufferMonitor', 'camera panel should monitor and trim accumulated live buffer')

assertContains(portsEnv, 'BENCH_CAMERA_IP_DIRECT', 'ports.env should document direct camera IP')
assertContains(portsEnv, 'BENCH_CAMERA_MODE', 'ports.env should document camera connection mode')

console.log('bench camera proxy wiring present')
