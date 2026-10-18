/** MKS-NHY 牛顿环 — 仪器控件联动 CCD 视场 */
const LAMBDA_MM = 0.0005893
const TRUE_R_MM = 874
const RING_CENTER_READING = 10

const optics = {
  eyepiece: 48,
  objectiveLift: 16,
  drumMain: 10,
  drumFine: 0,
  lampPos: 34,
  mirror: 38,
  mirrorFolded: false
}

const records = new Map()
const foundParts = new Set()
let currentView = 'front'
let activePartId = ''
let lastDrum = 4
let movedBackward = false
let measureDrumSynced = false

const sampleD = {
  5: 3.212, 6: 3.514, 7: 3.797, 8: 4.058, 9: 4.307,
  10: 4.538, 11: 4.763, 12: 4.973, 13: 5.177, 14: 5.368, 15: 5.557
}

const parts = {
  front: [
    { id: 'eyepiece', name: '目镜 (1)', x: 39, y: 14, title: '目镜视度调节', body: '旋转目镜使十字分划板清晰。示教时取下目镜安装 CCD 固定座。' },
    { id: 'ccd', name: 'CCD 相机', x: 39, y: 18, title: 'CCD 示教系统', body: '2100 万像素，HDMI/USB 输出，监视器显示视场。' },
    { id: 'focus', name: '调焦手轮 (13)', x: 28, y: 34, title: '物镜升降 / 调焦', body: '升降范围 40 mm，最小读数 0.1 mm。镜筒向上找环纹，严禁下压。' },
    { id: 'drum', name: '测微鼓轮 (15)', x: 10, y: 52, title: '测微鼓轮', body: '主尺 0–50 mm + 鼓轮 0.01 mm。十字丝固定，环纹随鼓轮横向移动。' },
    { id: 'mirror', name: '半反镜 (9)', x: 38, y: 54, title: '45° 半反镜组', body: '钠光反射到牛顿环。微调使视场亮度均匀。' },
    { id: 'sample', name: '牛顿环 (11)', x: 44, y: 70, title: '牛顿环 + 压片', body: '中心接触点对准物镜下方，压片压紧。' },
    { id: 'lamp', name: '钠光灯', x: 76, y: 30, title: '钠光灯位置', body: '水平照射半反镜。位置影响视场明暗分布。λ = 589.3 nm。' }
  ],
  angle: [
    { id: 'eyepiece', name: '目镜 / CCD', x: 32, y: 10, title: 'CCD + 变焦镜头', body: '2.8–12 mm CS 镜头，到目镜出光面约 5–7 mm。' },
    { id: 'focus', name: '调焦 (13)', x: 50, y: 30, title: '调焦手轮', body: '控制物镜升降，影响环纹清晰度。' },
    { id: 'drum', name: '鼓轮 (15)', x: 70, y: 60, title: '横向测微鼓轮', body: '沿导轨移动显微镜，记录暗环左右读数。' },
    { id: 'sample', name: '牛顿环', x: 38, y: 74, title: '牛顿环夹座', body: '勿触摸光学面，勿随意拧压紧螺丝。' },
    { id: 'lamp', name: '钠光灯', x: 76, y: 24, title: '钠光灯 + 电源', body: '调节钠灯位置保证足够光强。' }
  ]
}

const requiredParts = new Set(['eyepiece', 'ccd', 'focus', 'drum', 'mirror', 'sample', 'lamp'])

const $ = (s) => document.querySelector(s)
const $$ = (s) => Array.from(document.querySelectorAll(s))

function ringDiameter(order) {
  return Math.sqrt(4 * order * LAMBDA_MM * TRUE_R_MM)
}

function drumReading() {
  return optics.drumMain + optics.drumFine
}

function readingLabel(v) {
  const main = Math.floor(Math.max(0, v))
  const drum = Math.round((v - main) * 100)
  return `主尺 ${main} mm + 鼓轮 ${String(drum).padStart(2, '0')} = ${v.toFixed(2)} mm`
}

function reticleQuality() {
  return 1 - Math.min(1, Math.abs(optics.eyepiece - 78) / 42)
}

function focusQuality() {
  return 1 - Math.min(1, Math.abs(optics.objectiveLift - 28) / 14)
}

function mirrorQuality() {
  return 1 - Math.min(1, Math.abs(optics.mirror - 45) / 20)
}

function lampQuality() {
  return 1 - Math.min(1, Math.abs(optics.lampPos - 52) / 38)
}

function overallQuality() {
  const fold = optics.mirrorFolded ? 1 : 0.45
  return Math.max(
    0.04,
    reticleQuality() * 0.22 +
    focusQuality() * 0.32 +
    mirrorQuality() * 0.18 +
    lampQuality() * 0.18 +
    fold * 0.1
  )
}

function ringOffsetMm(reading = drumReading()) {
  return -(reading - RING_CENTER_READING)
}

function drawField(ctx, w, h, { reading = drumReading(), highlightOrder = null } = {}) {
  const q = overallQuality()
  const fq = focusQuality()
  const rq = reticleQuality()
  const mq = mirrorQuality()
  const lq = lampQuality()
  const offsetPx = ringOffsetMm(reading) * (52 * w / 760)
  const cx = w / 2 + offsetPx
  const cy = h / 2

  ctx.fillStyle = '#07101d'
  ctx.fillRect(0, 0, w, h)

  const lampShift = (optics.lampPos - 52) * (w / 760) * 4.5
  const bg = ctx.createRadialGradient(cx + lampShift, cy - 18, 12, cx, cy, w * 0.58)
  bg.addColorStop(0, `rgba(255, 220, 110, ${0.12 + lq * 0.22 + mq * 0.08})`)
  bg.addColorStop(0.45, `rgba(28, 72, 118, ${0.2 + mq * 0.15})`)
  bg.addColorStop(1, '#07101d')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)

  const glareX = cx + (optics.mirror - 45) * (w / 760) * 8 + lampShift * 0.6
  const glare = ctx.createRadialGradient(glareX, cy - 24, 6, glareX, cy - 24, w * 0.38)
  glare.addColorStop(0, `rgba(255, 245, 190, ${Math.max(0, lq * mq - 0.15) * 0.85})`)
  glare.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = glare
  ctx.fillRect(0, 0, w, h)

  const blur = (1 - fq) * 7 * (w / 760)
  ctx.save()
  ctx.translate(cx, cy)
  ctx.filter = `blur(${blur}px)`
  const rs = (35 * w) / 760
  for (let i = 1; i <= 16; i += 1) {
    const r = Math.sqrt(i) * rs
    ctx.beginPath()
    ctx.arc(0, 0, r, 0, Math.PI * 2)
    const alpha = 0.16 + q * 0.7
    ctx.strokeStyle = i % 2 === 0
      ? `rgba(255, 220, 100, ${alpha})`
      : `rgba(18, 38, 62, ${0.48 + q * 0.42})`
    ctx.lineWidth = Math.max(2, 6 * w / 760 - (1 - fq) * 2)
    ctx.stroke()
  }
  ctx.filter = 'none'
  ctx.beginPath()
  ctx.arc(0, 0, 12 * w / 760, 0, Math.PI * 2)
  ctx.fillStyle = `rgba(5, 12, 22, ${0.55 + q * 0.38})`
  ctx.fill()
  ctx.restore()

  if (highlightOrder != null) {
    const hr = (ringDiameter(highlightOrder) / 2) * (52 * w / 760)
    ctx.beginPath()
    ctx.arc(cx, cy, hr, 0, Math.PI * 2)
    ctx.strokeStyle = 'rgba(20, 111, 168, 0.92)'
    ctx.lineWidth = 3
    ctx.stroke()
  }

  const reticleAlpha = 0.25 + rq * 0.65
  const reticleBlur = (1 - rq) * 2.5
  ctx.save()
  ctx.filter = `blur(${reticleBlur}px)`
  ctx.strokeStyle = `rgba(220, 238, 255, ${reticleAlpha})`
  ctx.lineWidth = Math.max(1, 1.6 * w / 760)
  ctx.beginPath()
  ctx.moveTo(w / 2, 28 * h / 520)
  ctx.lineTo(w / 2, h - 28 * h / 520)
  ctx.moveTo(28 * w / 760, h / 2)
  ctx.lineTo(w - 28 * w / 760, h / 2)
  ctx.stroke()
  ctx.restore()
}

function syncReadouts() {
  const reading = drumReading()
  const main = Math.floor(reading)
  const fine = Math.round((reading - main) * 100)
  if ($('#eyepieceOut')) $('#eyepieceOut').textContent = String(Math.round(optics.eyepiece))
  if ($('#liftOut')) $('#liftOut').textContent = `${optics.objectiveLift.toFixed(1)} mm`
  if ($('#drumOut')) $('#drumOut').textContent = `${reading.toFixed(2)} mm`
  if ($('#lampOut')) $('#lampOut').textContent = String(Math.round(optics.lampPos))
  if ($('#mainScale')) $('#mainScale').textContent = String(main)
  if ($('#fineScale')) $('#fineScale').textContent = String(fine).padStart(2, '0')
  if ($('#liftScale')) $('#liftScale').textContent = optics.objectiveLift.toFixed(1)
  if ($('#lampScale')) $('#lampScale').textContent = String(Math.round(optics.lampPos))
  if ($('#mirrorValue')) $('#mirrorValue').textContent = `${optics.mirror}°`
}

function syncMeasureDrumFromOptics() {
  const reading = drumReading()
  const el = $('#measureDrum')
  if (el && !measureDrumSynced) {
    el.value = reading
    measureDrumSynced = true
  }
}

function applyMeasureDrum(v) {
  const reading = Number(v)
  optics.drumMain = Math.floor(reading)
  optics.drumFine = Math.round((reading - optics.drumMain) * 100) / 100
  if ($('#alignDrum')) $('#alignDrum').value = reading
  syncReadouts()
}

function drawScope() {
  const canvas = $('#scopeCanvas')
  if (!canvas) return
  drawField(canvas.getContext('2d'), canvas.width, canvas.height)
  syncReadouts()

  const issues = []
  if (reticleQuality() < 0.72) issues.push('十字丝不够清晰，先旋节目镜。')
  if (focusQuality() < 0.72) issues.push('环纹模糊，继续转动调焦手轮（物镜升降）。')
  if (mirrorQuality() < 0.72) issues.push('半反镜角度需微调。')
  if (lampQuality() < 0.72) issues.push('钠光灯位置不合适，视场明暗不均。')
  if (Math.abs(ringOffsetMm()) > 0.35) issues.push('鼓轮读数偏离环心，继续平移。')
  if (!optics.mirrorFolded) issues.push('底座反光镜未收起。')

  const fb = $('#alignFeedback')
  const st = $('#scopeStatus')
  if (issues.length === 0) {
    fb.className = 'feedback good'
    fb.innerHTML = '<strong>视场合格</strong><p>十字丝清晰、环纹清楚、照明均匀、环心居中。</p>'
    st.textContent = '可测量'
    const cb = document.querySelector('[data-ready="align"]')
    if (cb) cb.checked = true
  } else {
    fb.className = issues.length > 2 ? 'feedback bad' : 'feedback warn'
    fb.innerHTML = `<strong>继续调节</strong><p>${issues.join(' ')}</p>`
    st.textContent = '调节中'
    document.querySelector('[data-ready="align"]').checked = false
  }
  updateProgress()
}

function drawMeasure() {
  const canvas = $('#measureCanvas')
  if (!canvas) return
  const order = Number($('#ring')?.value ?? 10)
  const reading = Number($('#measureDrum')?.value ?? drumReading())
  drawField(canvas.getContext('2d'), canvas.width, canvas.height, { reading, highlightOrder: order })

  const main = Math.floor(reading)
  const fine = Math.round((reading - main) * 100)
  if ($('#measureMain')) $('#measureMain').textContent = String(main)
  if ($('#measureFine')) $('#measureFine').textContent = String(fine).padStart(2, '0')
  if ($('#measureDrumOut')) $('#measureDrumOut').textContent = `${reading.toFixed(2)} mm`
  if ($('#ringValue')) $('#ringValue').textContent = order
  if ($('#readout')) $('#readout').textContent = readingLabel(reading)

  const idealL = RING_CENTER_READING - ringDiameter(order) / 2
  const idealR = RING_CENTER_READING + ringDiameter(order) / 2
  const err = Math.min(Math.abs(reading - idealL), Math.abs(reading - idealR))
  const fb = $('#measureFeedback')
  if (movedBackward) {
    fb.className = 'feedback bad'
    fb.innerHTML = '<strong>回程误差</strong><p>鼓轮已反向，应退回较远处再同向逼近。</p>'
  } else if (err < 0.04) {
    fb.className = 'feedback good'
    fb.innerHTML = '<strong>相切</strong><p>竖叉丝与目标暗环相切，可记录读数。</p>'
  } else {
    fb.className = 'feedback warn'
    fb.innerHTML = '<strong>微调鼓轮</strong><p>继续同向转动，使暗环与中心竖叉丝相切。</p>'
  }
}

function refreshViews() {
  drawScope()
  drawMeasure()
}

function switchSection(name) {
  $$('[data-section-panel]').forEach((p) => p.classList.toggle('active', p.id === name))
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.section === name))
  if (name === 'align') drawScope()
  if (name === 'measure') {
    if ($('#measureDrum')) $('#measureDrum').value = drumReading()
    drawMeasure()
  }
}

function renderPartList() {
  const list = parts[currentView] || []
  $('#partList').innerHTML = list.map((p) => `
    <button class="part-chip${foundParts.has(p.id) ? ' done' : ''}${activePartId === p.id ? ' active' : ''}" type="button" data-part="${p.id}">${p.name}</button>
  `).join('')
  $$('.part-chip').forEach((b) => b.addEventListener('click', () => selectPart(b.dataset.part)))
}

function selectPart(id) {
  const part = (parts[currentView] || []).find((p) => p.id === id)
  if (!part) return
  activePartId = id
  foundParts.add(id)
  $('#partTitle').textContent = part.title
  $('#partBody').textContent = part.body
  const m = $('#photoMarker')
  m.style.left = `${part.x}%`
  m.style.top = `${part.y}%`
  m.classList.add('show')
  renderPartList()
  if ([...requiredParts].filter((k) => (parts.front || []).some((p) => p.id === k)).every((k) => foundParts.has(k))) {
    document.querySelector('[data-ready="scene"]').checked = true
  }
  updateProgress()
}

function switchPhoto(view) {
  currentView = view
  activePartId = ''
  const img = $('#instrumentPhoto')
  if (img) {
    img.src = view === 'front' ? './real-instrument-front.png' : './real-instrument-angle.png'
  }
  $('#photoMarker')?.classList.remove('show')
  $$('[data-view]').forEach((b) => b.classList.toggle('active', b.dataset.view === view))
  renderPartList()
}

function recordSide(side) {
  const order = Number($('#ring').value)
  const reading = Number($('#measureDrum').value)
  const ideal = side === 'left'
    ? RING_CENTER_READING - ringDiameter(order) / 2
    : RING_CENTER_READING + ringDiameter(order) / 2
  if (Math.abs(reading - ideal) > 0.06) {
    $('#measureFeedback').className = 'feedback bad'
    $('#measureFeedback').innerHTML = `<strong>未对准</strong><p>距第 ${order} 级相切点 ${Math.abs(reading - ideal).toFixed(2)} mm。</p>`
    return
  }
  const row = records.get(order) || { order }
  row[side] = Number(reading.toFixed(2))
  if (row.left != null && row.right != null) {
    row.d = Math.abs(row.right - row.left)
    row.d2 = row.d ** 2
  }
  records.set(order, row)
  renderTable()
  fillSelects()
  if (row.d) document.querySelector('[data-ready="measure"]').checked = true
  updateProgress()
}

function renderTable() {
  const rows = [...records.values()].sort((a, b) => a.order - b.order)
  $('#dataRows').innerHTML = rows.length
    ? rows.map((r) => `<tr><td>第 ${r.order} 级</td><td>${r.left ?? '—'}</td><td>${r.right ?? '—'}</td><td>${r.d?.toFixed(3) ?? '—'}</td><td>${r.d2?.toFixed(3) ?? '—'}</td></tr>`).join('')
    : '<tr><td colspan="5">选择暗环级数，同向转动鼓轮分别记录左右相切读数。</td></tr>'
}

function fillSelects() {
  const opts = Array.from({ length: 11 }, (_, i) => i + 5).map((n) => `<option value="${n}">${n}</option>`).join('')
  const m = $('#mSelect'), n = $('#nSelect')
  const om = m?.value || '10', on = n?.value || '5'
  if (m) { m.innerHTML = opts; m.value = om }
  if (n) { n.innerHTML = opts; n.value = on }
  syncCalcInputs()
}

function syncCalcInputs() {
  const mr = records.get(Number($('#mSelect')?.value))
  const nr = records.get(Number($('#nSelect')?.value))
  if (mr?.d) $('#dm').value = mr.d.toFixed(3)
  if (nr?.d) $('#dn').value = nr.d.toFixed(3)
}

function loadSample() {
  records.clear()
  Object.entries(sampleD).forEach(([k, d]) => {
    const order = Number(k)
    records.set(order, {
      order,
      left: Number((RING_CENTER_READING - d / 2).toFixed(3)),
      right: Number((RING_CENTER_READING + d / 2).toFixed(3)),
      d, d2: d * d
    })
  })
  renderTable()
  fillSelects()
}

function calculate(e) {
  e.preventDefault()
  const m = Number($('#mSelect').value), n = Number($('#nSelect').value)
  const dm = Number($('#dm').value), dn = Number($('#dn').value)
  const card = $('#resultCard')
  if (!dm || !dn || m === n) {
    card.innerHTML = '<h3>数据不完整</h3><p>选择不同环级并填入直径。</p>'
    return
  }
  const hi = m > n ? { o: m, d: dm } : { o: n, d: dn }
  const lo = m > n ? { o: n, d: dn } : { o: m, d: dm }
  const delta = hi.o - lo.o
  const diff = hi.d ** 2 - lo.d ** 2
  const rMm = diff / (4 * delta * LAMBDA_MM)
  const ok = delta >= 5 && rMm / 1000 > 0.2 && rMm / 1000 < 2.5
  card.innerHTML = `<h3>${ok ? '结果合理' : '需复核'}</h3>
    <p>D<sub>${hi.o}</sub>²−D<sub>${lo.o}</sub>² = <strong>${diff.toFixed(3)} mm²</strong></p>
    <p>R = <strong>${(rMm / 1000).toFixed(3)} m</strong></p>
    <p>${delta < 5 ? '|m−n| < 5。' : '与标称 R ≈ 0.87 m 接近。'}</p>`
  if (ok) document.querySelector('[data-ready="calc"]').checked = true
  updateProgress()
}

function updateProgress() {
  const done = $$('[data-ready]').filter((c) => c.checked).length
  $('#progressText').textContent = `${done}/5`
  const safe = ['lensSafe', 'focusSafe', 'backlashSafe'].every((id) => document.getElementById(id)?.checked)
  const box = $('#readyBox')
  if (!box) return
  if (done === 5 && safe) {
    box.className = 'ready-box good'
    box.innerHTML = '<h3>可以进入真实实验</h3><p>你已掌握仪器装调、成像、读数与计算全流程。</p>'
  } else {
    box.className = 'ready-box'
    box.innerHTML = `<h3>预习 ${done}/5</h3><p>继续完成各阶段并勾选安全项。</p>`
  }
}

function setDemoAlign() {
  optics.eyepiece = 78
  optics.objectiveLift = 28
  optics.drumMain = 10
  optics.drumFine = 0
  optics.lampPos = 52
  optics.mirror = 45
  optics.mirrorFolded = true
  $('#eyepiece').value = optics.eyepiece
  $('#objectiveLift').value = optics.objectiveLift
  $('#alignDrum').value = 10
  $('#lampPos').value = optics.lampPos
  $('#mirror').value = optics.mirror
  $('#mirrorFolded').checked = true
  refreshViews()
}

function bindOptics() {
  $('#eyepiece')?.addEventListener('input', (e) => {
    optics.eyepiece = Number(e.target.value)
    refreshViews()
  })
  $('#objectiveLift')?.addEventListener('input', (e) => {
    optics.objectiveLift = Number(e.target.value)
    refreshViews()
  })
  $('#alignDrum')?.addEventListener('input', (e) => {
    applyMeasureDrum(Number(e.target.value))
    refreshViews()
    if ($('#measureDrum')) $('#measureDrum').value = drumReading()
  })
  $('#lampPos')?.addEventListener('input', (e) => {
    optics.lampPos = Number(e.target.value)
    refreshViews()
  })
  $('#mirror')?.addEventListener('input', (e) => {
    optics.mirror = Number(e.target.value)
    refreshViews()
  })
  $('#mirrorFolded')?.addEventListener('change', (e) => {
    optics.mirrorFolded = e.target.checked
    refreshViews()
  })
  $('#demoAlign')?.addEventListener('click', setDemoAlign)

  $('#measureDrum')?.addEventListener('input', (e) => {
    const v = Number(e.target.value)
    if (v < lastDrum - 0.001) movedBackward = true
    lastDrum = v
    applyMeasureDrum(v)
    drawMeasure()
  })
  $('#ring')?.addEventListener('input', () => { drawMeasure(); syncCalcInputs() })
  $('#recordLeft')?.addEventListener('click', () => recordSide('left'))
  $('#recordRight')?.addEventListener('click', () => recordSide('right'))
  $('#restartMeasure')?.addEventListener('click', () => {
    const start = RING_CENTER_READING - ringDiameter(Number($('#ring').value)) / 2 - 1.2
    $('#measureDrum').value = start
    lastDrum = start
    movedBackward = false
    applyMeasureDrum(start)
    drawMeasure()
  })
}

function bind() {
  $$('.tab').forEach((b) => b.addEventListener('click', () => switchSection(b.dataset.section)))
  $$('[data-view]').forEach((b) => b.addEventListener('click', () => switchPhoto(b.dataset.view)))
  $$('[data-ready], #lensSafe, #focusSafe, #backlashSafe').forEach((el) => el.addEventListener('change', updateProgress))
  bindOptics()
  $('#sampleData')?.addEventListener('click', loadSample)
  $('#calcForm')?.addEventListener('submit', calculate)
}

function init() {
  bind()
  renderPartList()
  renderTable()
  fillSelects()
  syncReadouts()
  refreshViews()
  updateProgress()
}

init()
