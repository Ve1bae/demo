import { expect, test } from '@playwright/test'

const seedLogin = async (page) => {
  await page.addInitScript(() => {
    localStorage.setItem('loginUserNickname', '测试用户')
    localStorage.setItem('loginUser', '测试用户')
    localStorage.setItem('loginUserId', '1')
    localStorage.setItem('loginUserAvatar', '')
  })
}

const openVideo = async (page) => {
  await page.goto('/#/home')
  const card = page.locator('.video-grid .video-card').first()
  await expect(card).toBeVisible()
  await card.click()
  await expect(page.locator('.video-player-container')).toBeVisible()
}

test.describe('UC-04 至 UC-06 视频播放与互动端到端测试', () => {
  test('E2E-TC-04-01 播放器加载视频详情并请求播放计数', async ({ page }) => {
    const playResponse = page.waitForResponse((response) =>
      response.url().includes('/api/videos/') && response.url().endsWith('/play')
      && response.request().method() === 'POST'
    )

    await openVideo(page)
    const video = page.locator('video.main-video')
    await expect(video).toBeVisible()
    await video.evaluate((element) => new Promise((resolve) => {
      if (element.readyState >= 2) {
        resolve()
        return
      }
      element.addEventListener('loadeddata', resolve, { once: true })
    }))
    await page.locator('.bottom-controls .left-controls > .control-btn').click()
    await expect((await playResponse).status()).toBe(200)
  })

  test('E2E-TC-05-01 登录用户提交评论后页面显示评论', async ({ page }) => {
    await seedLogin(page)
    await openVideo(page)

    const content = `UC05 E2E 评论 ${Date.now()}`
    await page.locator('.comment-input').fill(content)
    await page.locator('.send-comment-btn').click()

    await expect(page.locator('.comment-text', { hasText: content })).toBeVisible()
  })

  test('E2E-TC-06-01 登录用户发送视频弹幕后页面显示弹幕', async ({ page }) => {
    await seedLogin(page)
    await openVideo(page)

    const content = `UC06 E2E 弹幕 ${Date.now()}`
    await page.locator('.danmaku-input').fill(content)
    const sendResponse = page.waitForResponse((response) =>
      response.url().includes('/danmakus') && response.request().method() === 'POST'
    )
    await page.locator('.send-danmaku-btn').click()

    await expect((await sendResponse).status()).toBe(200)
    await expect(page.locator('.danmaku-item', { hasText: content })).toBeVisible()
  })
})
