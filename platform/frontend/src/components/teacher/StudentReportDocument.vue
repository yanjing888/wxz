<template>
  <article ref="documentRoot" class="report-doc" :class="{ compact }">
    <header class="doc-header">
      <h1 class="doc-title">{{ report?.experimentName || '实验报告' }}</h1>
      <div class="doc-subtitle">大学物理实验报告</div>
      <table class="doc-info-table">
        <tbody>
        <tr>
          <td class="doc-info-label">姓名</td>
          <td class="doc-info-value">{{ report?.studentName || '—' }}</td>
          <td class="doc-info-label">班级</td>
          <td class="doc-info-value">{{ report?.studentClass || '—' }}</td>
        </tr>
        <tr>
          <td class="doc-info-label">实验</td>
          <td class="doc-info-value">{{ report?.experimentName || '—' }}</td>
          <td class="doc-info-label">日期</td>
          <td class="doc-info-value">{{ reportDate }}</td>
        </tr>
        </tbody>
      </table>
    </header>
    <div v-if="!hasAnySection" class="doc-empty-all">学生尚未提交报告正文。</div>
    <div v-else class="doc-body">
      <section v-for="sec in sections" :key="sec.key" :data-report-section="sec.key" class="doc-section">
        <h2 class="doc-heading">{{ sec.label }}</h2>
        <div v-if="sec.hasContent" class="doc-content" v-html="formatPreviewSection(sec.html)" />
        <p v-else class="doc-empty">（未填写）</p>
      </section>
    </div>
    <ProcessCheckAppendix :records="report?.processCheckRecords || []" />
  </article>
</template>

<script setup>
import { computed, ref } from 'vue'
import ProcessCheckAppendix from '../experiment/ProcessCheckAppendix.vue'
import { REPORT_SECTION_DEFS } from '../../utils/sessionReport'
import { renderMathInMarkdown } from '../../utils/markdown'

const props = defineProps({
  report: { type: Object, default: null },
  compact: { type: Boolean, default: false }
})
const documentRoot = ref(null)
function scrollToSection(key) {
  const section = documentRoot.value?.querySelector(`[data-report-section="${key}"]`)
  const scroller = documentRoot.value?.parentElement
  if (!section || !scroller) return
  scroller.scrollTo({ top: scroller.scrollTop + section.getBoundingClientRect().top - scroller.getBoundingClientRect().top - 16, behavior: 'smooth' })
}
defineExpose({ scrollToSection })

const sections = computed(() => {
  const raw = props.report?.studentReportSections
  const map = raw && typeof raw === 'object' ? raw : {}
  return REPORT_SECTION_DEFS.map((def) => {
    const html = String(map[def.key] || '').trim()
    return {
      key: def.key,
      label: def.label,
      html,
      hasContent: hasText(html)
    }
  })
})

const hasAnySection = computed(() => sections.value.some((sec) => sec.hasContent))

const reportDate = computed(() => {
  const raw = props.report?.generatedAt
  if (!raw) return '—'
  const d = new Date(raw)
  if (!Number.isNaN(d.getTime())) {
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
  }
  return String(raw).replace(/\s+\d{2}:\d{2}.*$/, '')
})

function hasText(html) {
  return /<img\b/i.test(html) || String(html || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/gi, ' ').trim().length > 0
}

function formatPreviewSection(html) {
  if (!html?.trim()) return ''
  const withMath = html.replace(
    /<span class="inline-formula"[^>]*>\$([^<]+)\$<\/span>/gi,
    (_, tex) => `$${tex}$`
  )
  // Keep student photos and tables, including sections that contain only a photo.
  const content = /<(p|div|table|ul|ol|h[1-6])\b/i.test(withMath) ? withMath : withMath.replace(/\n/g, '<br>')
  const rendered = renderMathInMarkdown(content)
  const doc = new DOMParser().parseFromString(rendered, 'text/html')
  doc.querySelectorAll('script,style,iframe,object,embed,link,meta,base,form,input,button,textarea,select,foreignObject,use,animate,animateMotion,animateTransform,set').forEach(el => el.remove())
  for (const el of doc.body.querySelectorAll('*')) {
    for (const attr of [...el.attributes]) {
      if (/^on/i.test(attr.name) || ['srcdoc', 'id', 'name'].includes(attr.name.toLowerCase()) || (attr.name === 'style' && /url\s*\(|expression\s*\(|@import/i.test(attr.value))) el.removeAttribute(attr.name)
      if (['src', 'href', 'xlink:href', 'action', 'srcset'].includes(attr.name.toLowerCase())) {
        const url = attr.value.trim()
        const safe = /^(https?:\/\/|\/(?!\/)|\.\.?\/|#)/i.test(url) || (el.tagName === 'IMG' && /^data:image\/(png|jpe?g|gif|webp|bmp);base64,/i.test(url))
        if (!safe || attr.name.toLowerCase() === 'srcset') el.removeAttribute(attr.name)
      }
    }
  }
  return doc.body.innerHTML
}
</script>

<style scoped>
.report-doc {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  background: #fff;
  padding: 48px 52px 40px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.08);
  min-height: 640px;
  font-family: "SimSun", "宋体", "Times New Roman", serif;
  color: #1a1a1a;
  line-height: 1.9;
}
.report-doc.compact {
  padding: 28px 32px 24px;
  min-height: 0;
  box-shadow: none;
  border: 1px solid #e4e9f3;
  border-radius: 12px;
}
.doc-header {
  text-align: center;
  margin-bottom: 28px;
  padding-bottom: 16px;
  border-bottom: 2px solid #1a1a1a;
}
.doc-title {
  font-size: 22px;
  font-weight: 700;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  margin: 0 0 4px;
  letter-spacing: 2px;
}
.doc-subtitle {
  font-size: 14px;
  color: #555;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  letter-spacing: 1px;
}
.doc-info-table {
  width: 100%;
  margin-top: 14px;
  border-collapse: collapse;
  font-size: 13px;
}
.doc-info-table td {
  padding: 2px 6px;
  border: none;
}
.doc-info-label {
  text-align: right;
  font-weight: 600;
  white-space: nowrap;
  width: 60px;
  color: #333;
}
.doc-info-value {
  text-align: left;
  border-bottom: 1px solid #999;
  min-width: 120px;
  color: #1a1a1a;
}
.doc-empty-all {
  padding: 48px 12px;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
  font-family: "Microsoft YaHei", sans-serif;
}
.doc-section { margin-bottom: 18px; }
.doc-heading {
  font-size: 15px;
  font-weight: 700;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  margin: 0 0 6px;
}
.doc-content {
  font-size: 14px;
  line-height: 2;
  word-break: break-word;
}
.doc-content :deep(p) { margin: 0 0 8px; }
.doc-content :deep(ul), .doc-content :deep(ol) { padding-left: 24px; }
.doc-content :deep(.katex-display) { overflow-x: auto; overflow-y: hidden; }
.doc-section { scroll-margin-top: 16px; }
.compact .doc-info-value { min-width: 0; }
@media (max-width: 1500px) { .report-doc.compact { padding: 24px 20px; } .compact .doc-title { font-size: 19px; letter-spacing: 0; } .compact .doc-info-table { font-size: 12px; } .compact .doc-info-label { width: auto; } }
.doc-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 6px 0;
  text-indent: 0;
}
.doc-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0 12px;
  font-size: 12px;
  text-indent: 0;
}
.doc-content :deep(th),
.doc-content :deep(td) {
  border: 1px solid #333;
  padding: 4px 8px;
}
.doc-empty {
  color: #94a3b8;
  font-size: 13px;
  margin: 0;
}
</style>
