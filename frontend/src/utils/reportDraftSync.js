import { studentExperimentApi } from '../api'
import { REPORT_SECTION_DEFS } from './sessionReport'

const DRAFT_PREFIX = 'wxz_report_draft_'
const SECTION_KEYS = REPORT_SECTION_DEFS.map((d) => d.key)

function plainLength(html) {
  return String(html || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/gi, ' ').trim().length
}

export function extractReportSections(raw) {
  const sections = {}
  if (!raw || typeof raw !== 'object') return sections
  SECTION_KEYS.forEach((key) => {
    sections[key] = raw[key] || ''
  })
  return sections
}

export function reportSectionsLength(sections) {
  return SECTION_KEYS.reduce((sum, key) => sum + plainLength(sections?.[key]), 0)
}

export function readLocalReportDrafts() {
  const drafts = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key || !key.startsWith(DRAFT_PREFIX)) continue
    try {
      const parsed = JSON.parse(localStorage.getItem(key) || 'null')
      if (!parsed || typeof parsed !== 'object') continue
      const sections = extractReportSections(parsed)
      const len = reportSectionsLength(sections)
      if (len < 8) continue
      drafts.push({
        key,
        sessionId: Number(parsed.sessionId || key.slice(DRAFT_PREFIX.length)) || null,
        experimentCode: parsed.experimentCode || '',
        sections,
        len
      })
    } catch {
      // skip broken drafts
    }
  }
  return drafts.sort((a, b) => b.len - a.len)
}

let syncing = false

/** 把浏览器里已提交实验的报告草稿补传到服务器，供教师端评阅。 */
export async function syncSubmittedReportDrafts() {
  if (syncing) return
  syncing = true
  try {
    const { data } = await studentExperimentApi.listProgress()
    const submitted = (data || []).filter((row) => row?.reportCompleted && row?.experimentCode)
    if (!submitted.length) return
    const drafts = readLocalReportDrafts()
    if (!drafts.length) return

    for (const row of submitted) {
      const serverLen = reportSectionsLength(row.reportSections)
      if (serverLen >= 8) continue
      const match = drafts.find((d) => d.experimentCode === row.experimentCode) || (submitted.length === 1 ? drafts[0] : null)
      if (!match) continue
      await studentExperimentApi.completeReport(row.experimentCode, {
        sessionId: match.sessionId || row.finishedSessionId || row.activeSessionId || null,
        sections: match.sections
      })
    }
  } catch {
    // 补传失败不阻断页面
  } finally {
    syncing = false
  }
}
