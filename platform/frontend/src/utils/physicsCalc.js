/** 大学物理实验常用数据处理（本地计算，不依赖 AI） */

export function parseNumbers(text) {
  if (!text || !String(text).trim()) return []
  return String(text)
    .split(/[\s,，;；\n\t]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => Number(s.replace(/[^\d.eE+\-]/g, '')))
    .filter((n) => Number.isFinite(n))
}

export function calcBasicStats(values) {
  const n = values.length
  if (n === 0) return null
  const mean = values.reduce((a, b) => a + b, 0) / n
  if (n === 1) {
    return { n, mean, std: 0, uA: 0, min: values[0], max: values[0] }
  }
  const variance = values.reduce((sum, v) => sum + (v - mean) ** 2, 0) / (n - 1)
  const std = Math.sqrt(variance)
  const uA = std / Math.sqrt(n)
  return {
    n,
    mean,
    std,
    uA,
    min: Math.min(...values),
    max: Math.max(...values)
  }
}

/** B 类不确定度：仪器误差按均匀分布 uB = a / √3 */
export function calcInstrumentUB(instrumentError) {
  const raw = String(instrumentError || '').trim()
  if (!raw) return 0
  const match = raw.match(/[\d.]+/)
  if (!match) return 0
  const a = Number(match[0])
  if (!Number.isFinite(a) || a <= 0) return 0
  return a / Math.sqrt(3)
}

export function calcCombinedUncertainty(uA, uB) {
  return Math.sqrt(uA * uA + uB * uB)
}

export function formatUncertaintyResult(mean, uCombined, unit = '') {
  if (!Number.isFinite(mean) || !Number.isFinite(uCombined)) return '—'
  const u = uCombined
  let decimals = 2
  if (u > 0) {
    const exp = Math.floor(Math.log10(u))
    const scale = 10 ** -exp
    const uRounded = Math.round(u * scale) / scale
    decimals = Math.max(0, -Math.floor(Math.log10(uRounded)) + 1)
  }
  const meanStr = mean.toFixed(decimals)
  const uStr = u.toFixed(decimals)
  const rel = mean !== 0 ? ((u / Math.abs(mean)) * 100).toFixed(2) : '—'
  const suffix = unit ? ` ${unit}` : ''
  return {
    formatted: `${meanStr} ± ${uStr}${suffix}`,
    meanStr,
    uStr,
    relativePercent: rel,
    decimals
  }
}

export function linearFit(xs, ys) {
  const n = Math.min(xs.length, ys.length)
  if (n < 2) return null
  const x = xs.slice(0, n)
  const y = ys.slice(0, n)
  const xMean = x.reduce((a, b) => a + b, 0) / n
  const yMean = y.reduce((a, b) => a + b, 0) / n
  let num = 0
  let den = 0
  for (let i = 0; i < n; i++) {
    num += (x[i] - xMean) * (y[i] - yMean)
    den += (x[i] - xMean) ** 2
  }
  if (den === 0) return null
  const slope = num / den
  const intercept = yMean - slope * xMean
  let ssRes = 0
  let ssTot = 0
  for (let i = 0; i < n; i++) {
    const pred = slope * x[i] + intercept
    ssRes += (y[i] - pred) ** 2
    ssTot += (y[i] - yMean) ** 2
  }
  const r2 = ssTot > 0 ? 1 - ssRes / ssTot : 1
  return { slope, intercept, r2, n }
}

/** 逐差法：等间隔测量列按半分组求差，抵消系统漂移，适用于弹簧伸长、光栅位移等 */
export function successiveDifference(values, interval = 1) {
  const n = values.length
  if (n < 4 || n % 2 !== 0) {
    return { ok: false, reason: '逐差法要求偶数个（≥4）等间隔测量值' }
  }
  const half = n / 2
  const diffs = []
  for (let i = 0; i < half; i += 1) {
    diffs.push({
      pair: `x${i + half + 1} - x${i + 1}`,
      value: values[i + half] - values[i]
    })
  }
  const diffValues = diffs.map((d) => d.value)
  const meanDiff = diffValues.reduce((a, b) => a + b, 0) / half
  const stats = calcBasicStats(diffValues)
  const stepValue = meanDiff / (half * interval)
  return {
    ok: true,
    half,
    diffs,
    meanDiff,
    stepValue,
    stats,
    formula: `Δ̄ = (Σ(x_{i+${half}} − x_i)) / ${half} = ${meanDiff.toFixed(4)}`
  }
}

export function buildSuccessiveParagraph(result, unit = '', label = '测量量') {
  if (!result?.ok) return ''
  const u = unit ? ` ${unit}` : ''
  const lines = [
    `对${label}的 ${result.half * 2} 个等间隔测量值采用逐差法处理，分为前后两组两两作差：`,
    result.diffs.map((d) => `  ${d.pair} = ${d.value.toFixed(4)}${u}`).join('\n'),
    `逐差平均值 Δ̄ = ${result.meanDiff.toFixed(4)}${u}，`,
    `每间隔的平均增量为 Δ̄/${result.half} = ${result.stepValue.toFixed(4)}${u}。`
  ]
  if (result.stats && result.stats.n > 1) {
    lines.push(`各差值的标准差 s = ${result.stats.std.toFixed(4)}${u}，A 类不确定度 u_A = ${result.stats.uA.toFixed(4)}${u}。`)
  }
  lines.push('逐差法充分利用了全部数据，并可有效抵消线性变化的系统误差。')
  return lines.join('\n')
}

/** 统计一个数值字符串的有效数字位数 */
export function countSigFigs(raw) {
  const s = String(raw || '').trim().replace(/[+\-]/g, '')
  if (!s || !/\d/.test(s)) return 0
  const [mantissa] = s.split(/[eE]/)
  if (mantissa.includes('.')) {
    const stripped = mantissa.replace('.', '').replace(/^0+/, '')
    return stripped.length || 1
  }
  const stripped = mantissa.replace(/^0+/, '')
  return stripped.replace(/0+$/, '').length || (stripped.length ? 1 : 0)
}

/** 按有效数字位数修约（四舍六入五成双的简化实现，超出常规误差范围时退回四舍五入） */
export function roundToSigFigs(value, figures) {
  if (!Number.isFinite(value) || value === 0) return 0
  const n = Math.max(1, Math.floor(figures))
  const exp = Math.floor(Math.log10(Math.abs(value)))
  const scale = 10 ** (n - 1 - exp)
  return Math.round(value * scale) / scale
}

/** 不确定度修约：首位为 1 或 2 时保留两位，否则保留一位 */
export function roundUncertainty(u) {
  if (!Number.isFinite(u) || u <= 0) return { value: 0, figures: 1, decimals: 0 }
  const exp = Math.floor(Math.log10(u))
  const lead = Math.floor(u / 10 ** exp)
  const figures = lead <= 2 ? 2 : 1
  const scale = 10 ** (figures - 1 - exp)
  const value = Math.ceil(u * scale) / scale
  return { value, figures, decimals: Math.max(0, figures - 1 - exp) }
}

const GRUBBS_005 = {
  3: 1.153, 4: 1.463, 5: 1.672, 6: 1.822, 7: 1.938, 8: 2.032, 9: 2.11, 10: 2.176,
  11: 2.234, 12: 2.285, 13: 2.331, 14: 2.371, 15: 2.409, 16: 2.443, 17: 2.475,
  18: 2.504, 19: 2.532, 20: 2.557
}

/** 离群值检测：n≤20 用 Grubbs（α=0.05），更大样本用 3σ 拉依达准则 */
export function detectOutliers(values) {
  const stats = calcBasicStats(values)
  if (!stats || stats.n < 3 || stats.std === 0) return { method: '', outliers: [] }
  const useGrubbs = stats.n <= 20
  const threshold = useGrubbs ? GRUBBS_005[stats.n] : 3
  const outliers = []
  values.forEach((v, i) => {
    const g = Math.abs(v - stats.mean) / stats.std
    if (g > threshold) {
      outliers.push({ index: i + 1, value: v, g: Number(g.toFixed(3)) })
    }
  })
  return {
    method: useGrubbs ? `Grubbs 检验（n=${stats.n}, α=0.05, G₀=${threshold}）` : '拉依达 3σ 准则',
    threshold,
    outliers
  }
}

/**
 * 数据体检：对一组原始测量值做本地检查，返回可直接展示的条目清单。
 * 无 AI 时也必须有完整输出。
 */
export function checkDataHealth(rawText, options = {}) {
  const tokens = String(rawText || '')
    .split(/[\s,，;；\n\t]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  const values = parseNumbers(rawText)
  const stats = calcBasicStats(values)
  const checks = []

  if (values.length === 0) {
    return { values, stats: null, checks: [{ level: 'error', title: '未识别到数值', detail: '请检查输入格式，用逗号或换行分隔各次测量值。' }] }
  }

  if (values.length < 3) {
    checks.push({
      level: 'warn',
      title: '测量次数偏少',
      detail: `当前仅 ${values.length} 次测量。大学物理实验一般要求重复测量 5-10 次，否则 A 类不确定度不可靠。`
    })
  } else {
    checks.push({ level: 'ok', title: '测量次数', detail: `共 ${values.length} 次重复测量，满足统计处理要求。` })
  }

  const cv = stats.mean !== 0 ? Math.abs(stats.std / stats.mean) : Infinity
  if (Number.isFinite(cv)) {
    if (cv > 0.1) {
      checks.push({
        level: 'error',
        title: '数据离散度过大',
        detail: `变异系数 CV = s/x̄ = ${(cv * 100).toFixed(2)}%，远超一般实验的 5%。可能存在读数错误、仪器未调平或操作不稳定。`
      })
    } else if (cv > 0.05) {
      checks.push({
        level: 'warn',
        title: '数据离散度偏大',
        detail: `变异系数 CV = ${(cv * 100).toFixed(2)}%，建议复查测量条件是否一致。`
      })
    } else {
      checks.push({ level: 'ok', title: '数据离散度', detail: `变异系数 CV = ${(cv * 100).toFixed(2)}%，重复性良好。` })
    }
  }

  const outlierInfo = detectOutliers(values)
  if (outlierInfo.outliers.length) {
    checks.push({
      level: 'error',
      title: '存在可疑离群值',
      detail: `${outlierInfo.method} 判定第 ${outlierInfo.outliers.map((o) => o.index).join('、')} 个数据（${outlierInfo.outliers.map((o) => o.value).join('、')}）为离群值，应复测或说明剔除依据。`
    })
  } else if (outlierInfo.method) {
    checks.push({ level: 'ok', title: '离群值检验', detail: `${outlierInfo.method} 未发现离群值。` })
  }

  const figs = tokens.map(countSigFigs).filter((f) => f > 0)
  const uniqueFigs = [...new Set(figs)]
  if (uniqueFigs.length > 1) {
    checks.push({
      level: 'warn',
      title: '有效数字位数不一致',
      detail: `各次读数分别为 ${uniqueFigs.sort().join('、')} 位有效数字。同一仪器的重复测量应保留相同位数（含估读位），请检查是否漏记末位 0。`
    })
  } else if (uniqueFigs.length === 1) {
    checks.push({ level: 'ok', title: '有效数字', detail: `各次读数均为 ${uniqueFigs[0]} 位有效数字，记录规范。` })
  }

  const precision = Number(String(options.precision || '').match(/[\d.]+/)?.[0])
  if (Number.isFinite(precision) && precision > 0) {
    const decimalsNeeded = Math.max(0, -Math.floor(Math.log10(precision)) + 1)
    const badPrecision = tokens.filter((t) => {
      const dot = t.indexOf('.')
      const d = dot >= 0 ? t.length - dot - 1 : 0
      return d < decimalsNeeded - 1
    })
    if (badPrecision.length) {
      checks.push({
        level: 'warn',
        title: '未按仪器精度估读',
        detail: `仪器分度值为 ${precision}，读数应记到小数点后 ${decimalsNeeded} 位（含 1 位估读）。以下读数位数不足：${badPrecision.join('、')}。`
      })
    } else {
      checks.push({ level: 'ok', title: '估读位', detail: `读数位数与分度值 ${precision} 相符。` })
    }
  }

  const expected = Number(options.expectedValue)
  if (Number.isFinite(expected) && expected !== 0) {
    const deviation = ((stats.mean - expected) / Math.abs(expected)) * 100
    const level = Math.abs(deviation) > 10 ? 'error' : Math.abs(deviation) > 5 ? 'warn' : 'ok'
    checks.push({
      level,
      title: '与参考值的偏差',
      detail: `平均值 ${stats.mean.toFixed(4)} 相对参考值 ${expected} 的相对偏差为 ${deviation.toFixed(2)}%。`
        + (level === 'ok' ? '在合理范围内。' : '建议用「误差溯源」查找原因。')
    })
  }

  return { values, stats, checks, outlierInfo, cv }
}

export function buildUncertaintyParagraph(stats, uB, uCombined, unit, label) {
  if (!stats) return ''
  const result = formatUncertaintyResult(stats.mean, uCombined, unit)
  const lines = [
    `对${label || '测量量'}共 ${stats.n} 次测量，算术平均值为 ${stats.mean.toFixed(4)}${unit ? ' ' + unit : ''}。`,
    `A 类不确定度 u_A = s/√n = ${stats.std.toFixed(4)}/√${stats.n} = ${stats.uA.toFixed(4)}${unit ? ' ' + unit : ''}。`
  ]
  if (uB > 0) {
    lines.push(`B 类不确定度 u_B = ${uB.toFixed(4)}${unit ? ' ' + unit : ''}（由仪器示值误差估计）。`)
  }
  lines.push(
    `合成不确定度 u_c = √(u_A² + u_B²) = ${uCombined.toFixed(4)}${unit ? ' ' + unit : ''}。`,
    `相对不确定度 u_r = u_c/|x̄| × 100% ≈ ${result.relativePercent}%。`,
    `测量结果表示为：${result.formatted}。`
  )
  return lines.join('\n')
}
