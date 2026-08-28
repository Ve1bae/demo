import { test, expect } from '@playwright/test'
import { spawn } from 'node:child_process'
import http from 'node:http'

const API_BASE = process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api'
const WS_PATTERN = '**/ws/live/*'

function checkFlvStatus(url) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, (response) => {
      response.destroy()
      resolve(response.statusCode)
    })
    req.setTimeout(5000, () => {
      req.destroy()
      reject(new Error('flv request timed out'))
    })
    req.on('error', reject)
  })
}

async function createRoom(request) {
  const response = await request.post(`${API_BASE}/live/rooms`, {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': '1'
    },
    data: {
      title: 'Playwright 测试直播间',
      categoryId: 1,
      coverUrl: ''
    }
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  return body.data.roomId
}

async function seedLogin(page) {
  await page.addInitScript(() => {
    localStorage.setItem('loginUserNickname', '测试用户')
    localStorage.setItem('loginUser', '测试用户')
    localStorage.setItem('loginUserId', '1')
    localStorage.setItem('loginUserAvatar', '')
  })
}

async function mockLiveRoomDetail(page, roomId, overrides) {
  await page.route(`**/api/live/rooms/${roomId}`, async (route) => {
    const response = await route.fetch()
    const json = await response.json()
    json.data = { ...json.data, ...overrides }
    await route.fulfill({ response, json })
  })
}

test('详情接口异常时回退本地缓存', async ({ page, request }) => {
  const roomId = await createRoom(request)
  await seedLogin(page)

  await page.goto('/#/live')
  await page.locator('.video-card').first().waitFor()
  await page.goto(`/#/live/${roomId}`)
  await page.locator('.live-player-panel').waitFor()

  await page.route(`**/api/live/rooms/${roomId}`, (route) => route.abort())
  await page.reload()

  await page.locator('.live-player-panel').waitFor()
  await expect(page.locator('.live-player-panel')).toBeVisible()
})

test('pullUrl 为空时显示等待推流', async ({ page, request }) => {
  const roomId = await createRoom(request)
  await mockLiveRoomDetail(page, roomId, {
    pullUrl: '',
    qualityUrls: {}
  })

  await page.goto(`/#/live/${roomId}`)

  await expect(page.locator('.player-empty')).toHaveText('等待主播推流后即可观看')
})

test('切换清晰度会重新加载播放器', async ({ page, request }) => {
  const roomId = await createRoom(request)
  await mockLiveRoomDetail(page, roomId, {
    qualityUrls: {
      '原画': 'http://127.0.0.1:8081/live/test.flv',
      '720P': 'http://127.0.0.1:8081/live/test_720p.flv',
      '480P': 'http://127.0.0.1:8081/live/test_480p.flv'
    }
  })

  await page.goto(`/#/live/${roomId}`)
  await page.locator('.quality-btn').waitFor()
  await page.locator('.quality-btn').click()
  await page.locator('.live-quality-dropdown.show').waitFor()
  await page.locator('.live-quality-dropdown button', { hasText: '720P' }).click()

  await expect(page.locator('.quality-btn strong')).toHaveText('720P')
})

test('WebSocket 未连接时发送弹幕提示连接未建立', async ({ page, request }) => {
  const roomId = await createRoom(request)
  await seedLogin(page)
  await page.routeWebSocket(WS_PATTERN, (webSocket) => webSocket.close())

  await page.goto(`/#/live/${roomId}`)
  await page.locator('.live-chat-input input[type="text"]').waitFor()

  let dialogText = ''
  page.on('dialog', async (dialog) => {
    dialogText = dialog.message()
    await dialog.accept()
  })

  await page.fill('.live-chat-input input[type="text"]', '你好')
  await page.locator('.live-chat-input button').click()

  await expect.poll(() => dialogText).toContain('直播互动连接未建立')
})

test('WebSocket 未连接时点赞保持原数量并提示', async ({ page, request }) => {
  const roomId = await createRoom(request)
  await seedLogin(page)
  await page.routeWebSocket(WS_PATTERN, (webSocket) => webSocket.close())

  await page.goto(`/#/live/${roomId}`)
  await page.locator('.live-like-btn').waitFor()

  const before = await page.locator('.live-like-btn').innerText()
  let dialogText = ''
  page.on('dialog', async (dialog) => {
    dialogText = dialog.message()
    await dialog.accept()
  })

  await page.locator('.live-like-btn').click()

  await expect.poll(() => dialogText).toContain('直播互动连接未建立')
  await expect(page.locator('.live-like-btn')).toHaveText(before)
})

test('未登录发送弹幕弹出登录弹窗', async ({ page, request }) => {
  const roomId = await createRoom(request)

  await page.goto(`/#/live/${roomId}`)
  await page.locator('.live-chat-input input[type="text"]').click()

  await expect(page.locator('.modal-overlay')).toBeVisible()
  await expect(page.locator('.modal-overlay h2')).toHaveText('欢迎来到航音')
})

test('主播通过页面创建直播间并看到推流信息', async ({ page, request }) => {
  await seedLogin(page)
  await page.goto('/#/live')
  const uploadButton = page.locator('.upload-btn')
  await uploadButton.waitFor({ state: 'visible' })
  await expect(uploadButton).toHaveText('开始直播')
  await expect(uploadButton).toBeEnabled()
  await uploadButton.click()
  await expect(page.locator('.live-modal')).toBeVisible()
  await page.locator('.live-modal input[placeholder="输入标题"]').fill(`UC07 页面直播 ${Date.now()}`)
  await page.locator('.live-modal .confirm-btn').click()

  await expect(page.locator('.live-modal .stream-result')).toBeVisible()
  await expect(page.locator('.live-modal')).toContainText('OBS 服务器')
  await expect(page.locator('.live-modal')).toContainText('直播间号')
})

test('SRS 推流后直播间能播放', async ({ page, request }) => {
  test.skip(!process.env.RUN_SRS_E2E, 'SRS E2E 需要设置 RUN_SRS_E2E=true 并启动 SRS')
  test.setTimeout(120_000)

  const roomId = await createRoom(request)
  const detailResponse = await request.get(`${API_BASE}/live/rooms/${roomId}`)
  const detail = await detailResponse.json()
  const pushUrl = detail.data.pushUrl
  const streamKey = pushUrl.split('/').pop()
  const flvUrl = `http://127.0.0.1:8081/live/${streamKey}.flv`

  const ffmpeg = spawn('ffmpeg', [
    '-hide_banner',
    '-loglevel', 'error',
    '-re',
    '-f', 'lavfi',
    '-i', 'testsrc=duration=60:size=640x360:rate=15',
    '-f', 'lavfi',
    '-i', 'sine=frequency=440:duration=60',
    '-c:v', 'libx264',
    '-preset', 'ultrafast',
    '-tune', 'zerolatency',
    '-c:a', 'aac',
    '-b:a', '128k',
    '-f', 'flv',
    pushUrl
  ], { windowsHide: true })

  let ffmpegOutput = ''
  let ffmpegExitCode = null
  let ffmpegError = ''
  ffmpeg.stdout.on('data', (chunk) => {
    ffmpegOutput += chunk.toString()
  })
  ffmpeg.stderr.on('data', (chunk) => {
    ffmpegOutput += chunk.toString()
  })
  ffmpeg.on('exit', (code) => {
    ffmpegExitCode = code
  })
  ffmpeg.on('error', (error) => {
    ffmpegError = error.message
  })

  try {
    await new Promise((resolve) => setTimeout(resolve, 2000))
    await expect.poll(async () => {
      if (ffmpegError) {
        throw new Error(`ffmpeg failed to start: ${ffmpegError}`)
      }
      if (ffmpegExitCode !== null) {
        throw new Error(`ffmpeg exited early with code ${ffmpegExitCode}: ${ffmpegOutput}`)
      }
      return await checkFlvStatus(flvUrl)
    }, { timeout: 30_000 }).toBe(200)

    await new Promise((resolve) => setTimeout(resolve, 3000))
    await page.goto(`/#/live/${roomId}`)
    await page.locator('video.live-player').waitFor()
    await page.waitForFunction(() => {
      const video = document.querySelector('video.live-player')
      return video && video.currentTime > 0.5
    }, undefined, { timeout: 30_000 })
  } finally {
    ffmpeg.kill()
  }
})
