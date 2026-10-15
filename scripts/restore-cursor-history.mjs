import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const history = path.join(process.env.APPDATA, 'Cursor', 'User', 'History');
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const needle = '物小智-项目';
let restored = 0;
let skipped = 0;

function walk(dir, fn) {
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) walk(p, fn);
    else fn(p);
  }
}

walk(history, (file) => {
  if (!file.endsWith('entries.json')) return;
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return;
  }
  const resource = decodeURIComponent(data.resource || '');
  if (!resource.includes(needle)) return;
  const rel = resource.replace(/^file:\/\/\//i, '').replace(/\//g, path.sep);
  if (!rel.toLowerCase().startsWith(project.toLowerCase())) return;
  const entries = data.entries || [];
  if (!entries.length) return;
  const latest = entries.reduce((a, b) => (a.timestamp > b.timestamp ? a : b));
  const snap = path.join(path.dirname(file), latest.id);
  if (!fs.existsSync(snap)) {
    skipped++;
    return;
  }
  fs.mkdirSync(path.dirname(rel), { recursive: true });
  fs.copyFileSync(snap, rel);
  restored++;
});

console.log(JSON.stringify({ project, restored, skipped }, null, 2));
