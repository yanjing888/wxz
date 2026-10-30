<template>
  <nav class="list-pagination" :class="{ compact }" aria-label="列表分页">
    <span>共 {{ total }} 条</span>
    <select :value="pageSize" aria-label="每页条数" :disabled="disabled" @change="$emit('update:pageSize', Number($event.target.value))">
      <option v-for="size in [10, 20, 50]" :key="size" :value="size">{{ size }} 条/页</option>
    </select>
    <button type="button" :disabled="disabled || page <= 1" @click="$emit('update:page', page - 1)">上一页</button>
    <span class="page-position">{{ page }} / {{ pages }}</span>
    <button type="button" :disabled="disabled || page >= pages" @click="$emit('update:page', page + 1)">下一页</button>
    <label v-if="!compact">跳至 <input type="number" min="1" :max="pages" :value="page" :disabled="disabled" aria-label="跳转页码" @change="jump" @keydown.enter="$event.target.blur()" /> 页</label>
  </nav>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ page: { type: Number, default: 1 }, pageSize: { type: Number, default: 10 }, total: { type: Number, default: 0 }, disabled: Boolean, compact: Boolean })
const emit = defineEmits(['update:page', 'update:pageSize'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
function jump(event) {
  const value = Math.min(pages.value, Math.max(1, Math.trunc(Number(event.target.value)) || 1))
  emit('update:page', value); event.target.value = value
}
</script>
<style scoped>
.list-pagination{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-wrap:wrap;flex-shrink:0;padding:12px 16px;background:#fff;color:#64748b;font-size:12px;line-height:1.5;border-top:1px solid #e2e8f0}
.list-pagination button,.list-pagination select,.list-pagination input{font:inherit;color:#475569;border:1px solid #dbe2ec;border-radius:5px;background:#fff;padding:5px 8px;min-height:30px}
.list-pagination button:not(:disabled):hover{color:#4f46e5;border-color:#a5b4fc;background:#f5f3ff}.list-pagination button:disabled{opacity:.4;cursor:not-allowed}.list-pagination input{width:48px;text-align:center}.list-pagination label{display:flex;align-items:center;gap:5px}.page-position{font-variant-numeric:tabular-nums;color:#334155}.list-pagination :focus-visible{outline:2px solid #818cf8;outline-offset:2px}
@media(max-width:600px){.list-pagination{justify-content:center;gap:7px;padding:10px}.list-pagination label{display:none}}
.list-pagination.compact{display:grid;grid-template-columns:1fr auto 1fr;gap:8px;padding:12px}.compact>span:first-child{grid-column:1/3}.compact>select{grid-column:3;max-width:100%;padding-left:4px;padding-right:4px}.compact>button{padding:5px 6px}.compact>.page-position{text-align:center}
</style>
