import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
await build({
  entryPoints: [fileURLToPath(new URL('../src/utils/prepReportDocx.js', import.meta.url))],
  outfile: fileURLToPath(new URL('../public/newton-rings-simulation/student-report-docx.js', import.meta.url)),
  bundle: true, minify: true, format: 'esm', platform: 'browser', target: 'es2020',
});
