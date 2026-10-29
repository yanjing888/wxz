<template>
  <div class="learning-mask" @click.self="$emit('close')" @keydown.esc="$emit('close')">
    <section class="learning-dialog" role="dialog" aria-modal="true" aria-label="统计规则" tabindex="-1" ref="panel">
      <header><h2>统计规则</h2><button type="button" @click="$emit('close')">关闭</button></header>
      <h3>学习积分</h3>
      <table><thead><tr><th>环节</th><th>积分</th></tr></thead><tbody><tr v-for="rule in rules" :key="rule[0]"><td>{{ rule[0] }}</td><td>{{ rule[1] }}</td></tr></tbody></table>
      <p>每个实验的同一环节只计一次；教师确认成绩按百分制折算。重新提交、重复实验和重复订正不叠加积分。修改成绩、更新反馈或移除任务后，统计随记录更新。</p>
      <h3>能力雷达</h3>
      <dl><template v-for="item in dimensions" :key="item[0]"><dt>{{ item[0] }}</dt><dd>{{ item[1] }}</dd></template></dl>
      <h3>班级排行</h3><p>{{ experimentScoped ? '按当前所选实验在本班的积分排序，只计入已分配该实验的学生。' : '按本班已分配实验的总积分排序。' }}同分同名次；零积分不排名。学生只可查看所属班级，教师只可查看可管理班级。</p>
    </section>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
defineEmits(['close'])
defineProps({ experimentScoped: Boolean })
const panel = ref(null)
onMounted(() => panel.value?.focus())
const rules = [['完成仿真或预习', '10'], ['完成实物实验', '20'], ['记录正式实验数据', '10'], ['提交实验报告', '20'], ['完成当前教师反馈的订正', '10'], ['教师确认评分', '成绩 × 0.3，四舍五入']]
const dimensions = [['实验准备', '已完成仿真或预习的实验数 ÷ 已分配实验数。'], ['实验完成', '已完成实物实验数 ÷ 已分配实验数。'], ['数据质量', '各实验步骤最新正式数据中，明确校验通过的比例。未校验的数据不参与计算。'], ['报告质量', '教师已确认报告的平均分。'], ['反馈订正', '已完成当前反馈订正的报告数 ÷ 已批阅报告数。']]
</script>
<style scoped>
.learning-mask{position:fixed;inset:0;z-index:150;display:flex;align-items:center;justify-content:center;background:#0f172a66;padding:20px}.learning-dialog{width:100%;max-width:600px;max-height:88vh;overflow:auto;background:white;border-radius:12px;padding:24px;outline:none;box-shadow:0 12px 40px #0f172a26;color: #334155;font-size: 13px}.learning-dialog header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.learning-dialog h2{font-size:17px;margin:0}.learning-dialog h3{font-size:14px;margin:22px 0 12px}.learning-dialog button{border:1px solid #e4e9f3;background:white;border-radius:6px;padding:6px 12px;color: #64748b;cursor:pointer}.learning-dialog table{width:100%;border-collapse:collapse}.learning-dialog :is(th,td){text-align:left;padding:9px 12px;border:1px solid #e4e9f3}.learning-dialog th{background:#f8fafc}.learning-dialog p{line-height:1.8;margin:12px 0;color: #64748b}.learning-dialog dl{display:grid;grid-template-columns:75px 1fr;gap:12px;line-height:1.7}.learning-dialog dt{font-weight:600}.learning-dialog dd{margin:0;color: #64748b}
</style>
