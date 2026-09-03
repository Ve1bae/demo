import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'

const baseUrl = process.env.VIDEO_SERVICE_BASE_URL || 'http://127.0.0.1:8082'
const reportPath = process.env.VIDEO_SERVICE_API_REPORT || 'target/api-test-results/api-results.json'
const results = []

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, options)
  const text = await response.text()
  let body = text
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    // Keep the raw response so a malformed payload is visible in the report.
  }
  return { status: response.status, body }
}

async function waitForHealth() {
  let lastError
  for (let attempt = 1; attempt <= 60; attempt += 1) {
    try {
      const response = await request('/actuator/health')
      if (response.status === 200 && response.body?.status === 'UP') {
        return
      }
      lastError = new Error(`health returned ${response.status}`)
    } catch (error) {
      lastError = error
    }
    await delay(2000)
  }
  throw new Error(`video-service did not become healthy: ${lastError?.message || 'unknown error'}`)
}

async function test(id, name, path, options, verify) {
  const startedAt = new Date().toISOString()
  const record = {
    id,
    name,
    input: {
      method: options?.method || 'GET',
      path,
      headers: options?.headers || {}
    },
    startedAt
  }

  let response = null
  try {
    response = await request(path, options)
    verify(response)
    Object.assign(record, { result: 'PASS', output: response })
  } catch (error) {
    Object.assign(record, {
      result: 'FAIL',
      failureReason: error.message,
      output: response
    })
  }
  results.push(record)
}

await waitForHealth()

await test('LIVE-API-TC-03-01', '健康检查', '/actuator/health', {}, ({ status, body }) => {
  assert.equal(status, 200)
  assert.equal(body.status, 'UP')
})

await test('LIVE-API-TC-03-02', '游客获取公开视频并按分数排序', '/api/videos/recommend', {}, ({ status, body }) => {
  assert.equal(status, 200)
  assert.equal(body.code, 200)
  assert.deepEqual(body.data.map((video) => video.id), [930301, 930302, 930303])
  assert.equal(body.data.some((video) => video.id === 930304), false)
})

await test(
  'LIVE-API-TC-03-03',
  '登录用户的已观看视频降权',
  '/api/videos/recommend',
  { headers: { 'X-User-Id': '930001' } },
  ({ status, body }) => {
    assert.equal(status, 200)
    assert.deepEqual(body.data.slice(0, 2).map((video) => video.id), [930302, 930303])
    assert.equal(body.data.at(-1).id, 930301)
  }
)

await test(
  'LIVE-API-TC-03-04',
  '分类和关键词组合筛选',
  '/api/videos/recommend?categoryId=3&keyword=music',
  {},
  ({ status, body }) => {
    assert.equal(status, 200)
    assert.deepEqual(body.data.map((video) => video.id), [930301])
  }
)

await test(
  'LIVE-API-TC-03-05',
  '排序后分页',
  '/api/videos/recommend?page=2&pageSize=1',
  {},
  ({ status, body }) => {
    assert.equal(status, 200)
    assert.deepEqual(body.data.map((video) => video.id), [930302])
  }
)

await test(
  'LIVE-API-TC-03-06',
  '无匹配结果返回空数组',
  '/api/videos/recommend?keyword=not-found',
  {},
  ({ status, body }) => {
    assert.equal(status, 200)
    assert.deepEqual(body.data, [])
  }
)

await test(
  'LIVE-API-TC-03-07',
  '非法页码返回参数错误',
  '/api/videos/recommend?page=abc',
  {},
  ({ status, body }) => {
    assert.equal(status, 400)
    assert.equal(body.code, 400)
  }
)

await test(
  'LIVE-API-TC-03-08',
  '非法用户标识返回参数错误',
  '/api/videos/recommend',
  { headers: { 'X-User-Id': 'guest' } },
  ({ status, body }) => {
    assert.equal(status, 400)
    assert.equal(body.code, 400)
  }
)

const summary = {
  total: results.length,
  passed: results.filter((item) => item.result === 'PASS').length,
  failed: results.filter((item) => item.result === 'FAIL').length
}
const report = {
  generatedAt: new Date().toISOString(),
  environment: {
    baseUrl,
    database: 'MySQL 8.4 container',
    service: 'video-service container'
  },
  summary,
  cases: results
}

const reportDirectory = reportPath.substring(0, reportPath.lastIndexOf('/'))
if (reportDirectory) {
  await mkdir(reportDirectory, { recursive: true })
}
await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')

for (const result of results) {
  console.log(`${result.result} ${result.id} ${result.name}`)
}
console.log(`API tests: ${summary.passed}/${summary.total} passed`)

if (summary.failed > 0) {
  process.exitCode = 1
}



