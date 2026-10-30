<template>
  <section class="process-appendix" data-report-section="process-checks">
    <h2>附录 过程检查记录汇总</h2>
    <p v-if="loading">正在读取过程检查记录…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="!records.length">暂无过程检查记录。</p>
    <section v-for="group in groups" v-else :key="group.id">
      <h3>步骤 {{ group.id }}　{{ group.title }}</h3>
      <table><colgroup><col style="width:24%" /><col style="width:16%" /><col style="width:16%" /><col style="width:44%" /></colgroup><thead><tr><th>时间</th><th>检查结果</th><th>得分 / 满分</th><th>检查意见或提交说明</th></tr></thead><tbody>
        <tr v-for="(record, index) in group.records" :key="index"><td>{{ record.time?.replace('T', ' ').slice(0, 19) || '—' }}</td><td>{{ record.result }}</td><td>{{ record.score == null ? '—' : `${record.score} / ${record.maxScore}` }}</td><td>{{ record.note || '—' }}</td></tr>
      </tbody></table>
    </section>
  </section>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ records: { type: Array, default: () => [] }, loading: Boolean, error: String })
const groups = computed(() => {
  const map = new Map()
  for (const record of props.records) {
    if (!map.has(record.stepId)) map.set(record.stepId, { id: record.stepId, title: record.stepTitle, records: [] })
    map.get(record.stepId).records.push(record)
  }
  return [...map.values()].sort((a, b) => a.id - b.id)
})
</script>
<style scoped>
.process-appendix{margin-top:28px;color:#111;font:14px/1.7 'SimSun','宋体',serif;background:#fff;padding:20px 0}.process-appendix h2{font-size:18px;font-weight:700;color:#111;margin:0 0 16px;break-after:avoid}.process-appendix h3{font-size:15px;font-weight:600;margin:20px 0 10px;break-after:avoid}.process-appendix p{color:#475569}.process-appendix table{width:100%;table-layout:fixed;border-collapse:collapse;font-size:12px;margin-bottom:20px}.process-appendix th,.process-appendix td{border:1px solid #aeb6c1;padding:8px;vertical-align:top;text-align:left;white-space:pre-wrap;overflow-wrap:anywhere}.process-appendix th{background:#f1f3f5;font-weight:700}.process-appendix thead{display:table-header-group}.process-appendix tr{break-inside:avoid}
</style>
