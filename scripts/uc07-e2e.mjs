#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const baseUrl = (process.env.UC07_E2E_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const anchorId = Number(process.env.UC07_E2E_USER_ID || '42');
const reportPath = resolve(process.env.UC07_E2E_REPORT || 'backend/target/e2e-reports/uc07-e2e-report.json');

const startedAt = new Date();
const results = [];
let createdRoomId = null;

function assertThat(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function request(method, path, { headers = {}, body } = {}) {
  let response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    const detail = error.cause?.message || error.message;
    throw new Error(`请求 ${method} ${baseUrl}${path} 失败：${detail}`);
  }

  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`响应不是 JSON：HTTP ${response.status} ${text.slice(0, 200)}`);
  }

  return { response, json };
}

async function runCase(id, name, fn) {
  const start = Date.now();
  try {
    await fn();
    results.push({ id, name, status: 'PASSED', durationMs: Date.now() - start });
    console.log(`[PASS] ${id} ${name}`);
  } catch (error) {
    results.push({
      id,
      name,
      status: 'FAILED',
      durationMs: Date.now() - start,
      failureReason: error.message,
    });
    console.error(`[FAIL] ${id} ${name}: ${error.message}`);
  }
}

async function createRoom() {
  const title = `UC-07 E2E ${Date.now()}`;
  const { response, json } = await request('POST', '/api/live/rooms', {
    headers: { 'X-User-Id': String(anchorId) },
    body: {
      title,
      categoryId: 8,
      coverUrl: 'https://example.test/uc07-cover.png',
    },
  });

  assertThat(response.status === 200, `创建接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 200, `创建接口业务码应为 200，实际为 ${json.code}`);
  assertThat(json.data, '创建接口应返回 data');
  assertThat(Number.isFinite(Number(json.data.roomId)), '创建结果应包含 roomId');
  assertThat(Number(json.data.userId) === anchorId, `创建结果 userId 应为 ${anchorId}`);
  assertThat(json.data.title === title, '创建结果标题应与请求一致');
  assertThat(json.data.status === 'online', `创建后状态应为 online，实际为 ${json.data.status}`);
  assertThat(typeof json.data.streamName === 'string' && json.data.streamName.startsWith('room_'), '应生成 room_ 开头的 streamName');
  assertThat(typeof json.data.pushUrl === 'string' && json.data.pushUrl.includes(json.data.streamName), 'pushUrl 应包含 streamName');
  assertThat(typeof json.data.pullUrl === 'string' && json.data.pullUrl.includes(json.data.streamName), 'pullUrl 应包含 streamName');
  assertThat(json.data.qualityUrls && Object.keys(json.data.qualityUrls).length >= 3, '应返回至少 3 档直播播放地址');

  createdRoomId = Number(json.data.roomId);
}

async function getCreatedRoom() {
  assertThat(createdRoomId, '缺少已创建的 roomId');
  const { response, json } = await request('GET', `/api/live/rooms/${createdRoomId}`);

  assertThat(response.status === 200, `查询接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 200, `查询接口业务码应为 200，实际为 ${json.code}`);
  assertThat(Number(json.data.roomId) === createdRoomId, '查询结果 roomId 应与创建结果一致');
  assertThat(Number(json.data.userId) === anchorId, '查询结果 userId 应与主播一致');
  assertThat(json.data.status === 'online', '关闭前直播间应保持 online 状态');
  assertThat(Boolean(json.data.pushUrl), '查询结果应包含推流地址');
  assertThat(Boolean(json.data.pullUrl), '查询结果应包含播放地址');
}

async function listRoomsContainsCreatedRoom() {
  assertThat(createdRoomId, '缺少已创建的 roomId');
  const { response, json } = await request('GET', '/api/live/rooms?page=1&pageSize=50');

  assertThat(response.status === 200, `列表接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 200, `列表接口业务码应为 200，实际为 ${json.code}`);
  assertThat(Array.isArray(json.data?.list), '列表接口 data.list 应为数组');
  const found = json.data.list.some((room) => Number(room.roomId) === createdRoomId);
  assertThat(found, `直播间列表应包含 roomId=${createdRoomId}`);
}

async function closeCreatedRoom() {
  assertThat(createdRoomId, '缺少已创建的 roomId');
  const { response, json } = await request('POST', `/api/live/rooms/${createdRoomId}/close`, {
    headers: { 'X-User-Id': String(anchorId) },
  });

  assertThat(response.status === 200, `关闭接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 200, `关闭接口业务码应为 200，实际为 ${json.code}`);
  assertThat(Number(json.data.roomId) === createdRoomId, '关闭结果 roomId 应与创建结果一致');
  assertThat(json.data.status === 'offline', `关闭后状态应为 offline，实际为 ${json.data.status}`);
}

async function rejectBlankTitle() {
  const { response, json } = await request('POST', '/api/live/rooms', {
    headers: { 'X-User-Id': String(anchorId) },
    body: { title: '   ' },
  });

  assertThat(response.status === 200, `非法标题接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 400, `非法标题业务码应为 400，实际为 ${json.code}`);
  assertThat(json.message === '直播间标题不能为空', `非法标题提示不正确：${json.message}`);
  assertThat(json.data == null, '非法标题不应返回 data');
}

async function rejectMissingLogin() {
  const { response, json } = await request('POST', '/api/live/rooms', {
    body: { title: '未登录端到端测试直播间' },
  });

  assertThat(response.status === 200, `未登录接口 HTTP 状态应为 200，实际为 ${response.status}`);
  assertThat(json.code === 400, `未登录业务码应为 400，实际为 ${json.code}`);
  assertThat(json.message === '请先登录后再开始直播', `未登录提示不正确：${json.message}`);
  assertThat(json.data == null, '未登录不应返回 data');
}

async function writeReport() {
  const endedAt = new Date();
  const summary = {
    testObject: 'UC-07 创建直播间端到端测试',
    baseUrl,
    anchorId,
    startedAt: startedAt.toISOString(),
    endedAt: endedAt.toISOString(),
    durationMs: endedAt.getTime() - startedAt.getTime(),
    total: results.length,
    passed: results.filter((item) => item.status === 'PASSED').length,
    failed: results.filter((item) => item.status === 'FAILED').length,
    environment: {
      node: process.version,
      platform: process.platform,
      arch: process.arch,
      cwd: process.cwd(),
    },
    results,
  };

  await mkdir(dirname(reportPath), { recursive: true });
  await writeFile(reportPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
  console.log(`报告已生成：${reportPath}`);
  return summary;
}

await runCase('E2E-UC07-01', '接口入口创建直播间成功', createRoom);
await runCase('E2E-UC07-02', '创建后可按房间编号查询', getCreatedRoom);
await runCase('E2E-UC07-03', '创建后出现在直播间列表', listRoomsContainsCreatedRoom);
await runCase('E2E-UC07-04', '主播可关闭已创建直播间', closeCreatedRoom);
await runCase('E2E-UC07-05', '标题为空时返回业务失败', rejectBlankTitle);
await runCase('E2E-UC07-06', '未登录时返回业务失败', rejectMissingLogin);

const summary = await writeReport();
console.log(`UC-07 E2E 汇总：total=${summary.total}, passed=${summary.passed}, failed=${summary.failed}`);

if (summary.failed > 0) {
  process.exitCode = 1;
}
