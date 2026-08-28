# GitHub Actions 浏览器 E2E 错误修复报告

## 1. 问题信息

| 项目 | 内容 |
|---|---|
| 分支 | `uc07cicd` |
| 运行 | CI/CD Run 35 |
| 提交 | `169a924` |
| 失败任务 | `Frontend build and browser E2E` |
| 失败步骤 | `Run browser E2E`，步骤 12 |
| 现象 | 后端测试和 HTTP E2E 通过，浏览器 E2E 退出码为 1；镜像构建和 Kubernetes 部署因依赖失败被跳过 |
| 日志情况 | 已读取用户提供的 Run 35 日志 |

## 2. Run 35 真实失败结果

### UC03 字符集错误

页面收到的标题和昵称为乱码，例如：

```text
UC03 E2E çƒ­é—¨æŽ¨è
E2EæŽ¨èç”¨æˆ·
```

因此 `E2E-TC-03-01`、`E2E-TC-03-02`、`E2E-TC-03-03`、`E2E-TC-03-05` 失败。根因是 GitHub Runner 中 mysql 客户端导入 fixture 时未指定 `utf8mb4`，中文 SQL 被错误解释。

### UC07 页面时序问题

`主播通过页面创建直播间并看到推流信息` 在本次提供的日志中连续失败。日志显示按钮实际文本为 `关闭直播`，不是 `开始直播`。根因是此前 UC07-10 测试已用用户 `1` 创建了在线直播间，后续创建测试复用该用户，页面正确进入“关闭直播”状态。

## 3. 已确认的排查结果

1. 后端单元、API、WebSocket 测试通过。
2. HTTP E2E UC04-06 和 UC07 通过。
3. 浏览器测试共 16 项，包含 UC03、UC04-06、UC07-10。
4. 浏览器 fixture 中已创建用户 `1`、`930001-930004`，并清理重复执行产生的评论、弹幕、视频关系和直播间数据。
5. UC04 浏览器播放使用前端静态目录中的 WebM 测试视频，避免依赖 SRS 或不存在的远程媒体地址。

## 4. 本次修复

### 4.1 修复 MySQL 导入字符集

HTTP 和浏览器 fixture 导入命令均增加：

```bash
--default-character-set=utf8mb4
```

### 4.2 修复 UC07 测试时序

UC07 页面创建测试改用独立 fixture 用户 `930004`，避免被前置测试创建的用户 `1` 在线直播间污染；同时保留点击前等待按钮文本为 `开始直播`，确保页面已完成 `/live` 路由同步。

fixture 同时清理 `930001-930004` 用户的直播间、点赞和弹幕数据，支持重复运行。

### 4.3 拆分浏览器测试步骤

原先 16 个用例集中在一个 `Run browser E2E` 步骤中，任一用例失败都只能看到统一退出码。现已拆为：

- `Run UC03 browser E2E`
- `Run UC04-06 browser E2E`
- `Run UC07-10 browser E2E`

下一次运行会直接显示失败所属用例组和对应 Playwright 报告。

### 4.4 固定前端 API 地址

在前端 job 中显式设置：

```yaml
VITE_API_HOST: 127.0.0.1
VITE_API_PORT: '8080'
```

避免 Runner 环境变量、主机名解析或前端开发服务器继承环境差异造成浏览器请求错误地址。

### 4.5 媒体和测试数据修复

- 浏览器视频地址使用 `frontend/demo/public/e2e/hot.webm`。
- 登录互动测试使用 fixture 中存在的用户 ID `1`。
- fixture 可重复导入，避免播放量、评论和弹幕污染后续断言。

## 5. 验证结果

- `npm run build`：通过。
- `npm run test:e2e -- --list`：识别 16 项测试。
- `docker compose config --quiet`：通过。
- 本机完整运行受 Docker Hub TLS 证书错误阻断，无法启动 `mysql:8.4`；该环境问题不影响 GitHub Runner。

## 6. 后续判定标准

提交本次修改后重新运行流水线：

- 三个浏览器测试步骤全部通过，才允许继续构建镜像和部署 Kubernetes。
- 任一浏览器测试步骤失败时，依据独立步骤名称和 `browser-e2e-reports` artifact 定位具体用例。
- `SRS 推流后直播间能播放` 在未设置 `RUN_SRS_E2E=true` 时保持跳过，这是预期行为。
