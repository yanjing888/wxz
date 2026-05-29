import { defineStore } from 'pinia'
import { experimentApi, sessionApi, uploadApi } from '../api'

export const useLabStore = defineStore('lab', {
  state: () => ({
    experiments: [],
    experiment: null,
    session: null,
    activeStep: 1,
    imageUrl: '',
    imagePreview: '',
    marks: [],
    showAnalysisLabel: false,
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
    loadingAssist: false
  }),
  getters: {
    stepConfig(state) {
      return state.experiment?.steps?.[String(state.activeStep)] || null
    },
    stepCount(state) {
      return state.experiment?.menuLabels?.length || 5
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
      this.marks = []
      this.showAnalysisLabel = false
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
      this.uploadingImage = true
      const localPreview = URL.createObjectURL(file)
      this.imagePreview = localPreview
      this.marks = []
      this.showAnalysisLabel = false
      try {
        const { data } = await uploadApi.image(file)
        this.imageUrl = data.url
      } catch (e) {
        URL.revokeObjectURL(localPreview)
        this.imageUrl = ''
        this.imagePreview = ''
        throw e
      } finally {
        this.uploadingImage = false
      }
    },
    clearImage() {
      if (this.imagePreview?.startsWith('blob:')) {
        URL.revokeObjectURL(this.imagePreview)
      }
      this.imageUrl = ''
      this.imagePreview = ''
      this.marks = []
      this.showAnalysisLabel = false
    },
    pushUser(text, image) {
      this.messages.push({ role: 'user', text: text || '', image: image || '', ts: Date.now() })
    },
    pushAi(text) {
      this.messages.push({ role: 'ai', text, ts: Date.now() })
    },
    async sendMessage(userMessage) {
      if (!this.session?.id || this.loadingAssist || this.uploadingImage) return
      const text = (userMessage || '').trim()
      if (!text && !this.imageUrl) return

      this.loadingAssist = true
      const imageUrl = this.imageUrl
      const imagePreview = this.imagePreview
      const hadImage = !!imageUrl
      const prompt = text || (hadImage ? '请分析上传的实验图片。' : '')

      this.pushUser(prompt, hadImage ? imagePreview : '')

      const aiIndex = this.messages.length
      this.messages.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

      try {
        await sessionApi.assistStream(
          this.session.id,
          { userMessage: prompt, imageUrl: imageUrl || undefined },
          {
            onChunk: (chunk) => {
              this.loadingAssist = false
              this.messages[aiIndex].text += chunk
            },
            onDone: (data) => {
              this.messages[aiIndex].streaming = false
              if (data.marks?.length) {
                this.marks = data.marks
                this.showAnalysisLabel = true
              }
              if (!this.messages[aiIndex].text && data.feedback) {
                this.messages[aiIndex].text = data.feedback
              }
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
        if (hadImage) this.clearImage()
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
