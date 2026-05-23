import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Real backend API.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Mock API placeholder. Can point to a dedicated mock server later.
      '/mock-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 默认把 mock 前缀回写到真实 API，便于本地无 mock server 时联调。
        rewrite: (path) => path.replace(/^\/mock-api/, '/api'),
      },
    },
  },
})
