import { readFileSync, existsSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const CONFIG_DIR = dirname(fileURLToPath(import.meta.url))
const PORTS_FILE = resolve(CONFIG_DIR, 'ports.env')

const DEFAULTS = {
  BACKEND_PORT: 8082,
  FRONTEND_PORT: 5174,
  BENCH_CAMERA_IP: '188.18.31.195',
  BENCH_CAMERA_IP_DIRECT: '188.18.31.195',
  BENCH_CAMERA_MODE: 'auto'
}

export function loadPorts(filePath = PORTS_FILE) {
  const ports = { ...DEFAULTS }
  if (!existsSync(filePath)) return ports

  const content = readFileSync(filePath, 'utf8')
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const idx = trimmed.indexOf('=')
    if (idx < 0) continue
    const key = trimmed.slice(0, idx).trim()
    const rawValue = trimmed.slice(idx + 1).trim()
    const value = Number(rawValue)
    if ((key === 'BACKEND_PORT' || key === 'FRONTEND_PORT') && Number.isFinite(value)) {
      ports[key] = value
    }
    if (key === 'BENCH_CAMERA_IP' && rawValue) {
      ports[key] = rawValue
    }
    if (key === 'BENCH_CAMERA_IP_DIRECT' && rawValue) {
      ports[key] = rawValue
    }
    if (key === 'BENCH_CAMERA_MODE' && rawValue) {
      ports[key] = rawValue
    }
  }
  if (!ports.BENCH_CAMERA_IP_DIRECT) {
    ports.BENCH_CAMERA_IP_DIRECT = ports.BENCH_CAMERA_IP
  }
  return ports
}

export const portsFilePath = PORTS_FILE
