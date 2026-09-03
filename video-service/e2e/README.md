# video-service 端到端测试（Playwright + 真实环境）

这是 **真实的端到端测试**：不是 mock，而是让 Playwright 打真实运行的 video-service
（连接真实 MySQL `video_db` 和真实 MinIO `hangyin-video` 桶），从上传一路走到删除，
完整覆盖 UC-01 上传、UC-02 管理、点赞、评论、弹幕、播放等业务场景。

**关键特性：每一步都把「原始输入（请求）」和「真实输出（响应）」写入
`report/e2e-report.md`**，作为测试报告的证据；CI 会把该报告和 Playwright HTML 报告一起上传。

## 测试链路

上传视频 → 推荐列表 → 视频详情 → 用户列表 → 点赞 → 发表评论 → 评论列表 →
发送弹幕 → 播放计数 → 真实访问 MinIO 文件 → 越权删除(403) → 本人删除 → 删除后列表校验

## 本地运行

需要先准备好真实环境：

1. MySQL 里建好 `video_db` 并执行 `../sql/001_video_schema.sql`
2. 启动 MinIO（如 `docker run -p 9000:9000 -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin minio/minio server /data`）
3. 启动 video-service（参考 `../README.md` 的 Local Run，确保端口 8082）

然后：

```powershell
cd e2e
npm install
npx playwright install chromium
npx playwright test
```

跑完看 `report/e2e-report.md`（输入输出报告）和 `playwright-report/`（HTML 报告）。

## CI 运行

当前仓库的统一工作流是根目录 `.github/workflows/uc03-microservices-cicd.yml`。本目录测试可作为
video-service 独立环境的完整回归入口；微服务 CI 使用 Compose 联调并执行网关 HTTP 和前端
浏览器 E2E，相关日志和 Playwright 报告由 `microservices-ci` Artifact 保存。不要再引用已移除的
旧的 `video-service-ci.yml` 已不属于当前分支。
