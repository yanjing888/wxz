import { createRequire } from 'node:module'
import { appendFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

// Vite otherwise treats stdin EOF as a request to stop the server. A fixed
// workstation service must survive the launcher closing its input stream.
process.env.CI = 'true'
const logRoot = fileURLToPath(new URL('../../logs/', import.meta.url))
const lifecycle = (event, details = {}) => {
  const record = { timestamp: new Date().toISOString(), event, pid: process.pid, parentPid: process.ppid, uptimeSeconds: Math.round(process.uptime()), ...details }
  try {
    mkdirSync(logRoot, { recursive: true })
    appendFileSync(join(logRoot, 'frontend-lifecycle.jsonl'), JSON.stringify(record) + '\n')
  } catch { /* Logging must never stop the dev server. */ }
}
lifecycle('frontend-start', { node: process.version, runtimeFlags: process.execArgv, consoleAttached: !!process.stdout.isTTY, arguments: process.argv.slice(2) })
process.once('exit', code => lifecycle('frontend-exit', { code }))
process.on('uncaughtExceptionMonitor', error => lifecycle('frontend-uncaught-error', { message: error.message, stack: error.stack }))
process.stdin.once('end', () => lifecycle('frontend-stdin-closed', { ignored: true }))
setInterval(() => lifecycle('frontend-alive', { memory: process.memoryUsage().rss }), 60000).unref()
const require = createRequire(import.meta.url)
const cli = join(dirname(require.resolve('vite/package.json')), 'bin', 'vite.js')
await import(pathToFileURL(cli).href)
