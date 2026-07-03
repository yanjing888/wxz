import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

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

const chatBox = read('src/components/chat/ChatBox.vue')
const labStore = read('src/stores/lab.js')
const markdown = read('src/utils/markdown.js')

assertContains(
  chatBox,
  'bubbleBaseClass',
  'Chat bubbles should use an explicit shared base class.'
)

assertContains(
  chatBox,
  'aiBubbleClass',
  'AI bubbles should have a dedicated width/style class.'
)

assertContains(
  chatBox,
  'userBubbleClass',
  'User bubbles should have a dedicated adaptive width/style class.'
)

assertContains(
  chatBox,
  'w-[min(78%,680px)]',
  'AI bubbles should use a consistent compact reading width.'
)

assertNotContains(
  chatBox,
  'w-fit max-w-[min(78%,680px)]',
  'AI bubbles should not shrink independently because welcome and reply bubbles need matching widths.'
)

assertContains(
  chatBox,
  'max-w-[88%] brand-gradient-soft',
  'User bubbles should remain content-adaptive and right-aligned.'
)

assertNotContains(
  chatBox,
  'border max-w-[88%] chat-md',
  'The shared bubble class should not force AI bubbles to shrink-wrap like user bubbles.'
)

assertContains(
  labStore,
  'welcome-guide',
  'The welcome message should use a full-width guide layout inside the bubble.'
)

assertContains(
  labStore,
  'welcome-guide-row',
  'The welcome message should lay each helper item out as a full-width row.'
)

assertContains(
  read('src/styles/main.css'),
  '.welcome-guide-row',
  'Welcome guide rows should have CSS that fills the bubble width.'
)

assertNotContains(
  labStore,
  '左侧工作区会按步骤引导你操作：',
  'The welcome message should avoid the older wide-card wording.'
)

assertContains(
  markdown,
  "text.includes('welcome-guide')",
  'Welcome HTML should bypass AI short-title normalization so stray headings are not rendered.'
)

console.log('Chat bubble width rules look good.')
