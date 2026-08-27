import { expect, test } from '@playwright/test'

const titles = {
  hot: 'UC03 E2E 热门推荐',
  low: 'UC03 E2E 普通推荐',
  private: 'UC03 E2E 私密视频',
  followed: 'UC03 E2E 关注作者作品',
  music: 'UC03 E2E 校园音乐',
}

const videoCards = (page) => page.locator('.video-grid .video-card')

const cardTitles = async (page) => videoCards(page).locator('.title').allTextContents()

const openHome = async (page) => {
  await page.goto('/#/home')
  await expect(page.getByText('首页推荐', { exact: true })).toHaveClass(/active/)
  await expect(videoCards(page).first()).toBeVisible()
}

const search = async (page, keyword) => {
  const responsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/videos/recommend'
      && url.searchParams.get('keyword') === keyword
      && response.status() === 200
  })
  await page.getByPlaceholder('搜索感兴趣的视频...').fill(keyword)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await responsePromise
}

test.describe('UC-03 视频推荐端到端测试', () => {
  test('E2E-03-01 游客打开首页时按热度展示公开视频', async ({ page }) => {
    await openHome(page)

    const visibleTitles = await cardTitles(page)
    expect(visibleTitles).toContain(titles.hot)
    expect(visibleTitles).toContain(titles.low)
    expect(visibleTitles).not.toContain(titles.private)
    expect(visibleTitles.indexOf(titles.hot)).toBeLessThan(visibleTitles.indexOf(titles.low))
  })

  test('E2E-03-02 用户通过搜索框筛选推荐视频', async ({ page }) => {
    await openHome(page)
    await search(page, '校园音乐')

    await expect(videoCards(page)).toHaveCount(1)
    await expect(videoCards(page).first().locator('.title')).toHaveText(titles.music)
  })

  test('E2E-03-03 用户登录后优先看到已关注作者的视频', async ({ page }) => {
    await openHome(page)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.getByPlaceholder('请输入用户名').fill('uc03_e2e_viewer')
    await page.getByPlaceholder('请输入密码').fill('e2e-password')

    const recommendationPromise = page.waitForResponse((response) => {
      const request = response.request()
      return new URL(response.url()).pathname === '/api/videos/recommend'
        && request.headers()['x-user-id'] === '930001'
        && response.status() === 200
    })
    await page.locator('.modal-content').getByRole('button', { name: '登录', exact: true }).click()
    await recommendationPromise

    await expect(page.locator('.username')).toHaveText('E2E推荐用户')
    await expect(videoCards(page).first().locator('.title')).toHaveText(titles.followed)
  })

  test('E2E-03-04 搜索无匹配内容时显示空结果状态', async ({ page }) => {
    await openHome(page)
    await search(page, 'UC03_E2E_不存在的内容')

    await expect(videoCards(page)).toHaveCount(0)
    await expect(page.getByText('暂无视频，去创作者中心上传第一条作品吧。')).toBeVisible()
  })

  test('E2E-03-05 用户可从推荐卡片进入视频页并返回首页', async ({ page }) => {
    await openHome(page)
    await search(page, '热门推荐')
    await videoCards(page).filter({ hasText: titles.hot }).click()

    await expect(page.locator('.video-player-container')).toBeVisible()
    await expect(page.locator('.video-title')).toHaveText(titles.hot)
    await page.locator('.back-btn').click()
    await expect(videoCards(page).filter({ hasText: titles.hot })).toBeVisible()
  })
})
