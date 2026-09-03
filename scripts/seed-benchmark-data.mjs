#!/usr/bin/env node

// Prepare equivalent video/comment data for local performance comparisons.
// Usage: node scripts/seed-benchmark-data.mjs
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const base = (process.env.BENCHMARK_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/+$/, '')
const userId = String(process.env.BENCHMARK_USER_ID || '1')
const username = process.env.BENCHMARK_USERNAME || 'benchmark-user'
const comments = Number(process.env.BENCHMARK_COMMENT_COUNT || 20)
const fixture = resolve(process.env.BENCHMARK_VIDEO_FILE || 'frontend/demo/public/e2e/hot.mp4')
const output = resolve(process.env.BENCHMARK_DATA_FILE || 'benchmark-results/benchmark-data.json')

async function call(method, path, options = {}) {
  const headers = { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) }
  const response = await fetch(`${base}${path}`, { method, headers, body: options.body ? JSON.stringify(options.body) : options.form })
  const text = await response.text()
  let data; try { data = text ? JSON.parse(text) : null } catch { data = text }
  if (!response.ok || (data && data.code >= 400)) throw new Error(`${method} ${path} failed: HTTP ${response.status} ${text}`)
  return data
}

const form = new FormData()
form.set('title', process.env.BENCHMARK_VIDEO_TITLE || `Performance benchmark video ${Date.now()}`)
form.set('description', 'Local performance test fixture')
form.set('author', username)
form.set('userId', userId)
form.set('duration', '5')
form.set('file', new Blob([await readFile(fixture)], { type: 'video/mp4' }), 'benchmark.mp4')
const uploaded = await call('POST', '/videos/upload', { form })
const videoId = uploaded?.data?.id ?? uploaded?.data?.videoId
if (!videoId) throw new Error('Upload response did not contain data.id/videoId')

const commentIds = []
for (let i = 1; i <= comments; i++) {
  const created = await call('POST', `/videos/${videoId}/comments`, {
    headers: { 'X-User-Id': userId, 'X-Username': username },
    body: { content: `Benchmark comment ${i} ${Date.now()}` }
  })
  if (created?.data?.id) commentIds.push(created.data.id)
}
const result = { base, videoId: Number(videoId), userId: Number(userId), commentIds, createdAt: new Date().toISOString() }
await mkdir(resolve(output, '..'), { recursive: true })
await writeFile(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8')
console.log(JSON.stringify(result))
