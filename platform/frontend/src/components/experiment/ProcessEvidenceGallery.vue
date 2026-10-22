<template>
  <div v-if="pictures.length" class="evidence-gallery">
    <div class="image-grid" :class="{ compact }">
      <div v-for="(picture, index) in pictures" :key="picture.url" class="image-item">
        <button type="button" class="image-open" :aria-label="`查看${label} ${index + 1}`" @click="open(index)">
          <img v-if="!failed[picture.url]" :src="mediaUrl(picture.url)" :alt="picture.name || `${label} ${index + 1}`" loading="lazy" @error="failed[picture.url] = true" />
          <span v-else class="image-missing">图片文件不存在</span>
        </button>
        <div class="image-caption"><span>{{ picture.name || `${label} ${index + 1}` }}</span><button v-if="removable" type="button" :disabled="disabled" :aria-label="`移除图片 ${index + 1}`" @click="$emit('remove', index)">移除</button></div>
      </div>
    </div>
    <Teleport to="body">
      <div v-if="preview >= 0" class="image-overlay" @click.self="close" @keydown.esc="close">
        <section ref="dialog" tabindex="-1" role="dialog" aria-modal="true" :aria-label="label" class="image-dialog" @keydown.tab="trapFocus">
          <header><span>{{ label }} {{ preview + 1 }} / {{ pictures.length }}</span><button ref="closeButton" type="button" aria-label="关闭图片预览" @click="close">关闭</button></header>
          <div class="image-stage"><img v-if="!failed[current?.url]" :src="mediaUrl(current?.url)" :alt="current?.name || label" @error="failed[current.url] = true" /><div v-else class="preview-missing">图片文件不存在或已失效</div></div>
          <footer><button type="button" :disabled="preview <= 0" @click="preview--">上一张</button><span>{{ current?.name || '' }}</span><button type="button" :disabled="preview >= pictures.length - 1" @click="preview++">下一张</button></footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>
<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { mediaUrl } from '../../api/runtime'
import { processImages } from '../../utils/processAssessment'
const props = defineProps({ images: Array, compact: Boolean, removable: Boolean, disabled: Boolean, label: { type: String, default: '检查图片' } })
defineEmits(['remove'])
const pictures = computed(() => processImages(props.images))
const failed = ref({}), preview = ref(-1), closeButton = ref(null), dialog = ref(null)
const current = computed(() => pictures.value[preview.value])
let previousFocus = null
async function open(index) { previousFocus = document.activeElement; preview.value = index; await nextTick(); closeButton.value?.focus() }
function close() { preview.value = -1; previousFocus?.focus?.() }
function trapFocus(event) {
  const controls = [...dialog.value.querySelectorAll('button:not(:disabled)')]
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(() => pictures.value.map(picture => picture.url).join('|'), () => { preview.value = -1; failed.value = {} })
onUnmounted(() => { preview.value = -1 })
</script>
<style scoped>
.image-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}.image-grid.compact{grid-template-columns:repeat(auto-fill,minmax(90px,1fr));gap:8px}.image-item{min-width:0}.image-open{display:flex;align-items:center;justify-content:center;width:100%;height:156px;padding:0;border:1px solid #e4e9f3;border-radius:6px;overflow:hidden;background:#f8f9fc;cursor:zoom-in}.compact .image-open{height:90px}.image-open img{width:100%;height:100%;object-fit:contain}.image-missing{font-size:12px;color:#94a3b8}.image-caption{display:flex;gap:8px;align-items:center;margin-top:6px;font-size:12px;color:#64748b}.image-caption span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}.image-caption button{color:#dc2626;white-space:nowrap}.image-caption button:disabled{opacity:.4}.image-overlay{position:fixed;inset:0;z-index:100;display:flex;align-items:center;justify-content:center;background:rgba(15,23,42,.76);padding:28px}.image-dialog{display:flex;flex-direction:column;width:min(1100px,100%);max-height:92vh;background:white;border-radius:8px;overflow:hidden;outline:none}.image-dialog header,.image-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 16px;font-size:13px;color:#334155}.image-dialog button{padding:6px 12px;border:1px solid #e4e9f3;border-radius:6px;background:white;white-space:nowrap}.image-dialog button:disabled{opacity:.35}.image-stage{display:flex;align-items:center;justify-content:center;min-height:160px;background:#0f172a;overflow:hidden}.image-stage img{max-width:100%;max-height:72vh;object-fit:contain}.preview-missing{color:#cbd5e1;padding:80px 20px;font-size:14px}.image-dialog footer span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}@media(max-width:600px){.image-overlay{padding:10px}.image-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
