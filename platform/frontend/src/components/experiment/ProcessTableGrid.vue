<template>
  <div class="table-scroll">
    <table class="evidence-table"><tbody>
      <tr v-for="(row, r) in modelValue" :key="r">
        <component :is="r === 0 ? 'th' : 'td'" v-for="(cell, c) in row" :key="c" :scope="r === 0 ? 'col' : undefined">
          <textarea v-if="editable" :value="cell" rows="1" maxlength="2000" :disabled="disabled" :aria-label="`第 ${r + 1} 行第 ${c + 1} 列`" :placeholder="r === 0 ? '表头 / 单位' : ''" @input="update(r, c, $event.target.value)" />
          <span v-else>{{ cell || '—' }}</span>
        </component>
      </tr>
    </tbody></table>
  </div>
</template>
<script setup>
const props = defineProps({ modelValue: { type: Array, default: () => [] }, editable: Boolean, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
function update(r, c, value) { emit('update:modelValue', props.modelValue.map((row, i) => i === r ? row.map((cell, j) => j === c ? value : cell) : [...row])) }
</script>
<style scoped>
.table-scroll{overflow:auto;max-height:320px;border:1px solid #e2e8f0;border-radius:5px}.evidence-table{width:100%;border-collapse:separate;border-spacing:0;font-size:12px;table-layout:fixed}.evidence-table th,.evidence-table td{min-width:110px;width:130px;border-right:1px solid #e2e8f0;border-bottom:1px solid #e2e8f0;vertical-align:top;text-align:left}.evidence-table th:last-child,.evidence-table td:last-child{border-right:0}.evidence-table tr:last-child>*{border-bottom:0}.evidence-table th{background:#f8fafc;font-weight:500;position:sticky;top:0;z-index:1}.evidence-table textarea{display:block;width:100%;min-height:40px;resize:vertical;padding:9px 10px;background:transparent;color:#334155;line-height:1.6;font:inherit;border:0;border-radius:0}.evidence-table textarea:focus{outline:2px solid #a5b4fc;outline-offset:-2px;background:#fff}.evidence-table textarea:disabled{color:#64748b}.evidence-table span{display:block;padding:9px 10px;white-space:pre-wrap;overflow-wrap:anywhere;color:#475569;line-height:1.6}
</style>
