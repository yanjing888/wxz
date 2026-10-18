function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    if (!src.startsWith('data:')) {
      img.crossOrigin = 'anonymous'
    }
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('无法加载图片'))
    img.src = src
  })
}

/**
 * 在原始图片上绘制 0–1000 归一化坐标的纠错框，返回 JPEG Blob。
 * @param {string} imageSrc data URL 或同源图片 URL
 * @param {Array<{x:number,y:number,w:number,h:number,n:number}>} marks
 * @returns {Promise<Blob|null>}
 */
export async function composeMarkedImage(imageSrc, marks) {
  if (!imageSrc || !marks?.length) return null

  const img = await loadImage(imageSrc)
  const canvas = document.createElement('canvas')
  canvas.width = img.naturalWidth
  canvas.height = img.naturalHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('无法创建画布')

  ctx.drawImage(img, 0, 0)
  const sx = img.naturalWidth / 1000
  const sy = img.naturalHeight / 1000
  const scale = Math.min(sx, sy)

  for (const m of marks) {
    const x = m.x * sx
    const y = m.y * sy
    const w = m.w * sx
    const h = m.h * sy
    const r = Math.max(10, Math.round(14 * scale))

    ctx.strokeStyle = '#ef4444'
    ctx.lineWidth = Math.max(2, Math.round(scale * 3))
    ctx.strokeRect(x, y, w, h)

    ctx.fillStyle = '#ef4444'
    ctx.beginPath()
    ctx.arc(x + r, y + r, r, 0, Math.PI * 2)
    ctx.fill()

    ctx.fillStyle = '#ffffff'
    ctx.font = `bold ${Math.max(11, Math.round(r * 0.85))}px sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(String(m.n ?? ''), x + r, y + r)
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('标注图生成失败'))),
      'image/jpeg',
      0.92
    )
  })
}

export function blobToFile(blob, name = 'annotated.jpg') {
  return new File([blob], name, { type: blob.type || 'image/jpeg' })
}
