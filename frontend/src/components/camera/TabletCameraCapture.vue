<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/65 px-4 py-5 backdrop-blur-sm"
    >
      <section class="w-full max-w-3xl overflow-hidden rounded-2xl border border-line-soft bg-white shadow-2xl">
        <header class="flex items-center justify-between gap-3 border-b border-line-soft px-4 py-3">
          <div class="min-w-0">
            <h2 class="text-[15px] font-bold text-ink-strong">拍照上传</h2>
          </div>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted transition-colors hover:bg-surface-soft hover:text-ink-strong"
            title="关闭"
            aria-label="关闭"
            @click="close"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </header>

        <div class="bg-slate-950">
          <div class="relative mx-auto aspect-video max-h-[62vh] w-full overflow-hidden bg-slate-900">
            <img
              v-if="previewUrl"
              :src="previewUrl"
              class="absolute inset-0 h-full w-full object-contain"
              alt="系统拍照预览"
            />

            <div
              v-else
              class="absolute inset-0 flex flex-col items-center justify-center gap-3 px-6 text-center text-white"
            >
              <svg class="h-10 w-10 text-brand-200" fill="none" stroke="currentColor" stroke-width="1.6" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M4 8.5A2.5 2.5 0 016.5 6H8l1.4-2h5.2L16 6h1.5A2.5 2.5 0 0120 8.5v8A2.5 2.5 0 0117.5 19h-11A2.5 2.5 0 014 16.5v-8z"
                />
                <circle cx="12" cy="12.5" r="3.2" />
              </svg>
              <div class="space-y-1">
                <p class="text-[13px] font-semibold">请使用平板相机拍摄实验台画面</p>
                <p class="text-[12px] text-white/70">如果系统相机没有自动弹出，请点击下方“打开相机”。</p>
              </div>
            </div>
          </div>
        </div>

        <footer class="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
          <p class="text-[11px] text-ink-muted">
            {{ pendingFile ? '请确认照片清晰后使用，也可以重拍。' : '拍完后会回到这里预览确认。' }}
          </p>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="rounded-lg border border-line-soft bg-white px-3 py-1.5 text-[12px] font-bold text-ink-muted transition-colors hover:border-brand-200 hover:text-brand-600"
              @click="close"
            >
              取消
            </button>
            <button
              v-if="pendingFile"
              type="button"
              class="rounded-lg border border-brand-200 bg-white px-3 py-1.5 text-[12px] font-bold text-brand-600 transition-colors hover:bg-brand-50"
              @click="openSystemCamera"
            >
              重拍
            </button>
            <button
              v-if="pendingFile"
              type="button"
              class="btn-brand rounded-lg px-5 py-1.5 text-[12px] font-bold"
              @click="confirmPhoto"
            >
              使用照片
            </button>
            <button
              v-else
              type="button"
              class="btn-brand rounded-lg px-5 py-1.5 text-[12px] font-bold"
              @click="openSystemCamera"
            >
              打开相机
            </button>
          </div>
          <input
            ref="systemPhotoInput"
            type="file"
            accept="image/*"
            capture="environment"
            class="hidden"
            @change="onSystemPhotoChange"
          />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'captured'])

const systemPhotoInput = ref(null)
const pendingFile = ref(null)
const previewUrl = ref('')

function clearPreview() {
  pendingFile.value = null
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function openSystemCamera() {
  clearPreview()
  systemPhotoInput.value?.click()
}

function close() {
  clearPreview()
  emit('close')
}

function onSystemPhotoChange(e) {
  const file = e.target.files?.[0]
  if (file) {
    clearPreview()
    pendingFile.value = file
    previewUrl.value = URL.createObjectURL(file)
  }
  e.target.value = ''
}

function confirmPhoto() {
  if (!pendingFile.value) return
  const file = pendingFile.value
  clearPreview()
  emit('captured', file)
  emit('close')
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      clearPreview()
      return
    }
    await nextTick()
    openSystemCamera()
  }
)

onBeforeUnmount(() => {
  clearPreview()
})
</script>
