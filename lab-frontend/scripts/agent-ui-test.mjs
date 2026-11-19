// Isolated UI check with explicit fake API data. Does not access MySQL or a paid model.
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

let browser
try { browser = await chromium.launch() }
catch (error) {
  const executablePath = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => fs.existsSync(p))
  if (!executablePath) throw error
  browser = await chromium.launch({ executablePath })
}
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    localStorage.setItem('token', 'isolated-ui-test-token')
    localStorage.setItem('user', JSON.stringify({ id: 7, role: 'STUDENT', realName: '界面测试用户' }))
  })
  let confirmationCount = 0
  const frames = [
    ['session', { sessionId: 'ui-test-session' }],
    ['mode', { mode: 'rules', message: '规则演示模式（隔离界面测试数据）' }],
    ['tool', { name: 'search_labs', state: 'success', result: [{ labId: 1, name: '测试数据库实验室' }] }],
    ['action', { id: 'ui-test-action', tool: 'prepare_reservation', details: { 操作: '提交预约', 实验室: '测试数据库实验室（LAB-TEST）', 开始时间: '2026-11-18 14:00:00', 结束时间: '2026-11-18 16:00:00', 人数: 4, 用途: '数据库实验' } }],
    ['delta', { text: '已生成预约确认卡片，尚未提交。' }],
    ['done', { success: true }]
  ]
  await page.route('http://127.0.0.1:5173/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/agent/chat') {
      assert.equal(route.request().headers().authorization, 'Bearer isolated-ui-test-token')
      return route.fulfill({ contentType: 'text/event-stream', body: frames.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join('') })
    }
    let data = { records: [], total: 0 }
    if (url.pathname === '/api/agent/status') data = { configured: false, mode: 'rules', message: '隔离界面测试，无模型调用' }
    if (url.pathname === '/api/agent/confirm') {
      confirmationCount++
      assert.deepEqual(route.request().postDataJSON(), { sessionId: 'ui-test-session', actionId: 'ui-test-action' })
      data = { message: '预约提交成功（测试数据）', orderNo: 'RS-UI-TEST', path: '/my-reservations' }
    }
    if (url.pathname.includes('unread')) data = 0
    if (url.pathname === '/api/stats/dashboard') data = {}
    if (url.pathname.endsWith('/all')) data = []
    await route.fulfill({ json: { code: 200, msg: '成功', data } })
  })
  await page.goto('http://127.0.0.1:5173/my-reservations', { waitUntil: 'networkidle' })
  console.log('UI page:', page.url(), 'page errors:', errors)
  await page.screenshot({ path: path.resolve('../lab-backend/target/agent-ui-initial.png') })
  await page.getByRole('button', { name: '打开实验室智能助手' }).click()
  await page.getByRole('textbox', { name: '给助手发送消息' }).fill('预约 明天 14:00-16:00 4人 用途：数据库实验')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确认执行', exact: true })
  await confirm.waitFor({ state: 'visible' })
  assert.equal(confirmationCount, 0)
  await page.locator('.agent-text').filter({ hasText: '尚未提交' }).waitFor()
  await page.screenshot({ path: path.resolve('../lab-backend/target/agent-ui-desktop.png') })
  await confirm.click()
  await page.getByRole('button', { name: '已确认执行', exact: true }).waitFor()
  assert.equal(confirmationCount, 1)
  assert.equal(await page.getByRole('button', { name: '已确认执行' }).isDisabled(), true)
  await page.setViewportSize({ width: 390, height: 844 })
  const bounds = await page.locator('.agent-panel').boundingBox()
  assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390)
  await page.screenshot({ path: path.resolve('../lab-backend/target/agent-ui-mobile.png') })
  assert.deepEqual(errors, [])
  console.log('PASS: assistant renders, SSE/card flow works, writes require confirmation, mobile panel fits; mock API only.')
} finally { await browser.close() }
