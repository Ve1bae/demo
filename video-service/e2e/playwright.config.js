const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  // 被测服务地址，CI 里通过环境变量覆盖
  // 默认 8082 端口，即本机启动的 video-service
  testDir: './tests',
  timeout: 60_000,
  fullyParallel: false,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:8082',
    // 端到端链路里默认用户身份；越权用例会单独覆盖为其他用户
    extraHTTPHeaders: {
      'X-User-Id': '10',
    },
  },
});
