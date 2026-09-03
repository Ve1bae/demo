import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const baseUrl = (process.env.USER_SERVICE_E2E_BASE_URL || 'http://127.0.0.1:18081').replace(/\/+$/, '');
const healthUrl = (process.env.USER_SERVICE_E2E_HEALTH_URL || `${baseUrl}/actuator/health`).replace(/\/+$/, '');
const reportPath = resolve(process.env.USER_SERVICE_E2E_REPORT || 'user-service/target/e2e-reports/user-service-e2e-report.md');
const suffix = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
const alice = { username: `e2e_alice_${suffix}`, password: 'e2e-password', nickname: `E2E Alice ${suffix}` };
const bob = { username: `e2e_bob_${suffix}`, password: 'e2e-password', nickname: `E2E Bob ${suffix}` };
const cases = [];

function redact(value) {
  if (value && typeof value === 'object') {
    const copy = Array.isArray(value) ? [...value] : { ...value };
    if ('password' in copy) copy.password = '***';
    return copy;
  }
  return value;
}

async function request(name, method, path, { body, headers = {}, verify } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await response.text();
  let output;
  try { output = text ? JSON.parse(text) : null; } catch { output = text; }
  const passed = response.status === 200 && (!verify || verify(output));
  cases.push({ name, method, path, input: { body: redact(body), headers }, status: response.status, output, passed });
  if (!passed) throw new Error(`${name} failed: HTTP ${response.status}`);
  return output;
}

async function writeReport(error) {
  await mkdir(dirname(reportPath), { recursive: true });
  const lines = [
    '# user-service 端到端测试报告',
    '',
    `- 执行时间：${new Date().toISOString()}`,
    `- 服务地址：${baseUrl}`,
    `- 结果：${error ? '失败' : '通过'}`,
    '',
    '## 测试链路',
    '',
    '健康检查 -> 注册 Alice/Bob -> 登录 Alice -> 用户资料 -> 修改头像 -> 关注 Bob -> 查询关注/粉丝 -> 内部查询 -> 取消关注 -> 再次查询关注列表。',
    '',
    '## 用例明细',
    ''
  ];
  for (const item of cases) {
    lines.push(`### ${item.name}`);
    lines.push(`- 请求：\`${item.method} ${item.path}\``);
    lines.push(`- 输入：\`${JSON.stringify(item.input)}\``);
    lines.push(`- HTTP 状态：${item.status}`);
    lines.push(`- 输出：\`${JSON.stringify(item.output)}\``);
    lines.push(`- 断言：${item.passed ? '通过' : '失败'}`);
    lines.push('');
  }
  if (error) lines.push(`失败原因：${error.message}`);
  await writeFile(reportPath, `${lines.join('\n')}\n`, 'utf8');
}

try {
  const healthResponse = await fetch(healthUrl);
  const healthText = await healthResponse.text();
  const healthData = healthText ? JSON.parse(healthText) : null;
  cases.push({ name: 'E2E-01 健康检查', method: 'GET', path: healthUrl, input: { headers: {} }, status: healthResponse.status, output: healthData, passed: healthResponse.status === 200 && healthData?.status === 'UP' });
  if (!cases.at(-1).passed) throw new Error('E2E-01 健康检查失败');
  if (process.env.USER_SERVICE_E2E_SKIP_INFO !== 'true') await request('E2E-02 服务信息', 'GET', '/actuator/info');
  await request('E2E-03 注册 Alice', 'POST', '/api/user/register', { body: alice, verify: data => data.code === 200 });
  await request('E2E-04 注册 Bob', 'POST', '/api/user/register', { body: bob, verify: data => data.code === 200 });
  const login = await request('E2E-05 登录 Alice', 'POST', '/api/user/login', { body: alice, verify: data => data.code === 200 && data.data?.id });
  const aliceId = login.data.id;
  const bobLogin = await request('E2E-06 登录 Bob', 'POST', '/api/user/login', { body: bob, verify: data => data.code === 200 && data.data?.id });
  const bobId = bobLogin.data.id;
  await request('E2E-07 查询用户资料', 'GET', `/api/user/${aliceId}/profile?viewerId=${aliceId}`, { verify: data => data.data?.username === alice.username });
  const avatarUrl = `https://example.test/e2e/${suffix}.png`;
  await request('E2E-08 修改头像', 'PUT', `/api/user/${aliceId}/avatar`, { body: { avatarUrl }, verify: data => data.data?.avatarUrl === avatarUrl });
  await request('E2E-09 关注用户', 'POST', `/api/user/${bobId}/follow`, { headers: { 'X-User-Id': String(aliceId) }, verify: data => data.code === 200 });
  await request('E2E-10 查询关注列表', 'GET', `/api/user/${aliceId}/following`, { verify: data => data.data?.some(user => user.id === bobId) });
  await request('E2E-11 查询粉丝列表', 'GET', `/api/user/${bobId}/followers`, { verify: data => data.data?.some(user => user.id === aliceId) });
  await request('E2E-12 内部用户查询', 'GET', `/api/user/internal/${aliceId}`, { verify: data => data.data?.id === aliceId });
  await request('E2E-13 取消关注', 'DELETE', `/api/user/${bobId}/follow`, { headers: { 'X-User-Id': String(aliceId) }, verify: data => data.code === 200 });
  await request('E2E-14 验证取消关注', 'GET', `/api/user/${aliceId}/following`, { verify: data => !data.data?.some(user => user.id === bobId) });
  await writeReport();
  console.log(`user-service E2E passed: ${reportPath}`);
} catch (error) {
  await writeReport(error);
  console.error(`user-service E2E failed: ${reportPath}`);
  throw error;
}
