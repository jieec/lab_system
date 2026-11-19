import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

// Load the production parser with only axios excluded; test the real public stream function.
const source = (await readFile(new URL('../src/api/agent.js', import.meta.url), 'utf8')).replace("import request from './request'", 'const request = {}')
const { streamAgent } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
globalThis.localStorage = { getItem: () => 'test-jwt', removeItem() {} }

test('SSE parser handles UTF-8 and arbitrary byte boundaries, multiple frames and CRLF', async () => {
  const bytes = new TextEncoder().encode('event:delta\r\ndata:{"text":"实验室"}\r\n\r\nevent:done\ndata:{"success":true}\n\n')
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/agent/chat')
    assert.equal(options.headers.Authorization, 'Bearer test-jwt')
    return new Response(new ReadableStream({ start(controller) {
      for (let i = 0; i < bytes.length; i++) controller.enqueue(bytes.slice(i, i + 1))
      controller.close()
    } }), { headers: { 'content-type': 'text/event-stream' } })
  }
  const events = []
  await streamAgent({ message: '我的预约' }, (event, data) => events.push([event, data]))
  assert.deepEqual(events, [['delta', { text: '实验室' }], ['done', { success: true }]])
})

test('unexpected end is rejected instead of reporting a completed request', async () => {
  globalThis.fetch = async () => new Response('event:delta\ndata:{"text":"部分结果"}\n\n', { headers: { 'content-type': 'text/event-stream' } })
  await assert.rejects(streamAgent({}, () => {}), /连接中断/)
})

test('business validation errors returned as JSON are surfaced to the user', async () => {
  globalThis.fetch = async () => Response.json({ code: 429, msg: '本轮对话尚未结束' })
  await assert.rejects(streamAgent({}, () => {}), /本轮对话尚未结束/)
})
