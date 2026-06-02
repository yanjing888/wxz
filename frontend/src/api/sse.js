function parseSseBlock(block, handlers) {
  let event = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += (data ? '\n' : '') + line.slice(5).trim()
  }
  if (!data) return

  let payload
  try {
    payload = JSON.parse(data)
  } catch {
    payload = data
  }

  if (event === 'chunk') {
    handlers.onChunk?.(typeof payload === 'string' ? payload : payload.text || '')
  } else if (event === 'start') {
    handlers.onStart?.(payload)
  } else if (event === 'marks') {
    const marks = Array.isArray(payload) ? payload : payload.marks
    if (marks?.length) handlers.onMarks?.(marks)
  } else if (event === 'done') {
    handlers.onDone?.(payload)
  } else if (event === 'error') {
    const msg = typeof payload === 'string' ? payload : payload.message || '流式请求失败'
    handlers.onError?.(msg)
  }
}

export async function postSse(url, body, handlers = {}, signal) {
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(body),
    signal
  })

  if (!res.ok) {
    const text = await res.text()
    let message = text
    try {
      const json = JSON.parse(text)
      message = json.message || text
    } catch {
      /* keep text */
    }
    handlers.onError?.(message)
    throw new Error(message)
  }

  const reader = res.body?.getReader()
  if (!reader) throw new Error('浏览器不支持流式响应')

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''
    for (const part of parts) {
      if (part.trim()) parseSseBlock(part, handlers)
    }
  }

  if (buffer.trim()) parseSseBlock(buffer, handlers)
}
