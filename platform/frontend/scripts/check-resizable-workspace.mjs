import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

function read(relativePath) {
  return readFileSync(join(root, relativePath), 'utf8')
}

function assertContains(source, expected, message) {
  if (!source.includes(expected)) {
    throw new Error(message)
  }
}

function assertNotContains(source, unexpected, message) {
  if (source.includes(unexpected)) {
    throw new Error(message)
  }
}

const labView = read('src/views/LabView.vue')
const styles = read('src/styles/main.css')
const pkg = read('package.json')

assertContains(
  pkg,
  '"test:resizable-workspace": "node scripts/check-resizable-workspace.mjs"',
  'package.json should expose the resizable workspace check.'
)

assertContains(
  labView,
  'workspaceColumns',
  'Lab view should drive the two-panel layout with a reactive grid column style.'
)

assertContains(
  labView,
  'workspace-resizer',
  'Lab view should render a visible draggable splitter between the panels.'
)

assertContains(
  labView,
  '@pointerdown="startWorkspaceResize"',
  'Splitter should start resize with pointer events for mouse and tablet support.'
)

assertContains(
  labView,
  'localStorage.setItem(WORKSPACE_WIDTH_KEY',
  'Workspace width should persist between sessions.'
)

assertContains(
  labView,
  'setPointerCapture',
  'Splitter should capture pointer events while dragging.'
)

assertContains(
  styles,
  '.workspace-resizer',
  'Resizable workspace splitter should have global styling.'
)

assertContains(
  styles,
  'cursor: col-resize',
  'Splitter should communicate horizontal resizing.'
)

assertNotContains(
  labView,
  '<aside class="w-[400px]',
  'Left workspace should no longer be a fixed 400px sidebar.'
)

console.log('resizable workspace rules present')
