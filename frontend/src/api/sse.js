function parseSseBlock(block, handlers) {
  let event = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trimStart()
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
  } else if (event === 'sample') {
    handlers.onSample?.(payload)
  } else if (event === 'status') {
    handlers.onStatus?.(payload)
  } else if (event === 'complete') {
    handlers.onComplete?.(payload)
  } else if (event === 'message' && payload && typeof payload === 'object' && payload.snapshot) {
    handlers.onComplete?.(payload)
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
    let chunk
    try {
      chunk = await reader.read()
    } catch (e) {
      if (signal?.aborted || e.name === 'AbortError') return
      throw e
    }
    const { done, value } = chunk
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''
    for (const part of parts) {
      if (part.trim()) parseSseBlock(part, handlers)
    }
  }

  if (buffer.trim()) parseSseBlock(buffer, handlers)
  handlers.onStreamEnd?.()
}

export async function getSse(url, handlers = {}, signal) {
  const res = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
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
    let chunk
    try {
      chunk = await reader.read()
    } catch (e) {
      if (signal?.aborted || e.name === 'AbortError') return
      throw e
    }
    const { done, value } = chunk
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''
    for (const part of parts) {
      if (part.trim()) parseSseBlock(part, handlers)
    }
  }

  if (buffer.trim()) parseSseBlock(buffer, handlers)
  handlers.onStreamEnd?.()
}
