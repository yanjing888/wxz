/**
 * 从 Cursor agent-transcripts 里提取对「物小智-项目」的 Write 记录并写回磁盘。
 * 用法：node scripts/extract-transcript-writes.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const transcriptsRoot = path.join(
  process.env.USERPROFILE || '',
  '.cursor',
  'projects',
);
const project = path.resolve(import.meta.dirname, '..');
const needle = '物小智-项目';

let writes = 0;
let restored = 0;

function walk(dir, fn) {
  if (!fs.existsSync(dir)) return;
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) walk(p, fn);
    else fn(p);
  }
}

walk(transcriptsRoot, (file) => {
  if (!file.endsWith('.jsonl')) return;
  for (const line of fs.readFileSync(file, 'utf8').split(/\n/)) {
    if (!line.includes('Write') || !line.includes(needle)) continue;
    let o;
    try {
      o = JSON.parse(line);
    } catch {
      continue;
    }
    for (const part of o.message?.content || []) {
      if (part.type !== 'tool_use' || part.name !== 'Write') continue;
      const inp = part.input || {};
      if (!inp.path?.includes(needle) || typeof inp.contents !== 'string') continue;
      writes++;
      const dest = inp.path.replace(/\\/g, path.sep);
      if (!dest.toLowerCase().startsWith(project.toLowerCase())) continue;
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, inp.contents, 'utf8');
      restored++;
    }
  }
});

console.log(JSON.stringify({ transcriptsRoot, project, writes, restored }, null, 2));
