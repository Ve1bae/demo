import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const devServerPort = new URL(baseURL).port || '5173'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
    headless: true,
    launchOptions: {
      args: [
        '--autoplay-policy=no-user-gesture-required',
        '--no-sandbox'
      ]
    }
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${devServerPort}`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  }
})
