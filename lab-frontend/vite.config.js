import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 按需引入后：首屏 index 约 715 KB（含 40 个 Element Plus 组件），
    // 看板页懒加载的 ECharts 单块约 528 KB。两者都已是可接受的实测最小值。
    // 不用 manualChunks 强制分组：实测会破坏 Rollup 的跨模块 tree-shaking，
    // 把 element-plus 撑到 822 KB，总 JS 反而多出约 400 KB。
    chunkSizeWarningLimit: 750
  }
})
