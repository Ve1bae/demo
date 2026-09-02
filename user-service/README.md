# user-service

用户中心微服务，负责 `sys_user`、`user_follow` 和 `user_interest`。

默认端口为 `8081`，默认连接 `user_db`。接口前缀为 `/api/user`，供其他服务查询用户基本信息的内部接口为 `/api/user/internal/{userId}`。

推荐偏好接口同时兼容 `/api/user/{userId}/preferences` 和 `/api/users/{userId}/preferences`，返回 `followedAuthorIds`（关注作者 ID 集合）、`interests`（兴趣标签及分数）和 `viewedVideoIds`（当前服务不持有视频观看记录，返回空集合）。视频服务可直接调用复数路径获取偏好；用户不存在时返回业务码 `404`。

本服务从 `docker` 分支独立出来后先与单体并行运行。完成历史数据迁移和网关切换后，再移除单体中的用户领域代码。

全新 MySQL 数据卷会按 Compose 中的 `01`、`02`、`03` 脚本自动初始化并迁移用户数据。已有数据卷不会重复执行初始化脚本，需要在停机窗口手动执行 `infra/mysql/migrate-user-db.sql`，确认数据一致后再切换网关。

## 测试

执行 API 契约测试：`..\backend\mvnw.cmd -B test`。

执行真实 HTTP 端到端测试：`powershell -ExecutionPolicy Bypass -File scripts/run-user-service-e2e.ps1`。脚本会启动 MySQL 与用户服务，调用全部用户公开接口和健康/信息端点，并生成 `target/e2e-reports/user-service-e2e-report.md` 与服务日志。

本地常驻部署：`powershell -ExecutionPolicy Bypass -File scripts/deploy-user-service-local.ps1`，服务地址为 `http://127.0.0.1:18082`；查看状态使用 `docker compose -f docker-compose.user-service-e2e.yml -p user-service-local ps`，停止使用 `scripts/stop-user-service-local.ps1`。

## 运行状态与排障

部署完成后运行 `powershell -ExecutionPolicy Bypass -File scripts/inspect-user-service-local.ps1`，报告生成在 `target/deployment-reports/user-service-local-status.md`，其中包含容器状态、存活检查、就绪检查、版本信息及最近 100 行服务日志。

单项检查地址：存活检查 `http://127.0.0.1:18082/actuator/health`，就绪检查 `http://127.0.0.1:18082/actuator/health/readiness`，版本信息 `http://127.0.0.1:18082/actuator/info`。部署失败时，先运行状态脚本查看报告中的容器状态和日志；若 MySQL 未健康，检查数据库容器日志；若服务未就绪，检查用户服务日志中的数据库连接、表初始化和端口占用错误。
