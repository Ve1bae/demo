#!/usr/bin/env node

// Local-only benchmark for the three comparable video APIs.
// Usage example:
//   E2E_API_BASE=http://127.0.0.1:8080/api node scripts/benchmark-api.mjs

import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const baseUrl = (process.env.BENCHMARK_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/+$/, '')
const videoId = Number(process.env.BENCHMARK_VIDEO_ID || 1)
const userId = Number(process.env.BENCHMARK_USER_ID || 1)
const concurrencies = (process.env.BENCHMARK_CONCURRENCY || '1,10,50,100')
  .split(',').map(Number).filter(n => Number.isInteger(n) && n > 0)
const durationSeconds = Number(process.env.BENCHMARK_DURATION_SEC || 30)
const warmupSeconds = Number(process.env.BENCHMARK_WARMUP_SEC || 10)
const runs = Number(process.env.BENCHMARK_RUNS || 3)
const outputDir = resolve(process.env.BENCHMARK_OUTPUT_DIR || 'benchmark-results')

const cases = [
  { name: 'video-detail', method: 'GET', path: `/videos/${videoId}` },
  { name: 'comments-list', method: 'GET', path: `/videos/${videoId}/comments?page=1&pageSize=50` },
  { name: 'play-counter', method: 'POST', path: `/videos/${videoId}/play` }
]

const percentile = (values, p) => {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1)]
}

async function request(testCase) {
  const started = performance.now()
  try {
    const response = await fetch(`${baseUrl}${testCase.path}`, {
      method: testCase.method,
      headers: {
        Accept: 'application/json',
        ...(testCase.method === 'POST' ? { 'X-User-Id': String(userId) } : {})
      },
      signal: AbortSignal.timeout(15000)
    })
    // Consume the body so connection reuse is possible.
    await response.arrayBuffer()
    return { latency: performance.now() - started, ok: response.status >= 200 && response.status < 300 }
  } catch {
    return { latency: performance.now() - started, ok: false }
  }
}

async function runWindow(testCase, concurrency, seconds, collect) {
  const deadline = Date.now() + seconds * 1000
  const latencies = []
  let total = 0
  let errors = 0
  async function worker() {
    while (Date.now() < deadline) {
      const result = await request(testCase)
      if (collect) {
        latencies.push(result.latency)
        total++
        if (!result.ok) errors++
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker))
  return { total, errors, latencies }
}

async function benchmark(testCase, concurrency, run) {
  if (warmupSeconds > 0) await runWindow(testCase, concurrency, warmupSeconds, false)
  const result = await runWindow(testCase, concurrency, durationSeconds, true)
  const seconds = durationSeconds
  return {
    run, case: testCase.name, method: testCase.method, path: testCase.path,
    concurrency, requests: result.total, throughput: result.total / seconds,
    avgMs: result.latencies.length ? result.latencies.reduce((a, b) => a + b, 0) / result.latencies.length : 0,
    p95Ms: percentile(result.latencies, 0.95), errors: result.errors,
    errorRate: result.total ? result.errors / result.total : 1
  }
}

const results = []
console.log(`Target: ${baseUrl}; duration=${durationSeconds}s, warmup=${warmupSeconds}s, runs=${runs}`)
for (const concurrency of concurrencies) {
  for (const testCase of cases) {
    for (let run = 1; run <= runs; run++) {
      process.stdout.write(`\n${testCase.name} concurrency=${concurrency} run=${run} ... `)
      const row = await benchmark(testCase, concurrency, run)
      results.push(row)
      console.log(`${row.throughput.toFixed(2)} req/s, avg=${row.avgMs.toFixed(1)}ms, p95=${row.p95Ms.toFixed(1)}ms, errors=${row.errors}`)
    }
  }
}

await mkdir(outputDir, { recursive: true })
const stamp = new Date().toISOString().replace(/[:.]/g, '-')
const csvPath = resolve(outputDir, `api-benchmark-${stamp}.csv`)
const mdPath = resolve(outputDir, `api-benchmark-${stamp}.md`)
const headers = ['run', 'case', 'method', 'path', 'concurrency', 'requests', 'throughput', 'avgMs', 'p95Ms', 'errors', 'errorRate']
const csv = [headers.join(','), ...results.map(row => headers.map(h => JSON.stringify(row[h] ?? '')).join(','))].join('\n') + '\n'
const md = [
  '# API 性能测试结果', '', `- 地址：${baseUrl}`, `- 测试时长：${durationSeconds}s，预热：${warmupSeconds}s，重复：${runs} 次`, '',
  '| 用例 | 并发 | 次数 | 吞吐(req/s) | 平均(ms) | P95(ms) | 错误率 |', '|---|---:|---:|---:|---:|---:|---:|',
  ...results.map(r => `| ${r.case} | ${r.concurrency} | ${r.requests} | ${r.throughput.toFixed(2)} | ${r.avgMs.toFixed(1)} | ${r.p95Ms.toFixed(1)} | ${(r.errorRate * 100).toFixed(2)}% |`), ''
].join('\n')
await writeFile(csvPath, csv, 'utf8')
await writeFile(mdPath, md, 'utf8')
console.log(`\nCSV: ${csvPath}\nMarkdown: ${mdPath}`)
