import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy /api to the Spring Boot backend so browser requests are
    // same-origin during local dev — sidesteps CORS/cookie complications
    // entirely for `npm run dev`. Production still needs the real CORS
    // config on the backend (see SecurityConfig.corsConfigurationSource)
    // since the deployed frontend and backend are genuinely different
    // origins (Vercel + Railway/Render).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
