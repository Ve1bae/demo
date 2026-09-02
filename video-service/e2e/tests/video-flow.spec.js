/**
 * video-service 真实端到端测试
 *
 * 链路：上传 → 推荐列表 → 详情 → 用户列表 → 点赞 → 评论 → 评论列表 → 弹幕 → 播放计数
 *       → 真实访问 MinIO 文件 → 越权删除(403) → 本人删除 → 删除后列表校验
 *
 * 依赖真实环境（不是 mock）：
 *   - 真实 MySQL（video_db，schema 已初始化）
 *   - 真实 MinIO（hangyin-video 桶，允许匿名读）
 *   - 真实 video-service 进程（http://127.0.0.1:8082）
 *
 * 每一步都会把「原始输入」和「真实输出」追加写入 report/e2e-report.md，
 * 该报告随 CI 一并上传。
 */
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const REPORT_DIR = path.join(__dirname, '..', 'report');
const REPORT_FILE = path.join(REPORT_DIR, 'e2e-report.md');

// 串行模式下同一 worker 执行，模块级变量用于跨用例共享状态
const state = {
  userId: 10,
  videoId: null,
  playUrl: null,
};

fs.mkdirSync(REPORT_DIR, { recursive: true });
fs.writeFileSync(REPORT_FILE, [
  '# video-service 端到端测试报告（真实环境）',
  '',
  `> 运行时间：${new Date().toISOString()}`,
  `> 被测服务：${process.env.E2E_BASE_URL || 'http://127.0.0.1:8082'}`,
  '> 依赖：真实 MySQL(video_db) + 真实 MinIO(hangyin-video) + 真实后端进程',
  '',
  '## 用例汇总',
  '',
  '| 步骤 | 操作 | 结果 |',
  '| --- | --- | --- |',
  '| 1 | 上传视频（multipart 真实文件） | 见下方详细输入输出 |',
  '| 2 | 推荐列表包含新视频 | |',
  '| 3 | 查询视频详情 | |',
  '| 4 | 查询用户上传列表 | |',
  '| 5 | 点赞视频 | |',
  '| 6 | 发表评论 | |',
  '| 7 | 查询评论列表 | |',
  '| 8 | 发送点播弹幕 | |',
  '| 9 | 播放计数 | |',
  '| 10 | 真实访问 MinIO 原始文件 | |',
  '| 11 | 越权删除（非本人）拒绝 403 | |',
  '| 12 | 本人删除视频 | |',
  '| 13 | 删除后推荐列表不再包含 | |',
  '',
  '---',
  '',
].join('\n'));

/** 把一次请求的原始输入和真实输出追加到报告 */
function record(step, input, output) {
  const block = [
    `## ${step}`,
    '',
    '**输入（原始请求）**',
    '',
    '```json',
    JSON.stringify(input, null, 2),
    '```',
    '',
    '**输出（真实响应）**',
    '',
    '```json',
    JSON.stringify(output, null, 2),
    '```',
    '',
    '---',
    '',
  ].join('\n');
  fs.appendFileSync(REPORT_FILE, block);
}

test.describe.configure({ mode: 'serial' });

test.describe('video-service 完整操作链路（真实环境）', () => {

  test('步骤1 上传视频（真实 multipart 文件写入 MinIO）', async ({ request }) => {
    const fileBuf = Buffer.from('fake-mp4-content-for-video-service-e2e');
    const input = {
      method: 'POST',
      url: '/api/videos/upload',
      multipart: {
        title: '端到端测试视频',
        description: '来自端到端测试的原始输入',
        userId: state.userId,
        author: '测试员',
        tags: '测试,微服务',
        file: { name: 'e2e-test.mp4', mimeType: 'video/mp4', size: fileBuf.length },
      },
    };
    const resp = await request.post('/api/videos/upload', {
      multipart: {
        title: '端到端测试视频',
        description: '来自端到端测试的原始输入',
        userId: String(state.userId),
        author: '测试员',
        tags: '测试,微服务',
        file: { name: 'e2e-test.mp4', mimeType: 'video/mp4', buffer: fileBuf },
      },
    });
    const body = await resp.json();
    record('步骤1 上传视频', input, body);
    expect(resp.status()).toBe(200);
    expect(body.code).toBe(200);
    expect(body.data.id).toBeGreaterThan(0);
    expect(body.data.playUrl).toBeTruthy();
    state.videoId = body.data.id;
    state.playUrl = body.data.playUrl;
  });

  test('步骤2 推荐列表应包含刚上传的视频', async ({ request }) => {
    const resp = await request.get('/api/videos/recommend?page=1&pageSize=12');
    const body = await resp.json();
    record('步骤2 推荐列表', { method: 'GET', url: '/api/videos/recommend?page=1&pageSize=12' }, body);
    expect(body.code).toBe(200);
    expect(body.data.map((v) => v.id)).toContain(state.videoId);
  });

  test('步骤3 查询视频详情', async ({ request }) => {
    const resp = await request.get(`/api/videos/${state.videoId}`);
    const body = await resp.json();
    record('步骤3 视频详情', { method: 'GET', url: `/api/videos/${state.videoId}` }, body);
    expect(body.code).toBe(200);
    expect(body.data.title).toBe('端到端测试视频');
    expect(body.data.status).toBe('public');
  });

  test('步骤4 查询用户上传列表', async ({ request }) => {
    const resp = await request.get(`/api/videos/user/${state.userId}/uploads`);
    const body = await resp.json();
    record('步骤4 用户上传列表', { method: 'GET', url: `/api/videos/user/${state.userId}/uploads` }, body);
    expect(body.code).toBe(200);
    expect(body.data.map((v) => v.id)).toContain(state.videoId);
  });

  test('步骤5 点赞视频', async ({ request }) => {
    const input = { method: 'POST', url: `/api/videos/${state.videoId}/likes`, headers: { 'X-User-Id': state.userId } };
    const resp = await request.post(`/api/videos/${state.videoId}/likes`, { headers: { 'X-User-Id': '10' } });
    const body = await resp.json();
    record('步骤5 点赞视频', input, body);
    expect(body.code).toBe(200);
    expect(body.data.liked).toBe(true);
    expect(body.data.likeCount).toBe(1);
  });

  test('步骤6 发表评论', async ({ request }) => {
    const input = {
      method: 'POST',
      url: `/api/videos/${state.videoId}/comments`,
      headers: { 'X-User-Id': state.userId },
      body: { content: '端到端测试评论' },
    };
    const resp = await request.post(`/api/videos/${state.videoId}/comments`, {
      headers: { 'X-User-Id': '10' },
      data: { content: '端到端测试评论' },
    });
    const body = await resp.json();
    record('步骤6 发表评论', input, body);
    expect(body.code).toBe(200);
  });

  test('步骤7 查询评论列表', async ({ request }) => {
    const resp = await request.get(`/api/videos/${state.videoId}/comments`);
    const body = await resp.json();
    record('步骤7 评论列表', { method: 'GET', url: `/api/videos/${state.videoId}/comments` }, body);
    expect(body.code).toBe(200);
    expect(body.data.list[0].content).toBe('端到端测试评论');
  });

  test('步骤8 发送点播弹幕', async ({ request }) => {
    const input = {
      method: 'POST',
      url: `/api/videos/${state.videoId}/danmakus`,
      headers: { 'X-User-Id': state.userId },
      body: { content: '前方高能', timeSeconds: 5 },
    };
    const resp = await request.post(`/api/videos/${state.videoId}/danmakus`, {
      headers: { 'X-User-Id': '10' },
      data: { content: '前方高能', timeSeconds: 5 },
    });
    const body = await resp.json();
    record('步骤8 发送弹幕', input, body);
    expect(body.code).toBe(200);
  });

  test('步骤9 播放计数', async ({ request }) => {
    const resp = await request.post(`/api/videos/${state.videoId}/play`, { headers: { 'X-User-Id': '10' } });
    const body = await resp.json();
    record('步骤9 播放计数', { method: 'POST', url: `/api/videos/${state.videoId}/play`, headers: { 'X-User-Id': 10 } }, body);
    expect(body.code).toBe(200);
    expect(body.data.playCount).toBe(1);
  });

  test('步骤10 真实访问 MinIO 上的原始文件', async ({ request }) => {
    const resp = await request.get(state.playUrl);
    const output = {
      status: resp.status(),
      contentType: resp.headers()['content-type'] || null,
      bodyLength: (await resp.body()).length,
    };
    record('步骤10 访问 MinIO 文件', { method: 'GET', url: state.playUrl }, output);
    expect(resp.status()).toBe(200);
  });

  test('步骤11 越权删除被拒绝（403）', async ({ request }) => {
    const input = { method: 'DELETE', url: `/api/videos/${state.videoId}`, headers: { 'X-User-Id': 999 } };
    const resp = await request.delete(`/api/videos/${state.videoId}`, { headers: { 'X-User-Id': '999' } });
    const body = await resp.json();
    record('步骤11 越权删除', input, body);
    expect(body.code).toBe(403);
  });

  test('步骤12 本人删除视频', async ({ request }) => {
    const input = { method: 'DELETE', url: `/api/videos/${state.videoId}`, headers: { 'X-User-Id': state.userId } };
    const resp = await request.delete(`/api/videos/${state.videoId}`, { headers: { 'X-User-Id': '10' } });
    const body = await resp.json();
    record('步骤12 本人删除视频', input, body);
    expect(body.code).toBe(200);
  });

  test('步骤13 删除后推荐列表不再包含', async ({ request }) => {
    const resp = await request.get('/api/videos/recommend?page=1&pageSize=12');
    const body = await resp.json();
    record('步骤13 删除后推荐列表', { method: 'GET', url: '/api/videos/recommend?page=1&pageSize=12' }, body);
    expect(body.code).toBe(200);
    expect(body.data.map((v) => v.id)).not.toContain(state.videoId);
  });
});
