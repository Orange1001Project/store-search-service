import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 서버(Nginx)와 같은 주소 규칙: /api는 API 서버(로컬 8080)로 넘긴다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
