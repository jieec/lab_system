// 自动截图 + 控制台错误检查（管理员视角 + 学生视角）
// 用法：cd lab-frontend && node scripts/screenshots.mjs
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'

const BASE = 'http://127.0.0.1:5173'
const OUT = path.resolve('../docs/screenshots')
fs.mkdirSync(OUT, { recursive: true })

const errors = []

// 优先用 Playwright 自带的 Chromium；若未执行过 `npx playwright install chromium`，
// 自动回退到本机已安装的 Chrome / Edge，保证换一台机器也能直接出图。
async function launchBrowser() {
  try {
    return await chromium.launch()
  } catch (err) {
    const candidates = [
      process.env.PW_EXECUTABLE_PATH,
      'C:/Program Files/Google/Chrome/Application/chrome.exe',
      'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
      'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
      'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
    ].filter(Boolean)
    for (const executablePath of candidates) {
      if (fs.existsSync(executablePath)) {
        console.log(`Playwright 自带浏览器不可用，改用本机浏览器：${executablePath}`)
        return await chromium.launch({ executablePath })
      }
    }
    throw err
  }
}

const browser = await launchBrowser()
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
const page = await context.newPage()

page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`[console] ${msg.text()}`)
})
page.on('pageerror', (err) => errors.push(`[pageerror] ${err.message}`))

async function shot(name, wait = 1100) {
  await page.waitForTimeout(wait)
  await page.screenshot({ path: path.join(OUT, `${name}.png`) })
  console.log(`  saved ${name}.png`)
}

async function login(username, password = '123456') {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入账号"]', username)
  await page.fill('input[placeholder="请输入密码"]', password)
  await page.click('button:has-text("登 录")')
  await page.waitForURL('**/dashboard', { timeout: 15000 })
  await page.waitForTimeout(2000)
}

console.log('1. 登录页')
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await shot('01-login')

console.log('2. 管理员登录')
await login('admin')

console.log('3. 数据看板（长图）')
await page.setViewportSize({ width: 1440, height: 1420 })
await page.waitForTimeout(1500)
await shot('02-dashboard', 800)
await page.setViewportSize({ width: 1440, height: 1000 })

const adminRoutes = [
  ['/labs', '03-labs'],
  ['/calendar', '04-calendar'],
  ['/approval', '06-approval'],
  ['/equipments', '07-equipments'],
  ['/repairs', '08-repairs'],
  ['/users', '09-users'],
  ['/notices', '10-notices'],
  ['/profile', '11-profile']
]

for (const [route, name] of adminRoutes) {
  console.log(`4. ${route}`)
  await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle' })
  await shot(name)
}

// 工单详情抽屉（管理员视角有全部工单）
console.log('5. 工单详情抽屉')
await page.goto(`${BASE}/repairs`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
const detailBtn = page.locator('button:has-text("详情")').first()
if (await detailBtn.count()) {
  await detailBtn.click()
  await shot('13-repair-detail', 1400)
  await page.keyboard.press('Escape')
}

console.log('6. 切换到学生视角')
await page.evaluate(() => localStorage.clear())
await login('s2021001')

await page.goto(`${BASE}/labs`, { waitUntil: 'networkidle' })
await shot('03b-labs-student', 900)

console.log('7. 预约弹窗')
const reserveBtn = page.locator('button:has-text("立即预约")').first()
if (await reserveBtn.count()) {
  await reserveBtn.click()
  await page.waitForTimeout(700)
  const dateInput = page.locator('.el-dialog input[placeholder="选择日期"]').first()
  if (await dateInput.count()) {
    await dateInput.click()
    await page.waitForTimeout(500)
    const cells = page.locator('.el-picker-panel td.available')
    const n = await cells.count()
    if (n > 0) {
      await cells.nth(Math.min(3, n - 1)).click()
      await page.waitForTimeout(1000)
    }
  }
  await shot('12-reserve-dialog', 900)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
}

console.log('8. 我的预约（学生）')
await page.goto(`${BASE}/my-reservations`, { waitUntil: 'networkidle' })
await shot('05-my-reservations')

console.log('9. 报修工单（学生视角）')
await page.goto(`${BASE}/repairs`, { waitUntil: 'networkidle' })
await shot('08b-repairs-student')

console.log('10. 站内信（学生视角）')
await page.goto(`${BASE}/messages`, { waitUntil: 'networkidle' })
await shot('14-messages')

console.log('11. 切回管理员：闭馆安排与操作审计日志')
await page.evaluate(() => localStorage.clear())
await login('admin')
await page.goto(`${BASE}/closures`, { waitUntil: 'networkidle' })
await shot('15-closures')
await page.goto(`${BASE}/logs`, { waitUntil: 'networkidle' })
await shot('16-logs')

await browser.close()

console.log('\n控制台错误：', errors.length)
errors.slice(0, 20).forEach((e) => console.log('  ' + e))
if (errors.length) process.exitCode = 1
