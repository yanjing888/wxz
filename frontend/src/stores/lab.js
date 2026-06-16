import { defineStore } from 'pinia'
import { experimentApi, sessionApi, uploadApi } from '../api'
import { sniffImageMime, readFileAsDataUrl } from '../utils/imageFile'

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function briefEnvSummary(text, maxLen = 80) {
  if (!text) return '暂无异常'
  const plain = String(text)
    .replace(/[#*_>`[\]()]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) return '暂无异常'
  return plain.length <= maxLen ? plain : `${plain.slice(0, maxLen)}…`
}

const WELCOME_MESSAGE = `你好，我是物小智。

左侧工作区会按步骤引导你操作：

- 需要数据的步骤可**连接仪器自动采集**并提交纠错
- 需要现场确认的步骤请**上传实验台照片**进行纠错
- 点击右上角「本步骤教程」可查看操作说明

有疑问随时问我，也可以点输入框上方的推荐问题。`

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
    _envCaptureFn: null,
    tutViewCount: 0,
    uploadingImage: false,
    loadingAssist: false,
    assistStreamAbort: null,
    uploadSeq: 0,
    uploadError: '',
    sessionDataByStep: {},
    submittingData: false,
    dataSubmitErrors: [],
    deviceConnected: false,
    deviceState: 'idle',
    deviceName: '',
    deviceType: '',
    deviceSamplingHz: 0,
    deviceSnapshot: {},
    deviceLive: {},
    deviceCurve: [],
    deviceConnecting: false,
    deviceReading: false,
    deviceStreamAbort: null,
    deviceAcquireProgress: null,
    _deviceAcquireRunId: 0,
    _pendingAcquireSnapshot: null,
    _playbackSamples: [],
    switchingExperiment: false,
    /** 用户手动切换数据采集区折叠态的覆盖值：{ [stepNo]: boolean } */
    dataPanelOverride: {}
  }),
  getters: {
    stepConfig(state) {
      return state.experiment?.steps?.[String(state.activeStep)] || null
    },
    dataCollectionEnabled(state) {
      return !!state.experiment?.dataCollection?.enabled
    },
    useDeviceData(state) {
      const step = state.experiment?.steps?.[String(state.activeStep)]
      return step?.dataSource === 'device'
    },
    /** 当前步骤是否可以采集数据（用于折叠态默认值与开关可见性） */
    hasDataPanel(state) {
      if (!state.experiment?.dataCollection?.enabled) return false
      const step = state.experiment?.steps?.[String(state.activeStep)]
      if (!step) return false
      if (step.dataSource === 'device') return true
      if (step.correctionMode === 'data') return true
      return Array.isArray(step.dataFields) && step.dataFields.length > 0
    },
    /** 数据采集区是否展开：用户手动覆盖优先，否则按步骤配置自动决定 */
    dataPanelOpen(state) {
      if (!this.hasDataPanel) return false
      const override = state.dataPanelOverride[String(state.activeStep)]
      if (override != null) return override
      return true
    },
    useManualDataCorrection() {
      return this.hasDataPanel && !this.useDeviceData
    },
    deviceBusy(state) {
      return state.deviceConnecting || state.deviceReading
    },
    deviceHasSubmitData(state) {
      const snap = state.deviceSnapshot || {}
      const fields = state.experiment?.steps?.[String(state.activeStep)]?.dataFields || []
      const required = fields.filter((f) => f.required !== false)
      if (!required.length) return Object.keys(snap).length > 0
      return required.every((f) => {
        const v = snap[f.key]
        return v != null && String(v).trim() !== ''
      })
    },
    /** 图片上传区：始终可用，所有步骤都允许拍照求助 */
    useVisionCorrection() {
      return true
    },
    currentDataFields(state) {
      return state.experiment?.steps?.[String(state.activeStep)]?.dataFields || []
    },
    currentStepDataValues(state) {
      return state.sessionDataByStep[String(state.activeStep)]?.values || {}
    },
    currentStepDataSaved(state) {
      return !!state.sessionDataByStep[String(state.activeStep)]?.feedback
    },
    stepCount(state) {
      return state.experiment?.menuLabels?.length || 5
    },
    /** 聊天输入区待发送的图片（仅 composer，不含左侧实拍区已展示的图片） */
    readyImageUrl(state) {
      const raw = state.composerImageUrl
      if (!raw || !raw.startsWith('/uploads/')) return ''
      return raw.split('?')[0]
    }
  },
  actions: {
    async loadExperiments() {
      const { data } = await experimentApi.list()
      const order = ['newton_rings', 'tensile_steel', 'general']
      this.experiments = [...(data || [])].sort((a, b) => {
        const ia = order.indexOf(a.code)
        const ib = order.indexOf(b.code)
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
      })
    },
    async loadExperiment(code) {
      const { data } = await experimentApi.get(code)
      this.experiment = data
    },
    async switchExperiment(experimentCode) {
      const code = (experimentCode || '').trim()
      if (!code || code === this.experiment?.code) return false

      this.switchingExperiment = true
      this.stopEnvTimer()
      try {
        localStorage.setItem('wxz_exp', code)
        const name = localStorage.getItem('wxz_name') || this.session?.studentName || '学生'
        const cls = localStorage.getItem('wxz_class') || this.session?.studentClass || ''
        await this.loadExperiment(code)
        await this.startSession(code, name, cls)
        this.startEnvTimer()
        return true
      } catch (e) {
        throw e
      } finally {
        this.switchingExperiment = false
      }
    },
    async startSession(experimentCode, studentName, studentClass) {
      const { data } = await sessionApi.start({ experimentCode, studentName, studentClass })
      this.session = data
      this.activeStep = 1
      this.resetLabUi()
      await this.loadSessionData()
      this.pushAi(WELCOME_MESSAGE)
      if (this.useDeviceData) {
        await this.prepareDeviceStep()
      }
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
      this.sessionDataByStep = {}
      this.dataSubmitErrors = []
      this.dataPanelOverride = {}
      this.teardownDevice()
    },
    toggleDataPanel(forceOpen) {
      if (!this.hasDataPanel) return
      const key = String(this.activeStep)
      const next = typeof forceOpen === 'boolean' ? forceOpen : !this.dataPanelOpen
      this.dataPanelOverride = { ...this.dataPanelOverride, [key]: next }
    },
    teardownDevice() {
      this.cancelDeviceAcquirePlayback()
      if (this.session?.id && this.deviceState === 'acquiring') {
        sessionApi.deviceStop(this.session.id, this.activeStep).catch(() => {})
      }
      this.deviceConnected = false
      this.deviceState = 'idle'
      this.deviceName = ''
      this.deviceType = ''
      this.deviceSamplingHz = 0
      this.deviceSnapshot = {}
      this.deviceLive = {}
      this.deviceCurve = []
      this.deviceConnecting = false
      this.deviceReading = false
      this.deviceAcquireProgress = null
      this._pendingAcquireSnapshot = null
      this._playbackSamples = []
    },
    cancelDeviceAcquirePlayback() {
      this._deviceAcquireRunId += 1
      if (this.deviceStreamAbort) {
        this.deviceStreamAbort.abort()
        this.deviceStreamAbort = null
      }
    },
    summarizePartialSamples(samples) {
      if (!samples?.length) return {}
      let fMax = 0
      let fYield = 0
      let strainYield = 0
      let yieldSet = false
      for (const s of samples) {
        const f = Number(s.forceKn) || 0
        const stress = Number(s.stressMpa) || 0
        if (f > fMax) fMax = f
        if (!yieldSet && stress >= 230 && stress <= 250) {
          fYield = f
          strainYield = Number(s.strainPct) || 0
          yieldSet = true
        }
      }
      if (!yieldSet && samples.length > 20) {
        const p = samples[25]
        fYield = Number(p.forceKn) || 0
        strainYield = Number(p.strainPct) || 0
      }
      return {
        F_max_kN: Math.round(fMax * 1000) / 1000,
        F_yield_kN: Math.round(fYield * 1000) / 1000,
        strain_yield_pct: Math.round(strainYield * 1000) / 1000
      }
    },
    async playAcquireSamples(samples, finalSnapshot, runId) {
      const hz = this.deviceSamplingHz > 0 ? this.deviceSamplingHz : 10
      const intervalMs = Math.max(40, Math.floor(1000 / hz))
      const total = samples.length
      this._playbackSamples = samples

      for (let i = 0; i < total; i++) {
        if (runId !== this._deviceAcquireRunId) return false
        const s = samples[i]
        this.deviceCurve.push({
          strainPct: s.strainPct,
          stressMpa: s.stressMpa
        })
        this.deviceLive = {
          forceKn: s.forceKn,
          strainPct: s.strainPct,
          stressMpa: s.stressMpa,
          displacementMm: s.displacementMm
        }
        this.deviceAcquireProgress = { current: i + 1, total }
        if (i < total - 1) {
          await sleep(intervalMs)
        }
      }
      if (runId !== this._deviceAcquireRunId) return false
      this.deviceSnapshot = { ...finalSnapshot }
      this.deviceState = 'completed'
      this.deviceAcquireProgress = { current: total, total }
      this._pendingAcquireSnapshot = null
      return true
    },
    async loadSessionData() {
      if (!this.session?.id) return
      try {
        const { data } = await sessionApi.getData(this.session.id)
        this.sessionDataByStep = data?.byStep || {}
      } catch {
        this.sessionDataByStep = {}
      }
    },
    async selectStep(stepId) {
      this.teardownDevice()
      this.activeStep = stepId
      this.dataSubmitErrors = []
      if (this.session?.id) {
        const { data } = await sessionApi.updateStep(this.session.id, stepId)
        this.session = data
      }
      if (this.useDeviceData) {
        await this.prepareDeviceStep()
      }
    },
    async prepareDeviceStep() {
      if (!this.session?.id) return
      this.deviceType = this.stepConfig?.deviceType || ''
      await this.connectDevice()
      if (this.deviceType === 'dimension_measure'
          || this.deviceType === 'reading_microscope'
          || this.deviceType === 'newton_analyzer') {
        await this.readDeviceOnce(true)
      } else if (this.deviceType === 'post_measure') {
        const hasTensile = this.sessionDataByStep['4']?.values
        if (hasTensile) await this.readDeviceOnce(true)
      }
    },
    async connectDevice() {
      if (!this.session?.id) return
      this.deviceConnecting = true
      try {
        const { data } = await sessionApi.deviceConnect(this.session.id, this.activeStep)
        this.applyDeviceStatus(data)
      } catch (e) {
        this.deviceConnected = false
        this.deviceState = 'idle'
        this.dataSubmitErrors = [e.response?.data?.message || e.message || '仪器连接失败']
      } finally {
        this.deviceConnecting = false
      }
    },
    applyDeviceStatus(data) {
      const nextState = data?.state || 'idle'
      if (this.deviceState === 'completed' && nextState === 'acquiring') {
        return
      }
      this.deviceConnected = !!data?.connected
      this.deviceState = nextState
      this.deviceName = data?.deviceName || ''
      this.deviceType = data?.deviceType || this.stepConfig?.deviceType || ''
      this.deviceSamplingHz = data?.samplingHz || 0
      if (data?.snapshot && Object.keys(data.snapshot).length) {
        this.deviceSnapshot = { ...data.snapshot }
      }
    },
    async syncDeviceSnapshot() {
      if (!this.session?.id) return
      try {
        const { data } = await sessionApi.deviceSnapshot(this.session.id, this.activeStep)
        if (data?.values && Object.keys(data.values).length) {
          this.deviceSnapshot = { ...data.values }
        }
        if (data?.curve?.length) {
          this.deviceCurve = data.curve.map((p) => ({
            strainPct: p.strainPct,
            stressMpa: p.stressMpa
          }))
        }
        if (data?.live) {
          this.deviceLive = { ...data.live }
        }
        const serverState = data?.state
        if (this.deviceHasSubmitData) {
          this.deviceState = 'completed'
        } else if (serverState && serverState !== 'acquiring') {
          this.deviceState = serverState
        }
      } catch {
        if (this.deviceCurve.length > 0 && this.deviceLive?.forceKn != null) {
          this.deviceState = 'completed'
        }
      }
    },
    async readDeviceOnce(silent = false) {
      if (!this.session?.id || this.deviceReading) return
      this.cancelDeviceAcquirePlayback()
      const runId = this._deviceAcquireRunId
      this.deviceReading = true
      if (!silent) this.dataSubmitErrors = []
      try {
        if (!this.deviceConnected) {
          await this.connectDevice()
        }
        const { data } = await sessionApi.deviceAcquire(this.session.id, this.activeStep)
        if (runId !== this._deviceAcquireRunId) return
        await this.animateStaticRead(data, runId)
        this.deviceName = this.deviceName || this.stepConfig?.title
      } catch (e) {
        const msg = e.response?.data?.message || e.message || '读取失败'
        if (!silent) this.dataSubmitErrors = [msg]
      } finally {
        this.deviceReading = false
      }
    },
    applyAcquireResult(data) {
      if (data?.values && Object.keys(data.values).length) {
        this.deviceSnapshot = { ...data.values }
      }
      if (data?.curve?.length) {
        this.deviceCurve = data.curve.map((p) => ({
          strainPct: p.strainPct,
          stressMpa: p.stressMpa
        }))
      }
      if (data?.live) {
        this.deviceLive = {
          forceKn: data.live.forceKn,
          strainPct: data.live.strainPct,
          stressMpa: data.live.stressMpa,
          displacementMm: data.live.displacementMm
        }
      } else if (this.deviceCurve.length) {
        const last = this.deviceCurve[this.deviceCurve.length - 1]
        const snap = this.deviceSnapshot || {}
        this.deviceLive = {
          forceKn: snap.F_max_kN,
          strainPct: last.strainPct,
          stressMpa: last.stressMpa,
          displacementMm: null
        }
      }
      this.deviceState = this.deviceHasSubmitData ? 'completed' : data?.state || 'ready'
    },
    async startDeviceAcquisition() {
      if (!this.session?.id || this.deviceState === 'acquiring') return
      this.cancelDeviceAcquirePlayback()
      const runId = this._deviceAcquireRunId
      this.dataSubmitErrors = []
      this.deviceCurve = []
      this.deviceSnapshot = {}
      this.deviceLive = {}
      this.deviceAcquireProgress = { current: 0, total: 0 }
      this.deviceState = 'acquiring'

      try {
        if (!this.deviceConnected) {
          await this.connectDevice()
        }
        if (runId !== this._deviceAcquireRunId) return

        const { data } = await sessionApi.deviceAcquire(this.session.id, this.activeStep)
        if (runId !== this._deviceAcquireRunId) return

        if (this.deviceType === 'universal_tester') {
          const samples = data?.samples?.length
            ? data.samples
            : (data?.curve || []).map((p) => ({
                strainPct: p.strainPct,
                stressMpa: p.stressMpa,
                forceKn: null,
                displacementMm: null
              }))
          const finalSnap = { ...(data?.values || {}) }
          this._pendingAcquireSnapshot = finalSnap
          this.deviceAcquireProgress = { current: 0, total: samples.length }

          if (!samples.length) {
            this.applyAcquireResult(data)
            if (!this.deviceHasSubmitData) {
              this.dataSubmitErrors = ['采集完成但未得到有效特征点，请重试']
              this.deviceState = 'ready'
            }
            return
          }

          const ok = await this.playAcquireSamples(samples, finalSnap, runId)
          if (!ok) return
          if (!this.deviceHasSubmitData) {
            this.dataSubmitErrors = ['采集完成但未得到有效特征点，请重试']
            this.deviceState = 'ready'
          }
          return
        }

        await this.animateStaticRead(data, runId)
      } catch (e) {
        if (runId === this._deviceAcquireRunId) {
          this.dataSubmitErrors = [e.response?.data?.message || e.message || '采集失败']
          this.deviceState = 'ready'
          this.deviceAcquireProgress = null
        }
      }
    },
    async animateStaticRead(data, runId) {
      const values = data?.values || {}
      const keys = Object.keys(values)
      this.deviceState = 'acquiring'
      this.deviceSnapshot = {}
      for (let i = 0; i < keys.length; i++) {
        if (runId !== this._deviceAcquireRunId) return
        const k = keys[i]
        this.deviceSnapshot = { ...this.deviceSnapshot, [k]: values[k] }
        await sleep(380)
      }
      if (runId !== this._deviceAcquireRunId) return
      this.applyAcquireResult(data)
    },
    async stopDeviceAcquisition() {
      const runId = this._deviceAcquireRunId
      this.cancelDeviceAcquirePlayback()

      if (this.deviceType === 'universal_tester' && this._pendingAcquireSnapshot) {
        const played = this.deviceCurve.length
        if (played > 5 && this.deviceAcquireProgress) {
          const partial = this.summarizePartialSamples(this._playbackSamples.slice(0, played))
          this.deviceSnapshot = {
            ...this._pendingAcquireSnapshot,
            ...partial,
            point_count: played
          }
          this.deviceState = 'completed'
        } else {
          this.deviceState = 'ready'
          this.deviceSnapshot = {}
        }
        this._pendingAcquireSnapshot = null
        this.deviceAcquireProgress = null
        return
      }

      if (this.session?.id) {
        await sessionApi.deviceStop(this.session.id, this.activeStep).catch(() => {})
      }
      await this.syncDeviceSnapshot()
      if (this.deviceState === 'acquiring' && !this.deviceHasSubmitData) {
        this.deviceState = 'ready'
      }
    },
    async submitDeviceData() {
      if (!this.deviceHasSubmitData) {
        await this.syncDeviceSnapshot()
      }
      const values = { ...this.deviceSnapshot }
      if (!Object.keys(values).length) {
        this.dataSubmitErrors = ['暂无采集数据，请先完成仪器采集或读取测量值']
        return false
      }
      return this.submitStepData(values, true)
    },
    async submitStepData(values, fromDevice = false) {
      if (!this.session?.id || this.submittingData) return false
      this.submittingData = true
      this.dataSubmitErrors = []

      const stepId = this.activeStep
      const stepTitle = this.stepConfig?.title || ''
      const summary = Object.entries(values || {})
        .map(([k, v]) => `${k}: ${v}`)
        .join('，')
      const prefix = fromDevice ? '【仪器采集】' : '【数据提交】'
      this.pushUser(`${prefix}${stepTitle}\n${summary || '(空)'}`)

      const aiIndex = this.messages.length
      this.messages.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

      try {
        const { data } = await sessionApi.submitData(this.session.id, { stepId, values })
        const feedback = data.assist?.feedback || ''
        this.messages[aiIndex].text = feedback
        this.messages[aiIndex].streaming = false

        if (data.validation?.errors?.length) {
          this.dataSubmitErrors = data.validation.errors
        }

        const key = String(stepId)
        this.sessionDataByStep[key] = {
          stepId,
          stepTitle,
          values: data.values || values,
          validation: data.validation,
          feedback
        }
        this.session = (await sessionApi.get(this.session.id)).data
        return true
      } catch (e) {
        this.messages[aiIndex].streaming = false
        const msg = e.response?.data?.message || e.message || '数据提交失败'
        this.dataSubmitErrors = [msg]
        this.messages[aiIndex].text = `**提交失败**：${msg}`
        return false
      } finally {
        this.submittingData = false
      }
    },
    /**
     * 上传图片。
     * 上传后同时写入对话框附件状态和左栏「实验台拍摄」状态，两侧保持一致；
     * `target` 仅用于记录入口（保留参数以兼容调用方）。
     */
    async uploadImage(file, { target = 'composer' } = {}) { // eslint-disable-line no-unused-vars
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
    cancelAssistStream() {
      if (this.assistStreamAbort) {
        this.assistStreamAbort.abort()
        this.assistStreamAbort = null
      }
    },
    stopAssist() {
      if (!this.loadingAssist) return
      this.cancelAssistStream()
      for (let i = this.messages.length - 1; i >= 0; i--) {
        if (this.messages[i].streaming) {
          this.messages[i].streaming = false
          break
        }
      }
      this.loadingAssist = false
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

      this.cancelAssistStream()
      this.loadingAssist = true
      const abortCtrl = new AbortController()
      this.assistStreamAbort = abortCtrl
      const hadImage = !!imageUrl
      const displayImage = this.composerImagePreview || this.imagePreview || imageUrl
      if (hadImage) this.marks = []
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
              if (hadImage && marks?.length) pendingMarks = marks
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
              if (hadImage && data.marks?.length) pendingMarks = data.marks
              if (hadImage) applyMarksIfReady()
            },
            onError: (msg) => {
              if (abortCtrl.signal.aborted) return
              this.messages[aiIndex].streaming = false
              if (!this.messages[aiIndex].text) {
                this.messages[aiIndex].text = `**请求失败**：${msg}`
              }
            }
          },
          abortCtrl.signal
        )
        if (!abortCtrl.signal.aborted) {
          this.session = (await sessionApi.get(this.session.id)).data
        }
      } catch (e) {
        const aborted = e.name === 'AbortError' || abortCtrl.signal.aborted
        if (this.messages[aiIndex]) {
          this.messages[aiIndex].streaming = false
        }
        if (!aborted) {
          const msg = e.message || '网络异常，请稍后重试'
          if (this.messages[aiIndex] && !this.messages[aiIndex].text) {
            this.messages[aiIndex].text = `**请求失败**：${msg}`
          }
        }
      } finally {
        this.loadingAssist = false
        if (this.assistStreamAbort === abortCtrl) {
          this.assistStreamAbort = null
        }
        if (this.messages[aiIndex]) {
          this.messages[aiIndex].streaming = false
        }
      }
      return true
    },
    setEnvCaptureFn(fn) {
      this._envCaptureFn = typeof fn === 'function' ? fn : null
    },
    setEnvEnsureCamFn(fn) {
      this._envEnsureCamFn = typeof fn === 'function' ? fn : null
    },
    async uploadEnvSnapshot(blob) {
      if (!blob) return ''
      const file = new File([blob], `env-${Date.now()}.jpg`, { type: 'image/jpeg' })
      const { data } = await uploadApi.image(file)
      return data?.url || ''
    },
    async runEnvCheck(snapshotUrlOrBlob = '') {
      if (!this.session?.id || this.envCheckRunning) return
      this.envCheckRunning = true
      try {
        let snapshotUrl = typeof snapshotUrlOrBlob === 'string' ? snapshotUrlOrBlob : ''
        if (snapshotUrlOrBlob instanceof Blob) {
          try {
            snapshotUrl = await this.uploadEnvSnapshot(snapshotUrlOrBlob)
          } catch {
            snapshotUrl = ''
          }
        }
        const payload = snapshotUrl ? { snapshotUrl } : {}
        const { data } = await sessionApi.envCheck(this.session.id, payload)
        this.envLevel = data.level
        this.envHint = briefEnvSummary(data.summary)
        this.envSuggestion = data.suggestion || ''
        const now = new Date()
        const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        const createdAt = now.toISOString()
        this.envLogs.unshift({
          time,
          createdAt,
          level: data.level,
          summary: data.summary,
          suggestion: data.suggestion || '',
          snapshotUrl: data.snapshotUrl || snapshotUrl || ''
        })
        if (this.envLogs.length > 18) this.envLogs.pop()
        this.session = (await sessionApi.get(this.session.id)).data
      } finally {
        this.envCheckRunning = false
      }
    },
    async runEnvCheckWithCapture() {
      let blob = null
      if (this._envCaptureFn) {
        try {
          if (this._envEnsureCamFn) {
            await this._envEnsureCamFn()
          }
          blob = await this._envCaptureFn()
        } catch {
          blob = null
        }
      }
      if (!blob) return
      await this.runEnvCheck(blob)
    },
    startEnvTimer() {
      this.stopEnvTimer()
      if (!this.envCheckEnabled) return
      this.envTimer = setInterval(() => this.runEnvCheckWithCapture(), 60000)
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
      if (report && (!report.envLogs?.length) && this.envLogs.length) {
        report.envLogs = this.envLogs.map((l, i) => ({
          id: i,
          level: l.level,
          summary: l.summary,
          suggestion: l.suggestion || '',
          snapshotUrl: l.snapshotUrl || '',
          createdAt: l.createdAt || l.time
        }))
      }
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
