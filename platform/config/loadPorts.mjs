import { readFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const { parse } = require('yaml')
export const portsFilePath = resolve(dirname(fileURLToPath(import.meta.url)), 'application.yml')
export function loadApplication(filePath = portsFilePath) {
  if (!existsSync(filePath)) throw new Error(`Missing ${filePath}; copy application.yml.example and configure MySQL first.`)
  try { return parse(readFileSync(filePath, 'utf8')) || {} }
  catch { throw new Error(`Invalid YAML in ${filePath}; check indentation and duplicate keys.`) }
}
export function loadPorts(filePath = portsFilePath) {
  const config = loadApplication(filePath)
  const app = config.wuxiaozhi || {}
  const port = (value, name) => {
    const number = Number(value)
    if (!Number.isInteger(number) || number < 1 || number > 65535) throw new Error(`Invalid ${name} in ${filePath}`)
    return number
  }
  return {
    BACKEND_PORT: port(config.server?.port, 'server.port'),
    FRONTEND_PORT: port(app.frontend?.port, 'wuxiaozhi.frontend.port'),
    BENCH_CAMERA_IP: app.lab?.['camera-host'] || '',
    BENCH_CAMERA_IP_DIRECT: app.lab?.['camera-direct-host'] || app.lab?.['camera-host'] || '',
    BENCH_CAMERA_MODE: app['bench-camera']?.['connection-mode'] || 'auto'
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const ports = loadPorts()
  if (process.argv.includes('--cmd')) console.log(`BACKEND_PORT=${ports.BACKEND_PORT}\nFRONTEND_PORT=${ports.FRONTEND_PORT}`)
  else console.log(JSON.stringify(ports))
}
