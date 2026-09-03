import fs from 'node:fs/promises'

const captures = new WeakMap()

function safeHeaders(headers) {
  return Object.fromEntries(Object.entries(headers || {}).map(([key, value]) => {
    const lower = key.toLowerCase()
    return [key, ['authorization', 'cookie', 'set-cookie'].includes(lower) ? '[redacted]' : value]
  }))
}

export function startCapture(page, testInfo) {
  const events = []
  const pending = []

  page.on('request', (request) => {
    events.push({
      type: 'http.request',
      method: request.method(),
      url: request.url(),
      headers: safeHeaders(request.headers()),
      body: request.postData() || null,
      at: new Date().toISOString()
    })
  })
  page.on('response', (response) => {
    const capture = (async () => {
      let body = null
      const contentType = (await response.headerValue('content-type')) || ''
      const isApi = response.url().includes('/api/')
      const isBinary = /video|audio|octet-stream|image\//i.test(contentType) || response.url().endsWith('.flv')
      if (isApi && !isBinary) {
        try {
          const text = await response.text()
          body = text.length > 20000 ? `${text.slice(0, 20000)}...[truncated]` : text
        } catch {
          body = '[unavailable]'
        }
      }
      events.push({
        type: 'http.response',
        status: response.status(),
        url: response.url(),
        contentType,
        body,
        at: new Date().toISOString()
      })
    })()
    pending.push(capture)
  })
  page.on('requestfailed', (request) => {
    events.push({
      type: 'http.failed',
      method: request.method(),
      url: request.url(),
      error: request.failure()?.errorText || 'unknown',
      at: new Date().toISOString()
    })
  })
  page.on('websocket', (socket) => {
    events.push({ type: 'ws.open', url: socket.url(), at: new Date().toISOString() })
    socket.on('framesent', (payload) => events.push({ type: 'ws.sent', url: socket.url(), payload, at: new Date().toISOString() }))
    socket.on('framereceived', (payload) => events.push({ type: 'ws.received', url: socket.url(), payload, at: new Date().toISOString() }))
    socket.on('close', () => events.push({ type: 'ws.close', url: socket.url(), at: new Date().toISOString() }))
  })

  captures.set(testInfo, {
    events,
    pending,
    extra: []
  })
}

export function recordExchange(testInfo, name, input, output) {
  const capture = captures.get(testInfo)
  if (capture) capture.extra.push({ type: 'api.exchange', name, input, output, at: new Date().toISOString() })
}

export async function finishCapture(page, testInfo) {
  const capture = captures.get(testInfo)
  if (!capture) return
  await Promise.allSettled(capture.pending)
  let pageState = null
  try {
    pageState = await page.evaluate(() => ({
      url: location.href,
      title: document.title,
      visibleText: (document.body?.innerText || '').slice(0, 12000)
    }))
  } catch {
    pageState = { unavailable: true }
  }
  const document = {
    test: testInfo.title,
    status: testInfo.status,
    expectedStatus: testInfo.expectedStatus,
    errors: testInfo.errors.map((error) => error.message),
    pageState,
    events: [...capture.extra, ...capture.events]
  }
  const path = testInfo.outputPath('e2e-io.json')
  await fs.writeFile(path, JSON.stringify(document, null, 2), 'utf8')
  await testInfo.attach('e2e-io.json', { path, contentType: 'application/json' })
}
