import { defineStore } from 'pinia'
import { experimentApi, feedbackApi, sessionApi, studentFileApi, systemApi, uploadApi } from '../api'
import { sniffImageMime, readFileAsDataUrl } from '../utils/imageFile'
import { blobToFile, composeMarkedImage } from '../utils/markedImage'
import { buildComposerDataAttachment, formatDataValuesSummary } from '../utils/composerData'

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

const WELCOME_MESSAGE = `<div class="welcome-guide">
  <p class="welcome-guide-hello">你好，我是你的实验台助教。</p>
  <p class="welcome-guide-title">卡住就直接问我，或拍一张现场照片——不用先选工具。</p>
  <div class="welcome-guide-row"><strong>操作</strong><span>描述现象或拍照，我告诉你下一步怎么做。</span></div>
  <div class="welcome-guide-row"><strong>读数</strong><span>左侧可「拍照获取读数」，确认后再记入。</span></div>
  <div class="welcome-guide-row"><strong>数据</strong><span>进「数据核验」看提交记录、纠错结果和补测提示。</span></div>
  <p class="welcome-guide-foot">你也可以点上方推荐问题开始。</p>
</div>`

const DIFY_STATUS_UNAVAILABLE_INTERVAL = 2 * 60 * 1000
const DIFY_STATUS_AVAILABLE_INTERVAL = 5 * 60 * 1000
const DIFY_STATUS_CRITICAL_STALE_MS = 60 * 1000

function welcomeChatMessage() {
  return { role: 'ai', text: WELCOME_MESSAGE, localWelcome: true, ts: 0 }
}

function mapChatMessage(message) {
  const imageUrl = message?.imageUrl || ''
  const isAi = message?.role !== 'user'
  return {
    id: message?.id || null,
    role: isAi ? 'ai' : 'user',
    text: message?.text || '',
    image: imageUrl,
    imageFallback: imageUrl,
    annotated: isAi && !!imageUrl,
    stepId: message?.stepId,
    feedbackRating: message?.feedbackRating || '',
    ts: message?.createdAt ? Date.parse(message.createdAt) || Date.now() : Date.now()
  }
}

function emitAppAlert(title, message = '') {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('wxz-app-alert', {
    detail: { title, message }
  }))
}

function ellipsis(text, maxLen) {
  const normalized = String(text || '').trim()
  if (normalized.length <= maxLen) return normalized
  return `${normalized.slice(0, maxLen - 1)}…`
}

function compactQuestion(text) {
  let plain = String(text || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#*_>`[\]()]/g, '')
    .replace(/https?:\/\/\S+/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) return ''
  if (plain.startsWith('【数据提交】')) return '数据提交纠错'
  if (plain.startsWith('【仪器采集】')) return '仪器采集纠错'
  if (plain === '请分析上传的实验图片。' || plain === '请分析上传的实验图片') return '实验图片分析'
  plain = plain.replace(/^(请问|老师|物小智|我想问一下|想问一下|帮我看看|请帮我|请|帮我)/, '').trim()
  const stops = ['？', '?', '。', '；', ';', '\n']
    .map((char) => plain.indexOf(char))
    .filter((index) => index >= 0)
  if (stops.length) {
    plain = plain.slice(0, Math.min(...stops)).trim()
  }
  return ellipsis(plain, 12)
}

function buildHistoryTitleFromMessages(messages = []) {
  const parts = []
  for (const msg of messages) {
    if (msg?.role !== 'user') continue
    const title = compactQuestion(msg.text)
    if (title && !parts.includes(title)) parts.push(title)
    if (parts.length >= 3) break
  }
  return ellipsis(parts.join('、'), 24)
}

export const useLabStore = defineStore('lab', {
  state: () => ({
    experiments: [],
    experiment: null,
    session: null,
    sessionHistory: [],
    sessionHistoryLoading: false,
    activeStep: 1,
    composerImageUrl: '',
    composerImagePreview: '',
    composerDataAttachment: null,
    composerDataLoading: false,
    messages: [],
    envCheckEnabled: false,
    envLevel: 'L0',
    envHint: '暂无异常',
    envSuggestion: '',
    envLogs: [],
    envCheckRunning: false,
    benchCamera: null,
    difyStatus: null,
    difyStatusLoading: false,
    difyStatusCheckedAt: 0,
    difyStatusTimer: null,
    _difyStatusPromise: null,
    envTimer: null,
    _envCaptureFn: null,
    _envEnsureCamFn: null,
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
    /** 当前步骤是否支持从仪器读取数据到对话框 */
    canReadDeviceData(state) {
      if (!state.experiment?.dataCollection?.enabled) return false
      const step = state.experiment?.steps?.[String(state.activeStep)]
      return step?.dataSource === 'device'
    },
    canCaptureCcdImage(state) {
      return state.experiment?.code === 'newton_rings'
    },
    composerDataReady(state) {
      return !!state.composerDataAttachment?.values
        && Object.keys(state.composerDataAttachment.values).length > 0
    },
    /** 仪器读数按钮是否处于忙碌态 */
    deviceReadBusy(state) {
      return state.composerDataLoading
        || state.deviceConnecting
        || state.deviceReading
        || state.deviceState === 'acquiring'
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
    },
    envCheckDifyStatus(state) {
      return state.difyStatus?.workflowStatuses?.['env-check'] || null
    },
    /** 图片上传区：始终可用，所有步骤都允许拍照求助 */
    useVisionCorrection() {
      return true
    },
    envCheckAvailable() {
      return this.envCheckDifyStatus?.available === true
    },
    envCaptureFn() {
      return this._envCaptureFn
    },
    envEnsureCamFn() {
      return this._envEnsureCamFn
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
    async loadSessionHistory(experimentCode = '') {
      this.sessionHistoryLoading = true
      try {
        const params = experimentCode ? { experimentCode } : {}
        const { data } = await sessionApi.list(params)
        this.sessionHistory = data || []
        this.updateCurrentSessionHistoryTitle()
        await this.hydrateMissingHistoryTitles()
        return this.sessionHistory
      } finally {
        this.sessionHistoryLoading = false
      }
    },
    async hydrateMissingHistoryTitles() {
      const missing = this.sessionHistory
        .filter((item) => item?.id && !item.historyTitle)
        .slice(0, 20)
      if (!missing.length) return

      const titles = await Promise.all(missing.map(async (item) => {
        if (item.id === this.session?.id) {
          return [item.id, buildHistoryTitleFromMessages(this.messages)]
        }
        try {
          const { data } = await sessionApi.messages(item.id)
          return [item.id, buildHistoryTitleFromMessages((data || []).map(mapChatMessage))]
        } catch {
          return [item.id, '']
        }
      }))

      const titleById = new Map(titles.filter(([, title]) => title))
      if (!titleById.size) return
      this.sessionHistory = this.sessionHistory.map((item) => {
        const title = titleById.get(item.id)
        return title ? { ...item, historyTitle: title } : item
      })
    },
    updateCurrentSessionHistoryTitle() {
      if (!this.session?.id) return
      const title = buildHistoryTitleFromMessages(this.messages)
      if (!title) return

      const current = { ...this.session, historyTitle: title }
      const index = this.sessionHistory.findIndex((item) => item.id === this.session.id)
      if (index >= 0) {
        this.sessionHistory = this.sessionHistory.map((item, i) =>
          i === index ? { ...item, historyTitle: item.historyTitle || title } : item
        )
        return
      }

      const sameExperiment = !this.experiment?.code || current.experimentCode === this.experiment.code
      if (sameExperiment) {
        this.sessionHistory = [current, ...this.sessionHistory]
      }
    },
    async getLatestActiveSession(experimentCode) {
      try {
        const { data } = await sessionApi.latest(experimentCode)
        return data || null
      } catch (e) {
        if (e.response?.status === 404) return null
        throw e
      }
    },
    async loadBenchCamera() {
      try {
        const { data } = await systemApi.benchCamera()
        this.benchCamera = data || null
      } catch {
        this.benchCamera = null
      }
    },
    async loadDifyStatus({ silent = false } = {}) {
      if (this._difyStatusPromise) return this._difyStatusPromise
      if (!silent) this.difyStatusLoading = true
      this._difyStatusPromise = (async () => {
        try {
          const { data } = await systemApi.difyStatus()
          this.difyStatus = data || null
        } catch {
          this.difyStatus = null
        } finally {
          this.difyStatusCheckedAt = Date.now()
          if (!silent) this.difyStatusLoading = false
          this._difyStatusPromise = null
          this.applyEnvDifyStatus()
        }
        return this.difyStatus
      })()
      return this._difyStatusPromise
    },
    async refreshDifyStatusIfStale(maxAgeMs = DIFY_STATUS_CRITICAL_STALE_MS) {
      if (Date.now() - this.difyStatusCheckedAt <= maxAgeMs) return this.difyStatus
      return this.loadDifyStatus({ silent: true })
    },
    startDifyStatusTimer() {
      this.stopDifyStatusTimer()
      if (typeof document !== 'undefined' && document.hidden) return
      const available = this.difyStatus?.available === true
      const delay = available ? DIFY_STATUS_AVAILABLE_INTERVAL : DIFY_STATUS_UNAVAILABLE_INTERVAL
      this.difyStatusTimer = setTimeout(async () => {
        await this.loadDifyStatus({ silent: true })
        this.startDifyStatusTimer()
      }, delay)
    },
    stopDifyStatusTimer() {
      if (this.difyStatusTimer) {
        clearTimeout(this.difyStatusTimer)
        this.difyStatusTimer = null
      }
    },
    applyEnvDifyStatus() {
      if (this.envCheckAvailable) {
        if (this.envLevel === 'NA') {
          this.envLevel = 'L0'
          this.envHint = '暂无异常'
        }
        return
      }
      this.stopEnvTimer()
      this.envCheckEnabled = false
      this.envLevel = 'NA'
      this.envHint = 'Dify 安全监测服务不可用'
      this.envSuggestion = ''
    },
    async switchExperiment(experimentCode) {
      const code = (experimentCode || '').trim()
      if (!code || code === this.experiment?.code) return false

      this.switchingExperiment = true
      this.stopEnvTimer()
      try {
        localStorage.setItem('wxz_exp', code)
        const name = localStorage.getItem('wxz_displayName') || this.session?.studentName || '学生'
        await this.loadExperiment(code)
        await this.startSession(code, name, '')
        this.startEnvTimer()
        return true
      } catch (e) {
        throw e
      } finally {
        this.switchingExperiment = false
      }
    },
    async startSession(experimentCode, studentName, studentClass = '') {
      this.stopEnvTimer()
      const { data } = await sessionApi.start({ experimentCode, studentName, studentClass })
      this.session = data
      this.activeStep = 1
      this.resetLabUi()
      await this.loadSessionData()
      this.messages = [welcomeChatMessage()]
      if (this.useDeviceData) {
        await this.prepareDeviceStep()
      }
      this.loadSessionHistory(experimentCode).catch(() => {})
    },
    async resumeSession(sessionOrId) {
      this.stopEnvTimer()
      const session = typeof sessionOrId === 'object'
        ? sessionOrId
        : (await sessionApi.get(sessionOrId)).data
      if (!session?.id) return false

      if (session.experimentCode && session.experimentCode !== this.experiment?.code) {
        await this.loadExperiment(session.experimentCode)
        localStorage.setItem('wxz_exp', session.experimentCode)
      }

      this.resetLabUi()
      this.session = session
      this.activeStep = session.activeStep || 1
      await this.loadSessionData()
      await this.loadMessages()
      if (this.useDeviceData && session.status === 'ACTIVE') {
        await this.prepareDeviceStep()
      }
      this.loadSessionHistory(session.experimentCode || '').catch(() => {})
      return true
    },
    async loadMessages() {
      if (!this.session?.id) return
      try {
        const { data } = await sessionApi.messages(this.session.id)
        this.messages = [welcomeChatMessage(), ...(data || []).map(mapChatMessage)]
      } catch {
        this.messages = [welcomeChatMessage()]
      }
    },
    resetLabUi() {
      this.composerImageUrl = ''
      this.composerImagePreview = ''
      this.composerDataAttachment = null
      this.composerDataLoading = false
      this.messages = []
      this.envLogs = []
      this.envCheckEnabled = false
      this.envLevel = 'L0'
      this.envHint = '暂无异常'
      this.envSuggestion = ''
      this.tutViewCount = 0
      this.sessionDataByStep = {}
      this.dataSubmitErrors = []
      this.dataPanelOverride = {}
      this.teardownDevice()
      this.applyEnvDifyStatus()
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
      this.clearComposerDataAttachment()
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
    async submitStepData(values, fromDevice = false, extraMessage = '') {
      if (!this.session?.id || this.submittingData) return false
      this.submittingData = true
      this.dataSubmitErrors = []

      const stepId = this.activeStep
      const stepTitle = this.stepConfig?.title || ''
      const lines = formatDataValuesSummary(this.currentDataFields, values || {})
      const summary = lines.join('，')
      const prefix = fromDevice ? '【仪器采集】' : '【数据提交】'
      let text = `${prefix}${stepTitle}\n${summary || '(空)'}`
      const note = (extraMessage || '').trim()
      if (note) text += `\n\n${note}`
      this.pushUser(text)
      this.updateCurrentSessionHistoryTitle()

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
        this.loadSessionHistory(this.experiment?.code || '').catch(() => {})
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
     * 上传图片到对话框附件区。
     */
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
      this.clearComposerDataAttachment()

      const dataUrl = await readFileAsDataUrl(originalFile)
      this.composerImagePreview = dataUrl

      try {
        const { data } = await uploadApi.image(originalFile)
        if (seq !== this.uploadSeq) return

        this.composerImageUrl = data.url
      } catch (e) {
        if (seq !== this.uploadSeq) return

        this.composerImageUrl = ''
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
      if (!usedInChat && this.composerImagePreview !== url) {
        URL.revokeObjectURL(url)
      }
    },
    clearComposerImage() {
      this.revokeBlobIfUnused(this.composerImagePreview)
      this.composerImageUrl = ''
      this.composerImagePreview = ''
      this.uploadError = ''
    },
    clearComposerDataAttachment() {
      this.composerDataAttachment = null
    },
    /** 读数助手确认后的读数：放入对话附件，由学生发送后才入库 */
    applyPhotoReadingToComposer({ value, instrumentLabel, instrumentKey, targetFieldKey } = {}) {
      const reading = String(value || '').trim()
      if (!reading) return false
      const fields = this.currentDataFields || []
      const target = fields.find((f) => f.key === targetFieldKey)
        || fields.find((f) => /读数|reading|直径|diameter/i.test(`${f.label || ''} ${f.key || ''}`))
        || fields.find((f) => f.required !== false)
        || fields[0]
      const values = target
        ? { [target.key]: reading }
        : { reading }
      this.composerDataAttachment = buildComposerDataAttachment({
        stepId: this.activeStep,
        stepTitle: this.stepConfig?.title || instrumentLabel || '拍照获取读数',
        fields: target
          ? fields
          : [{ key: 'reading', label: instrumentLabel || '读数', unit: '' }],
        values,
        fromDevice: false
      })
      if (this.composerDataAttachment) {
        this.composerDataAttachment.label = '拍照获取读数'
        this.composerDataAttachment.title = `${target?.label || instrumentLabel || instrumentKey || '读数'}：${reading}${target?.unit || ''}`
      }
      this.clearComposerImage()
      return true
    },
    async loadDeviceDataIntoComposer() {
      if (!this.session?.id || this.composerDataLoading || this.deviceReadBusy) return false

      if (!this.canReadDeviceData) {
        emitAppAlert(
          '当前步骤无需仪器读数',
          '请切换到需要采集数据的实验步骤（如暗环直径测量）后再读取；也可直接在右侧对话框提问。'
        )
        return false
      }

      this.composerDataLoading = true
      this.dataSubmitErrors = []

      try {
        if (!this.deviceConnected) {
          await this.connectDevice()
        }

        if (this.deviceType === 'universal_tester') {
          if (!this.deviceHasSubmitData) {
            await this.startDeviceAcquisition()
          }
        } else {
          await this.readDeviceOnce(false)
        }

        const values = { ...(this.deviceSnapshot || {}) }
        if (!Object.keys(values).length) {
          this.dataSubmitErrors = ['未读取到有效数据，请确认仪器已连接并重试']
          return false
        }

        this.composerDataAttachment = buildComposerDataAttachment({
          stepId: this.activeStep,
          stepTitle: this.stepConfig?.title || '',
          fields: this.currentDataFields,
          values,
          fromDevice: true
        })
        this.clearComposerImage()
        return true
      } catch (e) {
        const msg = e.response?.data?.message || e.message || '读取仪器数据失败'
        this.dataSubmitErrors = [msg]
        emitAppAlert('读取失败', msg)
        return false
      } finally {
        this.composerDataLoading = false
      }
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
    async archiveSessionFile({ url, category = 'photo', fileName = '', note = '' }) {
      const code = this.experiment?.code
      if (!code || !url?.startsWith('/uploads/')) return
      try {
        await studentFileApi.save({
          experimentCode: code,
          category,
          stage: 'lab',
          url,
          sessionId: this.session?.id,
          fileName: fileName || url.substring(url.lastIndexOf('/') + 1),
          note: note || `步骤「${this.stepConfig?.title || ''}」`
        })
      } catch {
        // 资料归档失败不阻断实验流程
      }
    },
    async attachAnnotatedImageToAiMessage(aiIndex, imageSrc, marks) {
      if (!marks?.length || !imageSrc || !this.messages[aiIndex]) return

      try {
        const blob = await composeMarkedImage(imageSrc, marks)
        if (!blob) return

        const file = blobToFile(blob)
        const { data } = await uploadApi.image(file)
        const url = data.url?.split('?')[0] || ''
        if (!url) return

        this.messages[aiIndex].image = url
        this.messages[aiIndex].imageFallback = url
        this.messages[aiIndex].annotated = true

        if (this.session?.id) {
          await sessionApi.attachLatestAiImage(this.session.id, { imageUrl: url })
        }
        this.archiveSessionFile({
          url,
          category: 'photo',
          fileName: `步骤${this.activeStep}-标注.jpg`,
          note: `步骤「${this.stepConfig?.title || ''}」AI 标注图`
        })
      } catch {
        // 标注图生成或保存失败时不阻断主流程
      }
    },
    async sendMessage(userMessage) {
      if (!this.session?.id || this.loadingAssist || this.uploadingImage || this.submittingData) return false
      if (this.session.status === 'FINISHED') return false

      if (this.composerDataAttachment) {
        const attachment = this.composerDataAttachment
        this.clearComposerDataAttachment()
        return this.submitStepData(
          attachment.values,
          attachment.fromDevice !== false,
          (userMessage || '').trim()
        )
      }

      await this.refreshDifyStatusIfStale()

      const text = (userMessage || '').trim()
      const imageUrl = this.readyImageUrl
      const hasPreview = !!this.composerImagePreview

      if (!text && !imageUrl) {
        if (hasPreview) {
          emitAppAlert(
            this.uploadError ? '图片上传失败' : '图片仍在上传',
            this.uploadError
              ? `图片上传失败：${this.uploadError}\n请重新选择图片后再发送。`
              : '图片尚未上传完成，请等待上传结束后再发送。'
          )
        }
        return false
      }

      this.cancelAssistStream()
      this.loadingAssist = true
      const abortCtrl = new AbortController()
      this.assistStreamAbort = abortCtrl
      const hadImage = !!imageUrl
      const displayImage = this.composerImagePreview || imageUrl
      const prompt = text || (hadImage ? '请分析上传的实验图片。' : '')

      this.pushUser(prompt, hadImage ? displayImage : '', hadImage ? imageUrl : '')
      this.updateCurrentSessionHistoryTitle()
      if (hadImage) {
        this.archiveSessionFile({
          url: imageUrl,
          category: 'photo',
          fileName: `步骤${this.activeStep}-照片.jpg`,
          note: `步骤「${this.stepConfig?.title || ''}」实验照片`
        })
      }
      this.clearComposerImage()

      const aiIndex = this.messages.length
      this.messages.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

      let pendingMarks = null
      const resolveMarks = (marks) => {
        if (hadImage && marks?.length) pendingMarks = marks
      }

      try {
        await sessionApi.assistStream(
          this.session.id,
          {
            userMessage: prompt,
            imageUrl: imageUrl || undefined,
            stepId: this.activeStep
          },
          {
            onMarks: resolveMarks,
            onChunk: (chunk) => {
              this.messages[aiIndex].text += chunk
            },
            onAnswerEnd: () => {
              if (this.messages[aiIndex]) {
                this.messages[aiIndex].streaming = false
              }
              this.loadingAssist = false
            },
            onDone: async (data) => {
              this.messages[aiIndex].streaming = false
              if (data.feedback) {
                this.messages[aiIndex].text = data.feedback
              }
              if (data.aiMessageId) {
                this.messages[aiIndex].id = data.aiMessageId
              }
              if (hadImage && data.marks?.length) pendingMarks = data.marks
              if (hadImage && pendingMarks?.length) {
                await this.attachAnnotatedImageToAiMessage(aiIndex, displayImage, pendingMarks)
              }
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
      if (!abortCtrl.signal.aborted && this.session?.id) {
        sessionApi.get(this.session.id).then(({ data }) => {
          this.session = data
          this.loadSessionHistory(this.experiment?.code || '').catch(() => {})
        }).catch(() => {})
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
      await this.refreshDifyStatusIfStale()
      if (!this.envCheckAvailable) {
        this.applyEnvDifyStatus()
        return
      }
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
      } catch (e) {
        const now = new Date()
        const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        const createdAt = now.toISOString()
        const reason = e.response?.data?.message || e.message || '请求失败'
        this.envLevel = 'NA'
        this.envHint = `巡检请求失败：${reason}`
        this.envLogs.unshift({
          time,
          createdAt,
          level: 'NA',
          summary: `巡检请求失败：${reason}`,
          suggestion: '',
          snapshotUrl: ''
        })
        if (this.envLogs.length > 18) this.envLogs.pop()
      } finally {
        this.envCheckRunning = false
      }
    },
    async loadEnvLogs() {
      if (!this.session?.id) return
      try {
        const { data } = await sessionApi.envLogs(this.session.id)
        this.envLogs = (data || []).map((log) => ({
          time: log.createdAt
            ? new Date(log.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
            : '',
          createdAt: log.createdAt || '',
          level: log.level || 'NA',
          summary: log.summary || '',
          suggestion: log.suggestion || '',
          snapshotUrl: log.snapshotUrl || ''
        }))
      } catch {
        // 加载失败不阻断功能
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
      if (!this.envCheckAvailable) {
        this.applyEnvDifyStatus()
        return
      }
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
      if (enabled && !this.envCheckAvailable) {
        this.applyEnvDifyStatus()
        return
      }
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
      this.loadSessionHistory(this.experiment?.code || '').catch(() => {})
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
    },
    async submitMessageFeedback(messageId, rating) {
      if (!this.session?.id || !messageId) return
      await feedbackApi.submit(this.session.id, messageId, rating)
      const msg = this.messages.find((m) => m.id === messageId)
      if (msg) msg.feedbackRating = rating
    }
  }
})
