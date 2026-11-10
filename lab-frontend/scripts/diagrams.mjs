// 渲染 mermaid 设计图并导出 PNG
// 用法：cd lab-frontend && node scripts/diagrams.mjs
import { chromium } from 'playwright'
import path from 'node:path'

const OUT = path.resolve('../docs/diagrams')
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1200, height: 900 }, deviceScaleFactor: 2 })

const errors = []
page.on('pageerror', (e) => errors.push(e.message))
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))

await page.goto('http://127.0.0.1:8899/diagrams.html', { waitUntil: 'networkidle' })
await page.waitForSelector('body[data-ready="1"]', { timeout: 20000 })
await page.waitForTimeout(800)

const figs = ['fig1', 'fig2', 'fig3', 'fig4', 'fig5', 'fig6']
for (const id of figs) {
  const el = page.locator(`#${id}`)
  await el.screenshot({ path: path.join(OUT, `${id}.png`) })
  console.log('saved', id)
}

await browser.close()
if (errors.length) {
  console.log('errors:', errors.slice(0, 5))
  process.exitCode = 1
} else {
  console.log('全部设计图渲染完成')
}
