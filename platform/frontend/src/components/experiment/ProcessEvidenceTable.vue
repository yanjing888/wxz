<template>
  <section class="table-evidence">
    <div class="table-actions">
      <div class="insert-wrap" v-if="editable && !modelValue.length">
        <button type="button" class="table-button" :disabled="disabled" :aria-expanded="picker" @click="picker = !picker">▦ 插入表格</button>
        <div v-if="picker" class="table-picker" @keydown.esc="picker = false">
          <div class="picker-size">{{ columns }} 列 × {{ rows }} 行</div>
          <div class="picker-grid"><button v-for="n in 56" :key="n" type="button" :class="{ selected: selectedCell(n) }" :aria-label="`${(n - 1) % 8 + 1} 列 ${(Math.ceil(n / 8) + 1)} 行`" @mouseenter="hoverCell(n)" @focus="hoverCell(n)" @click="insert((Math.ceil(n / 8) + 1), (n - 1) % 8 + 1)" /></div>
          <div class="picker-fields"><label>行数（含表头）<input v-model.number="rows" type="number" min="2" max="50" /></label><label>列数<input v-model.number="columns" type="number" min="1" max="20" /></label></div>
          <button type="button" class="table-button" :disabled="!validSize" @click="insert(rows, columns)">插入</button>
        </div>
      </div>
      <template v-if="modelValue.length"><span class="table-size">{{ modelValue.length }} 行 × {{ modelValue[0]?.length || 0 }} 列</span><button ref="expandButton" type="button" class="table-button" @click="open">{{ editable ? '放大填写' : '放大查看' }}</button><button v-if="editable" type="button" class="table-link" :disabled="disabled" @click="emit('update:modelValue', [])">删除表格</button></template>
    </div>
    <ProcessTableGrid v-if="modelValue.length" :model-value="modelValue" :editable="editable" :disabled="disabled" @update:model-value="emit('update:modelValue', $event)" />
    <div v-if="editable && modelValue.length" class="table-footer"><button type="button" :disabled="disabled || modelValue.length >= 50" @click="addRow">＋ 行</button><button type="button" :disabled="disabled || modelValue[0].length >= 20" @click="addColumn">＋ 列</button></div>
    <Teleport to="body"><div v-if="expanded" class="table-overlay" @click.self="close"><section ref="dialog" class="table-dialog" role="dialog" aria-modal="true" aria-label="实验数据表格" tabindex="-1" @keydown="dialogKey">
      <header><strong>实验数据</strong><button type="button" class="table-button" @click="close">{{ editable ? '完成填写' : '关闭' }}</button></header>
      <ProcessTableGrid :model-value="modelValue" :editable="editable" :disabled="disabled" @update:model-value="emit('update:modelValue', $event)" />
      <div v-if="editable" class="table-footer"><button type="button" :disabled="disabled || modelValue.length >= 50" @click="addRow">＋ 行</button><button type="button" :disabled="disabled || modelValue[0]?.length >= 20" @click="addColumn">＋ 列</button></div>
    </section></div></Teleport>
  </section>
</template>
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import ProcessTableGrid from './ProcessTableGrid.vue'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, editable: Boolean, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const picker = ref(false), expanded = ref(false), rows = ref(3), columns = ref(3), dialog = ref(null), expandButton = ref(null)
const validSize = computed(() => Number.isInteger(rows.value) && rows.value >= 2 && rows.value <= 50 && Number.isInteger(columns.value) && columns.value >= 1 && columns.value <= 20)
function hoverCell(n) { rows.value = (Math.ceil(n / 8) + 1); columns.value = (n - 1) % 8 + 1 }
function selectedCell(n) { return Math.ceil(n / 8) + 1 <= rows.value && (n - 1) % 8 < columns.value }
function insert(r, c) { if (props.disabled || !Number.isInteger(r) || !Number.isInteger(c) || r < 2 || r > 50 || c < 1 || c > 20) return; emit('update:modelValue', Array.from({ length: r }, () => Array(c).fill(''))); picker.value = false }
function addRow() { if (!props.disabled && props.modelValue.length < 50) emit('update:modelValue', [...props.modelValue.map(row => [...row]), Array(props.modelValue[0].length).fill('')]) }
function addColumn() { if (!props.disabled && props.modelValue[0].length < 20) emit('update:modelValue', props.modelValue.map(row => [...row, ''])) }
async function open() { expanded.value = true; await nextTick(); dialog.value?.focus() }
async function close() { expanded.value = false; await nextTick(); expandButton.value?.focus() }
function dialogKey(event) { if (event.key === 'Escape') { event.preventDefault(); close() } else if (event.key === 'Tab') { const nodes = [...dialog.value.querySelectorAll('button:not(:disabled), textarea:not(:disabled)')]; const first = nodes[0], last = nodes.at(-1); if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) { event.preventDefault(); last?.focus() } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() } } }
watch(() => props.modelValue.length, length => { if (!length) expanded.value = false })
</script>
<style scoped>
.table-evidence{margin:8px 0 12px;min-width:0}.table-actions{display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap}.table-button{padding:5px 10px;border:1px solid #e2e8f0;border-radius:5px;background:#fff;color:#475569;font-size:12px;line-height:1.6}.table-button:hover:not(:disabled){background:#f8fafc}.table-link,.table-footer button{font-size:12px;color:#64748b;padding:4px 8px}.table-link{margin-left:auto}.table-size{font-size:11px;color:#94a3b8}.table-footer{display:flex;gap:8px;margin-top:5px}button:disabled{opacity:.45;cursor:default}.insert-wrap{position:relative}.table-picker{position:absolute;top:36px;left:0;z-index:30;width:252px;background:#fff;border:1px solid #e2e8f0;border-radius:6px;box-shadow:0 4px 16px #1e293b14;padding:12px}.picker-size{font-size:12px;color:#64748b;margin-bottom:8px}.picker-grid{display:grid;grid-template-columns:repeat(8,1fr);gap:3px}.picker-grid button{height:22px;border:1px solid #e2e8f0;background:#fff}.picker-grid button.selected{background:#eef2ff;border-color:#a5b4fc}.picker-fields{display:flex;gap:10px;margin:10px 0}.picker-fields label{font-size:11px;color:#64748b;flex:1}.picker-fields input{display:block;margin-top:4px;width:100%;border:1px solid #e2e8f0;border-radius:4px;padding:5px;color:#334155}.table-overlay{position:fixed;inset:0;z-index:2100;background:#0f172a66;display:flex;align-items:center;justify-content:center;padding:24px}.table-dialog{width:min(1200px,96vw);height:min(820px,90dvh);background:#fff;border-radius:8px;display:flex;flex-direction:column;padding:20px;min-height:0;outline:none}.table-dialog header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;color:#334155;font-size:14px}.table-dialog :deep(.table-scroll){flex:1;max-height:none}.table-dialog :deep(textarea){min-height:50px;font-size:15px}.table-dialog :deep(.evidence-table){font-size:15px}.table-dialog .table-footer{margin-top:12px}@media(max-width:600px){.table-overlay{padding:8px}.table-dialog{width:100%;height:95dvh;padding:12px}}
</style>
