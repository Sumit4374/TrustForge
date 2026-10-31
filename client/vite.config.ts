import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'
export default defineConfig({
  plugins: [react(), babel({ presets: [reactCompilerPreset()] }), tailwindcss()],
  server: { host: '127.0.0.1', watch: { ignored: ['**/test-results/**', '**/playwright-report/**', '**/e2e/**'] }, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
  build: { sourcemap: false, chunkSizeWarningLimit: 750 },
})
