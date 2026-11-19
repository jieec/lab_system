import request from './request'

export const agentApi = {
  status: () => request.get('/agent/status'),
  confirm: (data) => request.post('/agent/confirm', data),
  clear: (id) => request.delete(`/agent/sessions/${id}`)
}

export async function streamAgent(data, onEvent, signal) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token || ''}` },
    body: JSON.stringify(data), signal
  })
  if (!response.ok) throw new Error(`助手请求失败（${response.status}）`)
  if (!response.headers.get('content-type')?.includes('text/event-stream')) {
    const result = await response.json()
    if (result.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    throw new Error(result.msg || '助手响应异常')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', finished = false
  const dispatch = (frame) => {
    let name = 'message'
    const lines = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim()
      if (line.startsWith('data:')) lines.push(line.slice(5).trimStart())
    }
    if (!lines.length) return
    const value = JSON.parse(lines.join('\n'))
    onEvent(name, value)
    if (name === 'done' || name === 'error') finished = true
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      // A CRLF pair can be split between network chunks.
      buffer = buffer.replace(/\r\n/g, '\n')
      let index
      while ((index = buffer.indexOf('\n\n')) >= 0) {
        dispatch(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
      }
      if (done) {
        if (buffer.trim()) dispatch(buffer)
        break
      }
    }
    if (!finished) throw new Error('连接中断，请核对已有卡片或重新发送')
  } finally { reader.releaseLock() }
}
