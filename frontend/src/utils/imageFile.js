const MIME_EXT = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
  'image/bmp': '.bmp'
}

export async function sniffImageMime(file) {
  const header = new Uint8Array(await file.slice(0, 12).arrayBuffer())

  if (header.length >= 3 && header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff) {
    return 'image/jpeg'
  }
  if (header.length >= 8 && header[0] === 0x89 && header[1] === 0x50 && header[2] === 0x4e && header[3] === 0x47) {
    return 'image/png'
  }
  if (header.length >= 6 && header[0] === 0x47 && header[1] === 0x49 && header[2] === 0x46) {
    return 'image/gif'
  }
  if (
    header.length >= 12 &&
    header[0] === 0x52 &&
    header[1] === 0x49 &&
    header[2] === 0x46 &&
    header[3] === 0x46 &&
    header[8] === 0x57 &&
    header[9] === 0x45 &&
    header[10] === 0x42 &&
    header[11] === 0x50
  ) {
    return 'image/webp'
  }
  if (header.length >= 2 && header[0] === 0x42 && header[1] === 0x4d) {
    return 'image/bmp'
  }

  return (file.type || '').toLowerCase()
}

/** 修正扩展名/类型与实际内容不一致；用 buffer 重建 File，避免 FormData 上传空文件。 */
export async function normalizeImageFile(file) {
  const sniffed = await sniffImageMime(file)
  if (!sniffed || !MIME_EXT[sniffed]) return file

  const ext = MIME_EXT[sniffed]
  const base = (file.name || 'image').replace(/\.[^.]+$/, '') || 'image'
  const normalizedName = `${base}${ext}`

  if (file.type === sniffed && file.name.toLowerCase().endsWith(ext)) {
    return file
  }

  const buffer = await file.arrayBuffer()
  return new File([buffer], normalizedName, { type: sniffed, lastModified: file.lastModified })
}

export function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(reader.error || new Error('无法读取图片'))
    reader.readAsDataURL(file)
  })
}
