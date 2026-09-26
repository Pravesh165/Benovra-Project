import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies API calls to the Spring backend on :8080, so the
// browser only ever talks to one origin (no CORS needed). The production build
// is emitted straight into Spring's static/ folder, so `./mvnw spring-boot:run`
// serves the UI and the API together on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
