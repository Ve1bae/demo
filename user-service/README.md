# user-service

用户中心微服务，负责 `sys_user`、`user_follow` 和 `user_interest`。

默认端口为 `8081`，默认连接 `user_db`。接口前缀为 `/api/user`，供其他服务查询用户基本信息的内部接口为 `/api/user/internal/{userId}`。

本服务从 `docker` 分支独立出来后先与单体并行运行。完成历史数据迁移和网关切换后，再移除单体中的用户领域代码。

全新 MySQL 数据卷会按 Compose 中的 `01`、`02`、`03` 脚本自动初始化并迁移用户数据。已有数据卷不会重复执行初始化脚本，需要在停机窗口手动执行 `infra/mysql/migrate-user-db.sql`，确认数据一致后再切换网关。

## 测试

执行 API 契约测试：`..\backend\mvnw.cmd -B test`。

执行真实 HTTP 端到端测试：`powershell -ExecutionPolicy Bypass -File scripts/run-user-service-e2e.ps1`。脚本会启动 MySQL 与用户服务，调用全部用户公开接口和健康/信息端点，并生成 `target/e2e-reports/user-service-e2e-report.md` 与服务日志。
