import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Fail loudly if 5173 is taken, rather than silently picking 5174/5175/...
    // A silent port switch here breaks the backend's CORS allowlist
    // (APP_CORS_ALLOWED_ORIGIN defaults to http://localhost:5173 exactly)
    // in a way that's confusing to debug: the app "just" 403s with no
    // obvious cause, since nothing about the failure mentions ports.
    strictPort: true,
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
