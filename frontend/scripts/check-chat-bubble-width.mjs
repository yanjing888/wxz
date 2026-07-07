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
const labHeader = read('src/components/layout/LabHeader.vue')
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
  labStore,
  '我会围绕你当前的实验步骤',
  'The welcome message should stay centered on the current experiment instead of fixed feature categories.'
)

assertContains(
  labStore,
  '<strong>遇到卡点</strong>',
  'The welcome guide should invite students to describe blockers.'
)

assertContains(
  labStore,
  '<strong>需要判断</strong>',
  'The welcome guide should mention analysis based on uploaded evidence.'
)

assertContains(
  labStore,
  '<strong>继续推进</strong>',
  'The welcome guide should support moving through the experiment workflow.'
)

assertContains(
  read('src/styles/main.css'),
  '.welcome-guide-row',
  'Welcome guide rows should have CSS that fills the bubble width.'
)

assertContains(
  labHeader,
  'difyStatus',
  'The brand status dot should be bound to Dify service status instead of staying decorative.'
)

assertContains(
  labHeader,
  'difyDotClass',
  'The brand status dot should derive its color from Dify service availability.'
)

assertContains(
  labStore,
  'loadDifyStatus',
  'The lab store should expose a Dify status loader for the header dot.'
)

assertContains(
  labStore,
  'envCheckAvailable',
  'The environment status should be tied to the Dify env-check workflow availability.'
)

assertContains(
  labStore,
  "workflowStatuses?.['env-check']",
  'The environment status should check the safety monitoring Dify workflow specifically.'
)

assertContains(
  labStore,
  'DIFY_STATUS_UNAVAILABLE_INTERVAL',
  'Dify status polling should check unavailable services more frequently than available services.'
)

assertContains(
  labStore,
  'DIFY_STATUS_AVAILABLE_INTERVAL',
  'Dify status polling should slow down when services are available.'
)

assertContains(
  labStore,
  'refreshDifyStatusIfStale',
  'Critical Dify operations should refresh stale status before running.'
)

assertNotContains(
  labStore,
  '左侧工作区会按步骤引导你操作：',
  'The welcome message should avoid the older wide-card wording.'
)

assertNotContains(
  labStore,
  '<strong>数据采集</strong>',
  'The welcome message should avoid overly specific feature categories.'
)

assertNotContains(
  labStore,
  '<strong>现场确认</strong>',
  'The welcome message should avoid overly specific feature categories.'
)

assertNotContains(
  labStore,
  '<strong>操作说明</strong>',
  'The welcome message should avoid overly specific feature categories.'
)

assertContains(
  markdown,
  "text.includes('welcome-guide')",
  'Welcome HTML should bypass AI short-title normalization so stray headings are not rendered.'
)

console.log('Chat bubble width rules look good.')
