import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

function assertContains(file, text, message) {
  const body = read(file)
  if (!body.includes(text)) {
    throw new Error(`${file}: ${message}`)
  }
}

assertContains('src/styles/main.css', '@supports (height: 100dvh)', 'root viewport should use dynamic viewport height for tablet browsers')
assertContains('src/styles/main.css', 'height: 100dvh', 'root viewport should fit Chrome tablet visible viewport')
assertContains('src/styles/main.css', '--app-min-height', 'layout should keep a usable minimum height when the browser viewport is short')
assertContains('src/styles/main.css', 'overflow-y: auto', 'short tablet viewports should allow page scrolling instead of clipping')
assertContains('src/styles/main.css', '#app {\n    overflow-y: auto', 'the app container should scroll when the fixed-height lab surface is taller than Chrome tablet viewport')
assertContains('src/views/LabView.vue', 'lab-page', 'lab view should use the tablet-safe root layout class')

console.log('tablet viewport layout rules present')
