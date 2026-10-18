import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const composer = readFileSync(resolve(root, 'src/components/chat/Composer.vue'), 'utf8')

function assertContains(text, message) {
  if (!composer.includes(text)) {
    throw new Error(message)
  }
}

function assertNotContains(text, message) {
  if (composer.includes(text)) {
    throw new Error(message)
  }
}

assertContains(
  "'问物小智：实验中遇到的问题，都可以在这里说…（Enter 发送，Shift+Enter 换行）'",
  'composer placeholder should invite broad experiment help without enumerating fixed capabilities'
)

assertNotContains(
  '向物小智询问实验操作问题',
  'composer placeholder should not use the older generic operation-question copy'
)

for (const fixedCapability of ['步骤指导、数据处理、拍照纠错、报告生成', '步骤指导', '数据处理', '拍照纠错', '报告生成']) {
  assertNotContains(
    fixedCapability,
    `composer placeholder should not lock the assistant to fixed capability copy: ${fixedCapability}`
  )
}

console.log('composer placeholder copy present')
