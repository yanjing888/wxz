<template>
  <section class="experiment-manager">
    <template v-if="!detail">
      <header class="manager-head">
        <div><h2>实验管理</h2><p>管理实验内容、步骤教程和采集要求</p></div>
        <div class="button-group"><button class="secondary" :disabled="loading" @click="load">刷新</button><button class="primary" :disabled="loading" @click="create">新增实验</button></div>
      </header>
      <div class="filters">
        <input v-model.trim="search" type="search" placeholder="搜索实验名称" aria-label="搜索实验名称" />
        <select v-model="category" aria-label="实验分类"><option value="">全部分类</option><option v-for="name in categories" :key="name" :value="name">{{ categoryLabel(name) }}</option></select>
        <span>共 {{ total }} 个实验</span>
      </div>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <p v-if="message" class="alert success" role="status">{{ message }}</p>
      <div class="list-scroll"><table>
        <thead><tr><th>实验名称</th><th>分类</th><th>步骤数</th><th>示教资源</th><th>采集要求</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in pageItems" :key="item.code">
          <td><strong>{{ item.name }}</strong><p class="description">{{ item.description || '—' }}</p></td>
          <td>{{ categoryLabel(item.category) }}</td><td>{{ Object.keys(item.steps || {}).length }} 步</td>
          <td>{{ mediaCount(item, 'images') }} 张图片 · {{ mediaCount(item, 'video') }} 段视频</td>
          <td><span class="tag">图片必填 {{ count(item, 'imageRequired') }} 步</span><span class="tag">表格必填 {{ count(item, 'tableRequired') }} 步</span></td>
          <td class="actions"><button :disabled="loading" @click="open(item.code, false)">查看详情</button><button :disabled="loading" @click="open(item.code, true)">编辑</button></td>
        </tr><tr v-if="!pageItems.length"><td colspan="6" class="empty">{{ loading ? '正在加载实验…' : '暂无符合条件的实验' }}</td></tr></tbody>
      </table></div>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </template>

    <template v-else>
      <header class="manager-head">
        <div class="heading-row"><button class="back-button" :disabled="busy" @click="back">‹ 返回列表</button><span class="heading-divider" /><h2>{{ creating ? '新增实验' : editing ? '编辑实验' : detail.config.name }}</h2><span v-if="editing && !creating" class="tag">{{ detail.config.name }}</span></div>
        <button v-if="!editing" class="secondary" @click="editing = true; message = ''">编辑实验</button>
      </header>
      <nav class="main-tabs" aria-label="实验配置">
        <button :class="{ selected: activeSection === 'base' }" @click="activeSection = 'base'">基本信息与内容</button>
        <button :class="{ selected: activeSection === 'steps' }" @click="activeSection = 'steps'">实验步骤 <span>{{ steps.length }}</span></button>
      </nav>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p><p v-if="message" class="alert success" role="status">{{ message }}</p>

      <div v-if="activeSection === 'base'" class="base-scroll">
        <div class="form-content">
          <section class="form-section">
            <h3>基本信息</h3>
            <div class="form-row"><label for="experiment-name"><span v-if="editing" class="required-mark">*</span>实验名称</label><div class="field"><input v-if="editing" id="experiment-name" v-model.trim="draft.name" maxlength="128" :disabled="busy" placeholder="请输入实验名称" /><p v-else>{{ draft.name }}</p></div></div>
            <div class="form-row"><label for="experiment-category">实验分类</label><div class="field short-field"><select v-if="editing" id="experiment-category" v-model="draft.category" :disabled="busy"><option value="">未分类</option><option v-for="name in categoryOptions" :key="name" :value="name">{{ categoryLabel(name) }}</option></select><p v-else>{{ categoryLabel(draft.category) }}</p></div></div>
            <div class="form-row"><label for="experiment-description">实验简介</label><div class="field"><textarea v-if="editing" id="experiment-description" v-model="draft.description" rows="3" maxlength="20000" :disabled="busy" placeholder="简要说明本实验的内容与教学目标" /><p v-else class="pre-wrap">{{ draft.description || '暂无简介' }}</p></div></div>
          </section>
          <section class="form-section content-section">
            <div class="section-heading"><div><h3>实验内容</h3><p>实验目的、原理、仪器与教学要求</p></div><div v-if="editing" class="segmented" aria-label="实验内容编辑方式"><button :class="{ active: !contentPreview }" @click="contentPreview = false">编辑正文</button><button :class="{ active: contentPreview }" @click="contentPreview = true">预览</button></div></div>
            <textarea v-if="editing && !contentPreview" id="experiment-content" v-model="draft.teachingContent" class="content-input" rows="18" maxlength="100000" :disabled="busy" aria-label="实验内容正文" placeholder="在这里填写实验目的、实验原理、仪器与教学要求…" />
            <div v-else-if="draft.teachingContent" class="document-content" v-html="renderChatMarkdown(draft.teachingContent, { normalize: false })" />
            <div v-else class="empty content-empty">暂无实验内容</div>
          </section>
        </div>
      </div>

      <div v-else class="steps-layout">
        <aside class="step-sidebar">
          <header><h3>步骤列表</h3><span>{{ steps.length }} 步</span></header>
          <ol><li v-for="[id, item] in steps" :key="id"><button :class="{ selected: selected === id }" @click="selected = id"><span class="step-number">{{ id }}</span><span>{{ item.title || '未命名步骤' }}</span></button></li></ol>
          <button v-if="editing" class="add-step" :disabled="busy || steps.length >= 30" @click="addStep">＋ 新增步骤</button>
        </aside>
        <div v-if="step" class="step-main">
          <header class="step-heading"><div><h3>步骤 {{ selected }}<span>{{ step.title }}</span></h3></div><button v-if="editing && canRemoveStep" class="danger-link" :disabled="busy" @click="removeStep">删除此步骤</button></header>
          <nav class="step-tabs" aria-label="步骤编辑分组"><button v-for="tab in stepTabs" :key="tab.key" :class="{ active: stepTab === tab.key }" @click="stepTab = tab.key">{{ tab.label }}</button></nav>
          <div class="step-scroll">
            <div class="step-form">
              <template v-if="stepTab === 'instructions'">
                <section class="form-section"><h3>操作说明</h3><div class="form-row"><label for="step-title"><span v-if="editing" class="required-mark">*</span>步骤名称</label><div class="field"><input v-if="editing" id="step-title" v-model.trim="step.title" maxlength="128" :disabled="busy" placeholder="请输入步骤名称" /><p v-else>{{ step.title }}</p></div></div><div class="form-row"><label for="step-description">步骤说明</label><div class="field"><textarea v-if="editing" id="step-description" v-model="step.desc" rows="3" maxlength="10000" :disabled="busy" placeholder="说明本步骤的操作目标与要求" /><p v-else class="pre-wrap">{{ step.desc || '暂无说明' }}</p></div></div></section>
                <section class="form-section"><div class="section-heading"><h3>操作指引</h3><button v-if="editing" class="text-button" :disabled="busy || step.tut.steps.length >= 100" @click="step.tut.steps.push('')">＋ 添加操作</button></div><div v-if="editing" class="instruction-list"><div v-for="(text, index) in step.tut.steps" :key="index" class="instruction-row"><span>{{ index + 1 }}</span><textarea v-model="step.tut.steps[index]" :aria-label="`操作指引 ${index + 1}`" rows="2" maxlength="2000" :disabled="busy" placeholder="填写这一步操作的具体做法" /><button class="text-button" :disabled="busy" @click="step.tut.steps.splice(index, 1)">删除</button></div><p v-if="!step.tut.steps.length" class="empty-inline">点击“添加操作”填写具体指引</p></div><ol v-else-if="step.tut.steps.length" class="read-list"><li v-for="(text, i) in step.tut.steps" :key="i">{{ text }}</li></ol><p v-else class="empty-inline">暂无操作指引</p></section>
                <section class="form-section"><div class="section-heading"><h3>注意事项</h3><button v-if="editing" class="text-button" :disabled="busy || step.tut.warnings.length >= 100" @click="step.tut.warnings.push('')">＋ 添加事项</button></div><div v-if="editing" class="instruction-list"><div v-for="(text, index) in step.tut.warnings" :key="index" class="instruction-row"><span>{{ index + 1 }}</span><textarea v-model="step.tut.warnings[index]" :aria-label="`注意事项 ${index + 1}`" rows="2" maxlength="2000" :disabled="busy" placeholder="填写安全要求或易错点" /><button class="text-button" :disabled="busy" @click="step.tut.warnings.splice(index, 1)">删除</button></div><p v-if="!step.tut.warnings.length" class="empty-inline">暂无注意事项</p></div><ul v-else-if="step.tut.warnings.length" class="read-list bullet-list"><li v-for="(text, i) in step.tut.warnings" :key="i">{{ text }}</li></ul><p v-else class="empty-inline">暂无注意事项</p></section>
              </template>

              <template v-else-if="stepTab === 'media'">
                <section class="form-section"><div class="section-heading"><div><h3>示教图片</h3><p>展示仪器摆放、操作方法或预期现象</p></div><label v-if="editing" class="secondary upload-button">上传图片<input type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/bmp" :disabled="busy" @change="upload($event, 'image')" /></label></div><div v-if="step.tut.images.length" class="media-grid"><figure v-for="(url, index) in step.tut.images" :key="index"><img :src="mediaUrl(url)" :alt="`步骤 ${selected} 示教图片 ${index + 1}`" /><figcaption><span>示教图片 {{ index + 1 }}</span><button v-if="editing" class="text-button" :disabled="busy" @click="step.tut.images.splice(index, 1)">删除</button></figcaption></figure></div><div v-else class="media-empty">暂无示教图片</div><details v-if="editing" class="link-input"><summary>使用图片链接</summary><div class="inline-input"><input v-model.trim="imageUrl" aria-label="示教图片地址" placeholder="粘贴图片地址" :disabled="busy" /><button class="secondary" :disabled="busy || !imageUrl" @click="addImage">添加图片</button></div></details><p v-if="editing" class="help-text">最多 20 张，每张不超过 20MB</p></section>
                <section class="form-section"><div class="section-heading"><div><h3>示教视频</h3><p>本步骤的操作演示视频</p></div><div v-if="editing" class="button-group"><button v-if="step.tut.videoUrl" class="text-button" :disabled="busy" @click="step.tut.videoUrl = ''">删除视频</button><label class="secondary upload-button">{{ step.tut.videoUrl ? '替换视频' : '上传视频' }}<input type="file" accept="video/mp4,video/webm" :disabled="busy" @change="upload($event, 'video')" /></label></div></div><video v-if="safeUrl(step.tut.videoUrl)" :key="step.tut.videoUrl" :src="mediaUrl(step.tut.videoUrl)" controls preload="metadata" /><div v-else class="media-empty">暂无示教视频</div><details v-if="editing" class="link-input"><summary>使用视频链接</summary><input v-model.trim="step.tut.videoUrl" aria-label="示教视频地址" placeholder="粘贴 MP4 或 WebM 视频地址" :disabled="busy" /></details><p v-if="editing" class="help-text">支持 MP4、WebM，文件不超过 20MB</p></section>
              </template>

              <template v-else>
                <section class="form-section"><h3>材料采集</h3><p class="section-description">配置学生提交本步骤时必须提供的材料</p><div v-for="rule in collectionRules" :key="rule.key" class="rule-row"><div><label :for="rule.key">{{ rule.label }}</label><p>{{ rule.hint }}</p></div><select v-if="editing" :id="rule.key" v-model="step[rule.key]" :disabled="busy"><option :value="true">必填</option><option :value="false">选填</option></select><span v-else class="tag" :class="{ required: step[rule.key] }">{{ step[rule.key] ? '必填' : '选填' }}</span></div></section>
                <section class="form-section"><h3>教师检查</h3><p class="section-description">设置教师是否需要评价本步骤，以及学生何时可以继续</p><div class="rule-row"><div><label for="assessment-required">教师评价</label><p>开启后，本步骤计入过程考核</p></div><select v-if="editing" id="assessment-required" v-model="step.assessmentRequired" :disabled="busy"><option :value="true">需要</option><option :value="false">不需要</option></select><span v-else class="tag">{{ step.assessmentRequired ? '需要' : '不需要' }}</span></div><div class="rule-row"><div><label for="assessment-blocking">关键检查点</label><p>开启后，须经教师确认通过，学生才能进入下一步</p></div><select v-if="editing" id="assessment-blocking" v-model="step.assessmentBlocking" :disabled="busy || !step.assessmentRequired"><option :value="true">是</option><option :value="false">否</option></select><span v-else class="tag">{{ step.assessmentBlocking ? '是' : '否' }}</span></div></section>
              </template>
            </div>
          </div>
        </div>
      </div>
      <footer v-if="editing" class="editor-footer"><span>{{ uploading ? '正在上传资源…' : dirty ? '有未保存的修改' : '修改完成后请保存' }}</span><div class="button-group"><button class="secondary" :disabled="busy" @click="cancel">取消</button><button v-if="creating && activeSection === 'base'" class="secondary" :disabled="busy" @click="activeSection = 'steps'">配置实验步骤</button><button class="primary" :disabled="busy" @click="save">{{ saving ? '保存中…' : creating ? '创建实验' : '保存修改' }}</button></div></footer>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { experimentManagementApi } from '../../api'
import { mediaUrl } from '../../api/runtime'
import { renderChatMarkdown } from '../../utils/markdown'
import ListPagination from '../common/ListPagination.vue'
import { usePagination } from '../../composables/usePagination'

const emit = defineEmits(['saved'])
const items = ref([]), loading = ref(false), error = ref(''), message = ref(''), search = ref(''), category = ref('')
const detail = ref(null), draft = ref(null), editing = ref(false), creating = ref(false)
const activeSection = ref('base'), selected = ref('1'), stepTab = ref('instructions'), contentPreview = ref(false)
const saving = ref(false), uploading = ref(false), imageUrl = ref(''), persistedStepCount = ref(0)
const busy = computed(() => saving.value || uploading.value)
const categories = computed(() => [...new Set(items.value.map(item => item.category).filter(Boolean))])
const categoryOptions = computed(() => [...new Set(['optics', 'mechanics', 'electricity', 'thermodynamics', ...categories.value])])
const filtered = computed(() => items.value.filter(item => (!category.value || item.category === category.value) && item.name.toLowerCase().includes(search.value.toLowerCase())))
const { page, pageSize, total, items: pageItems } = usePagination(filtered, [search, category])
const steps = computed(() => Object.entries(draft.value?.steps || {}).sort((a, b) => Number(a[0]) - Number(b[0])))
const step = computed(() => draft.value?.steps?.[selected.value])
const canRemoveStep = computed(() => steps.value.length > 1 && Number(selected.value) > persistedStepCount.value)
const stepTabs = [{ key: 'instructions', label: '操作说明' }, { key: 'media', label: '示教资源' }, { key: 'requirements', label: '采集与检查' }]
const collectionRules = [{ key: 'imageRequired', label: '检查图片', hint: '必填时，至少上传或拍摄一张检查图片' }, { key: 'tableRequired', label: '表格数据', hint: '必填时，需填写表头及至少一组实验数据' }]
let snapshot = '', alive = true, request = 0
const dirty = computed(() => editing.value && JSON.stringify(draft.value) !== snapshot)

function categoryLabel(value) { return ({ optics: '光学', mechanics: '力学', electricity: '电学', thermodynamics: '热学' })[value] || value || '未分类' }
function count(item, field) { return Object.values(item.steps || {}).filter(s => field === 'imageRequired' ? s[field] !== false : s[field] === true).length }
function mediaCount(item, kind) { return Object.values(item.steps || {}).reduce((n, s) => n + (kind === 'images' ? s.tut?.images?.length || 0 : s.tut?.videoUrl ? 1 : 0), 0) }
function safeUrl(url) { return typeof url === 'string' && /^(https?:\/\/[^\s]+|\/(?!\/)[^\s]+)$/.test(url) }
function newStep(number) { return { title: `步骤 ${number}`, desc: '', imageRequired: true, tableRequired: false, assessmentRequired: true, assessmentBlocking: false, tut: { steps: [''], warnings: [], images: [], videoUrl: '' } } }
function normalize(config, guide = '') {
  const result = JSON.parse(JSON.stringify(config))
  result.category ||= ''; result.description ||= ''; result.teachingContent = result.teachingContent ?? guide
  for (const s of Object.values(result.steps || {})) {
    s.imageRequired = s.imageRequired !== false; s.tableRequired = s.tableRequired === true
    s.assessmentRequired = s.assessmentRequired !== false; s.assessmentBlocking = s.assessmentBlocking === true
    s.tut = { steps: [], warnings: [], images: [], videoUrl: '', ...s.tut }
    for (const field of ['steps', 'warnings', 'images']) s.tut[field] ||= []
    s.tut.videoUrl ||= ''
  }
  return result
}
function apply(data) { detail.value = data; draft.value = normalize(data.config, data.teachingGuide); snapshot = JSON.stringify(draft.value); persistedStepCount.value = Object.keys(data.config.steps || {}).length }
async function load() {
  loading.value = true; error.value = ''
  try { const { data } = await experimentManagementApi.list(); if (alive) items.value = data || [] }
  catch (e) { if (alive) error.value = e.response?.data?.message || '实验列表加载失败，请重试' }
  finally { if (alive) loading.value = false }
}
async function open(code, edit) {
  const seq = ++request; loading.value = true; error.value = ''
  try { const { data } = await experimentManagementApi.get(code); if (!alive || seq !== request) return; apply(data); creating.value = false; editing.value = edit; activeSection.value = 'base'; selected.value = '1'; stepTab.value = 'instructions'; contentPreview.value = false; message.value = '' }
  catch (e) { if (alive) error.value = e.response?.data?.message || '实验详情加载失败，请重试' }
  finally { if (alive) loading.value = false }
}
function create() {
  apply({ config: { name: '', category: 'optics', description: '', teachingContent: '', steps: { '1': newStep(1) } }, version: -1 })
  creating.value = true; editing.value = true; persistedStepCount.value = 0
  activeSection.value = 'base'; selected.value = '1'; stepTab.value = 'instructions'; contentPreview.value = false; message.value = ''; error.value = ''
}
function back() {
  if (busy.value || (dirty.value && !window.confirm('尚有未保存的修改，确定返回列表吗？'))) return
  detail.value = null; draft.value = null; editing.value = false; creating.value = false; error.value = ''; message.value = ''
}
function cancel() {
  if (dirty.value && !window.confirm('放弃本次未保存的修改？')) return
  if (creating.value) { detail.value = null; draft.value = null; creating.value = false }
  else { draft.value = JSON.parse(snapshot); if (!draft.value.steps[selected.value]) selected.value = '1' }
  editing.value = false; error.value = ''
}
function addStep() { if (busy.value || steps.value.length >= 30) return; const id = String(steps.value.length + 1); draft.value.steps[id] = newStep(id); selected.value = id; stepTab.value = 'instructions' }
function removeStep() {
  if (!canRemoveStep.value || busy.value || !window.confirm('确定删除此步骤吗？')) return
  const kept = steps.value.filter(([id]) => id !== selected.value).map(([, value]) => value)
  draft.value.steps = Object.fromEntries(kept.map((value, index) => [String(index + 1), value]))
  selected.value = String(Math.min(Number(selected.value), kept.length))
}
function validate() {
  if (!draft.value.name?.trim()) { activeSection.value = 'base'; return '请填写实验名称' }
  for (const [id, s] of steps.value) if (!s.title?.trim()) { activeSection.value = 'steps'; selected.value = id; stepTab.value = 'instructions'; return `请填写步骤 ${id} 的名称` }
  return ''
}
async function save() {
  if (busy.value) return
  error.value = validate(); if (error.value) return
  saving.value = true; message.value = ''
  const wasCreating = creating.value
  const config = JSON.parse(JSON.stringify(draft.value))
  for (const s of Object.values(config.steps)) for (const field of ['steps', 'warnings']) s.tut[field] = s.tut[field].map(text => text.trim()).filter(Boolean)
  try {
    const payload = { config, version: detail.value.version }
    const { data } = wasCreating ? await experimentManagementApi.create(payload) : await experimentManagementApi.save(detail.value.config.code, payload)
    if (!alive) return
    apply(data); creating.value = false; editing.value = false
    message.value = wasCreating ? '实验已创建，可在学生管理中分配给学生。' : '修改已保存，学生重新进入实验后读取新内容。'
    const index = items.value.findIndex(item => item.code === data.config.code)
    if (index >= 0) items.value[index] = data.config; else items.value.push(data.config)
    emit('saved', data.config)
  } catch (e) { if (alive) error.value = e.response?.data?.message || '保存失败，修改已保留，请重试' }
  finally { if (alive) saving.value = false }
}
function addImage() { if (!safeUrl(imageUrl.value)) { error.value = '请输入有效的图片地址'; return } if (step.value.tut.images.length >= 20) { error.value = '每步最多20张图片'; return } step.value.tut.images.push(imageUrl.value); imageUrl.value = ''; error.value = '' }
async function upload(event, kind) {
  const file = event.target.files?.[0]; event.target.value = ''
  if (!file || busy.value) return
  if (file.size > 20 * 1024 * 1024) { error.value = '单个文件不能超过20MB'; return }
  if (kind === 'image' ? !file.type.startsWith('image/') : !['video/mp4', 'video/webm'].includes(file.type)) { error.value = kind === 'image' ? '请选择图片文件' : '请选择 MP4 或 WebM 视频'; return }
  const target = step.value
  if (kind === 'image' && target.tut.images.length >= 20) { error.value = '每步最多20张图片'; return }
  uploading.value = true; error.value = ''
  try { const { data } = await experimentManagementApi.upload(file); if (!alive) return; if (kind === 'image') target.tut.images.push(data.url); else target.tut.videoUrl = data.url }
  catch (e) { if (alive) error.value = e.response?.data?.message || '上传失败，请重试' }
  finally { if (alive) uploading.value = false }
}
watch(() => step.value?.assessmentRequired, value => { if (editing.value && value === false && step.value) step.value.assessmentBlocking = false })
watch(selected, () => { imageUrl.value = '' })
function beforeUnload(event) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { alive = false; request++; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<style scoped>
.experiment-manager{display:flex;flex:1;min-height:0;flex-direction:column;margin:16px 22px 20px;border:1px solid #e4e9f3;border-radius:10px;background:#fff;overflow:hidden;font-size:13px;color:#475569}
.manager-head{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:68px;padding:16px 20px;border-bottom:1px solid #edf0f5;flex-shrink:0}.manager-head h2{font-size:16px;font-weight:650;color:#1e293b;line-height:1.6}.manager-head p{font-size:12px;color:#64748b;margin-top:4px}.button-group,.heading-row{display:flex;align-items:center;gap:10px}.heading-row{min-width:0;flex-wrap:wrap}.heading-divider{height:18px;width:1px;background:#e2e8f0;margin:0 4px}.back-button,.text-button,.actions button,.danger-link{padding:0;background:transparent;border:0;font-size:13px;color:#6366f1;white-space:nowrap}.back-button{color:#64748b}.danger-link{color:#dc2626}.text-button:hover:not(:disabled),.actions button:hover:not(:disabled),.back-button:hover{ text-decoration:underline }
.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:32px;padding:6px 14px;border:1px solid #dce2f5;border-radius:6px;font-size:12px;font-weight:600;line-height:18px;background:#eef2ff;color:#4f46a5;white-space:nowrap}.primary{background:#4f46e5;border-color:#4f46e5;color:white}.secondary:hover:not(:disabled){background:#e0e7ff}.primary:hover:not(:disabled){background:#4338ca}.upload-button{cursor:pointer}.upload-button:has(input:disabled){opacity:.5;cursor:not-allowed}.upload-button input{display:none}button:disabled,input:disabled,textarea:disabled,select:disabled{opacity:.5;cursor:not-allowed}
input,textarea,select{border:1px solid #dce2eb;border-radius:6px;padding:8px 10px;background:white;font-size:13px;line-height:1.6;color:#334155;box-sizing:border-box}input:focus,textarea:focus,select:focus{outline:2px solid #c7d2fe;outline-offset:1px}textarea{resize:vertical}input::placeholder,textarea::placeholder{color:#94a3b8}.filters{display:flex;align-items:center;gap:12px;padding:14px 20px}.filters input{width:240px}.filters>span{margin-left:auto;font-size:12px;color:#64748b}.list-scroll{flex:1;min-height:0;overflow:auto;margin:0 20px 16px;border:1px solid #e2e8f0;border-radius:7px}table{width:100%;border-collapse:collapse}th,td{padding:14px 16px;text-align:left;border-bottom:1px solid #edf0f5}th{font-size:13px;font-weight:700;color:#334155;background:#f1f5f9;white-space:nowrap;position:sticky;top:0}td{font-size:13px}td strong{color:#334155;font-weight:600}.description{margin-top:4px;font-size:12px;color:#64748b;max-width:350px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.tag{display:inline-block;padding:3px 7px;border-radius:4px;background:#f1f5f9;color:#64748b;font-size:12px;font-weight:400;white-space:nowrap}.tag+.tag{margin-left:6px}.tag.required{background:#eef2ff;color:#4f46e5}.actions{white-space:nowrap}.actions button+button{margin-left:16px}.empty{padding:60px 20px;text-align:center;color:#64748b;font-size:13px}
.main-tabs{display:flex;gap:24px;padding:0 24px;border-bottom:1px solid #e2e8f0;flex-shrink:0}.main-tabs button{padding:13px 0;border-bottom:2px solid transparent;font-size:13px;color:#64748b}.main-tabs button.selected{color:#4f46e5;border-bottom-color:#6366f1;font-weight:600}.main-tabs span{margin-left:5px;font-size:12px}.base-scroll{flex:1;min-height:0;overflow:auto}.form-content{max-width:1060px;padding:0 28px 30px;margin:0 auto}.form-section{padding:24px 0;border-bottom:1px solid #edf0f5}.form-section:last-child{border-bottom:0}.form-section h3,.step-sidebar h3{font-size:14px;font-weight:650;line-height:1.7;color:#334155}.form-section>h3{margin-bottom:20px}.form-row{display:flex;align-items:flex-start;gap:20px;margin-bottom:20px}.form-row:last-child{margin-bottom:0}.form-row>label{width:104px;flex-shrink:0;padding-top:9px;text-align:right;font-size:13px;color:#475569;line-height:1.6}.required-mark{margin-right:4px;color:#dc2626}.field{flex:1;min-width:0}.field>input,.field>textarea{width:100%}.field p{padding-top:9px;line-height:1.8;color:#334155;overflow-wrap:anywhere}.short-field{max-width:280px}.short-field select{width:100%}.pre-wrap{white-space:pre-wrap}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:16px}.section-heading p,.section-description{margin-top:4px;color:#64748b;font-size:12px;line-height:1.8}.segmented{display:inline-flex;border:1px solid #e2e8f0;border-radius:6px;background:#f8fafc;padding:2px;flex-shrink:0}.segmented button{font-size:12px;padding:5px 12px;border-radius:4px;color:#64748b}.segmented button.active{background:#fff;color:#4f46e5;box-shadow:0 1px 3px #0f172a10}.content-input{display:block;width:100%;min-height:360px;line-height:1.9;padding:16px}.content-empty{background:#f8fafc;border-radius:6px}.document-content{line-height:1.9;font-size:13px;color:#475569;overflow-wrap:anywhere}.document-content :deep(h1),.document-content :deep(h2){font-size:16px;font-weight:650;margin:18px 0 10px;color:#334155}.document-content :deep(h3){font-size:14px;font-weight:650;margin:16px 0 8px}.document-content :deep(p){margin:8px 0}.document-content :deep(ul){list-style:disc;padding-left:20px}.document-content :deep(ol){list-style:decimal;padding-left:20px}.document-content :deep(table){margin:12px 0;font-size:12px}.document-content :deep(th),.document-content :deep(td){padding:8px;border:1px solid #e2e8f0}.document-content :deep(img){max-width:100%}
.steps-layout{display:flex;flex:1;min-height:0}.step-sidebar{width:230px;flex-shrink:0;border-right:1px solid #e2e8f0;display:flex;flex-direction:column;padding:16px 12px;min-height:0;background:#fafbfe}.step-sidebar header{display:flex;align-items:center;justify-content:space-between;padding:0 10px 12px}.step-sidebar header>span{font-size:12px;color:#64748b}.step-sidebar ol{overflow:auto;flex:1;min-height:0}.step-sidebar li>button{display:flex;align-items:baseline;gap:10px;width:100%;text-align:left;padding:11px 12px;margin-bottom:5px;border-radius:6px;font-size:13px;color:#475569;line-height:1.7}.step-sidebar li>button.selected{background:#eef2ff;color:#4f46e5;font-weight:600}.step-sidebar li>button:hover:not(.selected){background:#f1f5f9}.step-number{font-variant-numeric:tabular-nums;font-weight:500;flex-shrink:0}.add-step{width:100%;margin-top:12px;min-height:34px;border:1px dashed #c7d2fe;border-radius:6px;color:#4f46e5;font-size:12px;background:#fff;flex-shrink:0}.step-main{flex:1;min-width:0;min-height:0;display:flex;flex-direction:column}.step-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 24px 12px;flex-shrink:0}.step-heading h3{font-size:14px;font-weight:650;color:#334155}.step-heading h3>span{margin-left:14px;font-weight:400;font-size:13px;color:#64748b}.step-tabs{display:flex;gap:6px;padding:0 24px 14px;border-bottom:1px solid #edf0f5;flex-shrink:0}.step-tabs button{font-size:12px;padding:7px 13px;border-radius:6px;color:#64748b}.step-tabs button.active{background:#eef2ff;color:#4f46e5;font-weight:600}.step-scroll{flex:1;min-height:0;overflow:auto}.step-form{max-width:1000px;padding:0 24px 24px}.instruction-list{display:flex;flex-direction:column;gap:12px}.instruction-row{display:flex;align-items:flex-start;gap:12px}.instruction-row>span{padding-top:9px;width:20px;flex-shrink:0;text-align:center;color:#64748b;font-size:12px;font-variant-numeric:tabular-nums}.instruction-row>textarea{flex:1;min-width:0}.instruction-row>button{padding-top:9px;font-size:12px}.empty-inline,.help-text{font-size:12px;color:#64748b;line-height:1.8}.help-text{margin-top:10px}.read-list{list-style:decimal;padding-left:24px;line-height:1.9;color:#475569}.read-list li{margin:6px 0;white-space:pre-wrap}.bullet-list{list-style:disc}.media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:16px}.media-grid figure{border:1px solid #e2e8f0;border-radius:6px;overflow:hidden}.media-grid img{display:block;width:100%;height:160px;object-fit:contain;background:#f8fafc}.media-grid figcaption{display:flex;align-items:center;justify-content:space-between;padding:9px 10px;font-size:12px}.media-grid figcaption button{font-size:12px}.media-empty{padding:38px 16px;text-align:center;border:1px dashed #dce2eb;border-radius:6px;background:#fafbfe;color:#94a3b8;font-size:12px}.link-input{margin-top:14px}.link-input summary{cursor:pointer;color:#64748b;font-size:12px}.link-input>input{width:100%;margin-top:10px}.inline-input{display:flex;gap:10px;margin-top:10px}.inline-input input{flex:1;min-width:0}video{display:block;width:560px;max-width:100%;max-height:320px;background:#0f172a;border-radius:6px}.rule-row{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:20px 0;border-bottom:1px solid #f1f5f9}.rule-row:last-child{border-bottom:0;padding-bottom:4px}.rule-row label{font-size:13px;color:#334155}.rule-row p{margin-top:5px;font-size:12px;color:#64748b;line-height:1.8}.rule-row select{min-width:120px}.section-description{margin-top:-12px}.editor-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 24px;border-top:1px solid #e2e8f0;background:#fff;flex-shrink:0}.editor-footer>span{font-size:12px;color:#64748b}.alert{padding:10px 20px;font-size:13px;line-height:1.7;flex-shrink:0}.error{background:#fff7ed;color:#9a3412}.success{background:#ecfdf5;color:#047857}
@media(max-width:900px){.experiment-manager{margin:10px}.form-content{padding:0 20px 24px}.step-sidebar{width:200px}.step-form{padding:0 18px 20px}.manager-head{padding:14px 16px}.heading-row .tag{display:none}}
@media(max-width:600px){.manager-head,.editor-footer{flex-wrap:wrap}.filters{flex-wrap:wrap;padding:12px}.filters input{width:170px}.filters>span{margin-left:0}.steps-layout{flex-direction:column}.step-sidebar{width:100%;max-height:180px;border-right:0;border-bottom:1px solid #e2e8f0;padding:10px}.step-sidebar ol{display:flex;gap:6px;overflow:auto}.step-sidebar li{flex-shrink:0;max-width:190px}.step-sidebar li>button{margin:0}.step-sidebar header{padding-bottom:6px}.add-step{margin-top:8px}.step-heading{padding:14px 16px}.step-heading h3>span{display:none}.step-tabs{padding:0 16px 10px}.form-row{flex-direction:column;gap:7px}.form-row>label{text-align:left;width:auto;padding:0}.field{width:100%}.form-content{padding:0 16px 20px}.rule-row{gap:12px}.rule-row select{min-width:90px}.editor-footer{padding:12px 16px}.editor-footer>span{display:none}.editor-footer .button-group{margin-left:auto}.main-tabs{padding:0 16px}}
</style>
