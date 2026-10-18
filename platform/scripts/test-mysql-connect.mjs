import { spawnSync } from 'node:child_process'
import { loadApplication } from '../config/loadPorts.mjs'
const db = loadApplication().spring?.datasource
if (!db?.url?.startsWith('jdbc:mysql://')) throw new Error('Configure a MySQL JDBC URL in config/application.yml')
const url = new URL(db.url.slice(5))
const result = spawnSync(process.platform === 'win32' ? 'mysql.exe' : 'mysql', [
  '--protocol=TCP', '--connect-timeout=5', '--host=' + url.hostname, '--port=' + (url.port || 3306),
  '--user=' + db.username, '--database=' + decodeURIComponent(url.pathname.slice(1)), '-e', 'SELECT 1 AS ok;'
], { stdio: 'inherit', env: { ...process.env, MYSQL_PWD: String(db.password || '') } })
if (result.error) console.error('MySQL client not found. Install mysql.exe or check backend startup logs.')
process.exit(result.status ?? 1)
