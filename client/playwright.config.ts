import { defineConfig, devices } from '@playwright/test'
const externalBase = process.env.PLAYWRIGHT_BASE_URL
export default defineConfig({
  testDir: './e2e', fullyParallel: false, retries: 0, workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: externalBase || 'http://127.0.0.1:5173', trace: 'retain-on-failure' },
  projects: [{ name:'chromium',testIgnore:/cross-browser\.spec\.ts/,use:{...devices['Desktop Chrome']} }, ...(process.env.CROSS_BROWSER === '1' ? [{name:'firefox',testMatch:/cross-browser\.spec\.ts/,use:{...devices['Desktop Firefox']}},{name:'webkit',testMatch:/cross-browser\.spec\.ts/,use:{...devices['Desktop Safari']}}] : [])],
  webServer: externalBase ? undefined : { command:'npm run dev -- --port 5173 --strictPort', url:'http://127.0.0.1:5173', reuseExistingServer: process.env.LIVE_BACKEND === '1' || !process.env.CI, timeout:30000 },
})
