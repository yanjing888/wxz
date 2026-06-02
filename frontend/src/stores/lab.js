import { defineStore } from 'pinia'
import { experimentApi, sessionApi, uploadApi } from '../api'
import { sniffImageMime, readFileAsDataUrl } from '../utils/imageFile'

export const useLabStore = defineStore('lab', {
  state: () => ({
    experiments: [],
    experiment: null,
    session: null,
    activeStep: 1,
    imageUrl: '',
    imagePreview: '',
    composerImageUrl: '',
    composerImagePreview: '',
    marks: [],
    messages: [],
    envCheckEnabled: true,
    envLevel: 'L0',
    envHint: '暂无异常',
    envSuggestion: '',
    envLogs: [],
    envCheckRunning: false,
    envTimer: null,
    tutViewCount: 0,
    uploadingImage: false,
    loadingAssist: false,
    uploadSeq: 0,
    uploadError: ''
  }),
  getters: {
    stepConfig(state) {
      return state.experiment?.steps?.[String(state.activeStep)] || null
    },
    stepCount(state) {
      return state.experiment?.menuLabels?.length || 5
    },
    /** 已成功上传到服务器的图片地址（不含 ? 参数），用于提交给智能体 */
    readyImageUrl(state) {
      const raw = state.composerImageUrl || state.imageUrl
      if (!raw || !raw.startsWith('/uploads/')) return ''
      return raw.split('?')[0]
    }
  },
  actions: {
    async loadExperiments() {
      const { data } = await experimentApi.list()
      this.experiments = data
    },
    async loadExperiment(code) {
      const { data } = await experimentApi.get(code)
      this.experiment = data
    },
    async startSession(experimentCode, studentName, studentClass) {
      const { data } = await sessionApi.start({ experimentCode, studentName, studentClass })
      this.session = data
      this.activeStep = 1
      this.resetLabUi()
      this.pushAi(`同学你好，实验已开始。\n\n可在中间栏查看步骤与上传实拍图；在下方输入问题并**发送**（Enter 或发送按钮），可上传图片一并提问。`)
    },
    resetLabUi() {
      this.imageUrl = ''
      this.imagePreview = ''
      this.composerImageUrl = ''
      this.composerImagePreview = ''
      this.marks = []
      this.messages = []
      this.envLogs = []
      this.envLevel = 'L0'
      this.envHint = '暂无异常'
      this.envSuggestion = ''
      this.tutViewCount = 0
    },
    async selectStep(stepId) {
      this.activeStep = stepId
      if (this.session?.id) {
        const { data } = await sessionApi.updateStep(this.session.id, stepId)
        this.session = data
      }
    },
    async uploadImage(file) {
      if (!file || !file.size) {
        throw new Error('请选择有效的图片文件')
      }

      const originalFile = file
      const sniffed = await sniffImageMime(originalFile)
      if (
        sniffed.includes('heic') ||
        sniffed.includes('heif') ||
        /\.heic$/i.test(originalFile.name || '') ||
        /\.heif$/i.test(originalFile.name || '')
      ) {
        throw new Error('当前浏览器不支持 HEIC/HEIF 格式，请先将图片转为 JPG 或 PNG')
      }

      const seq = ++this.uploadSeq
      this.uploadingImage = true
      this.uploadError = ''
      this.marks = []

      const dataUrl = await readFileAsDataUrl(originalFile)
      this.composerImagePreview = dataUrl
      this.imagePreview = dataUrl

      try {
        const { data } = await uploadApi.image(originalFile)
        if (seq !== this.uploadSeq) return

        this.composerImageUrl = data.url
        this.imageUrl = data.url
      } catch (e) {
        if (seq !== this.uploadSeq) return

        this.composerImageUrl = ''
        this.imageUrl = ''
        this.uploadError = e.response?.data?.message || e.message || '图片上传失败'
        throw e
      } finally {
        if (seq === this.uploadSeq) {
          this.uploadingImage = false
        }
      }
    },
    revokeBlobIfUnused(url) {
      if (!url?.startsWith('blob:')) return
      const usedInChat = this.messages.some((m) => m.image === url)
      if (!usedInChat && this.imagePreview !== url && this.composerImagePreview !== url) {
        URL.revokeObjectURL(url)
      }
    },
    clearComposerImage() {
      this.revokeBlobIfUnused(this.composerImagePreview)
      this.composerImageUrl = ''
      this.composerImagePreview = ''
    },
    clearImage() {
      this.revokeBlobIfUnused(this.composerImagePreview)
      this.revokeBlobIfUnused(this.imagePreview)
      this.imageUrl = ''
      this.imagePreview = ''
      this.composerImageUrl = ''
      this.composerImagePreview = ''
      this.marks = []
      this.uploadError = ''
    },
    pushUser(text, image, imageFallback = '') {
      this.messages.push({
        role: 'user',
        text: text || '',
        image: image || '',
        imageFallback: imageFallback || '',
        ts: Date.now()
      })
    },
    pushAi(text) {
      this.messages.push({ role: 'ai', text, ts: Date.now() })
    },
    async sendMessage(userMessage) {
      if (!this.session?.id || this.loadingAssist || this.uploadingImage) return false

      const text = (userMessage || '').trim()
      const imageUrl = this.readyImageUrl
      const hasPreview = !!(this.composerImagePreview || this.imagePreview)

      if (!text && !imageUrl) {
        if (hasPreview) {
          window.alert(
            this.uploadError
              ? `图片上传失败：${this.uploadError}\n请点「更换图片」重新上传。`
              : '图片尚未上传完成，请等待上传结束后再发送'
          )
        }
        return false
      }

      this.loadingAssist = true
      const hadImage = !!imageUrl
      const displayImage = this.composerImagePreview || this.imagePreview || imageUrl
      this.marks = []
      const prompt = text || (hadImage ? '请分析上传的实验图片。' : '')

      this.pushUser(prompt, hadImage ? displayImage : '', hadImage ? imageUrl : '')
      this.clearComposerImage()

      const aiIndex = this.messages.length
      this.messages.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

      let pendingMarks = null
      const applyMarksIfReady = () => {
        if (pendingMarks?.length) {
          this.marks = pendingMarks
          pendingMarks = null
        }
      }

      try {
        await sessionApi.assistStream(
          this.session.id,
          { userMessage: prompt, imageUrl: imageUrl || undefined },
          {
            onMarks: (marks) => {
              if (marks?.length) pendingMarks = marks
            },
            onChunk: (chunk) => {
              applyMarksIfReady()
              this.messages[aiIndex].text += chunk
            },
            onDone: (data) => {
              this.messages[aiIndex].streaming = false
              if (data.feedback) {
                this.messages[aiIndex].text = data.feedback
              }
              if (data.marks?.length) pendingMarks = data.marks
              applyMarksIfReady()
            },
            onError: (msg) => {
              this.messages[aiIndex].streaming = false
              if (!this.messages[aiIndex].text) {
                this.messages[aiIndex].text = `**请求失败**：${msg}`
              }
            }
          }
        )
        this.session = (await sessionApi.get(this.session.id)).data
      } catch (e) {
        this.messages[aiIndex].streaming = false
        const msg = e.message || '网络异常，请稍后重试'
        if (!this.messages[aiIndex].text) {
          this.messages[aiIndex].text = `**请求失败**：${msg}`
        }
      } finally {
        this.loadingAssist = false
        if (this.messages[aiIndex]) {
          this.messages[aiIndex].streaming = false
        }
      }
      return true
    },
    async runEnvCheck() {
      if (!this.session?.id || this.envCheckRunning) return
      this.envCheckRunning = true
      try {
        const { data } = await sessionApi.envCheck(this.session.id)
        this.envLevel = data.level
        this.envHint = data.summary
        this.envSuggestion = data.suggestion || ''
        const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        this.envLogs.unshift({ time, level: data.level, summary: data.summary })
        if (this.envLogs.length > 18) this.envLogs.pop()
        this.session = (await sessionApi.get(this.session.id)).data
      } finally {
        this.envCheckRunning = false
      }
    },
    startEnvTimer() {
      this.stopEnvTimer()
      if (!this.envCheckEnabled) return
      this.envTimer = setInterval(() => this.runEnvCheck(), 60000)
    },
    stopEnvTimer() {
      if (this.envTimer) {
        clearInterval(this.envTimer)
        this.envTimer = null
      }
    },
    toggleEnvCheck(enabled) {
      this.envCheckEnabled = enabled
      if (enabled) this.startEnvTimer()
      else this.stopEnvTimer()
    },
    async openTutorial() {
      if (this.session?.id) {
        const { data } = await sessionApi.tutorialView(this.session.id)
        this.session = data
        this.tutViewCount = data.tutViewCount
      }
    },
    async finishSession() {
      if (!this.session?.id) return null
      const { data } = await sessionApi.finish(this.session.id)
      this.session = data
      this.stopEnvTimer()
      const { data: report } = await sessionApi.report(this.session.id)
      return report
    },
    async downloadReportDocx() {
      if (!this.session?.id) return
      const { data } = await sessionApi.reportDocx(this.session.id)
      const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `实验总结报告-${this.experiment?.name || '实验'}.docx`
      a.click()
      URL.revokeObjectURL(url)
    }
  }
})
