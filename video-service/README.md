# video-service

UC-03 视频推荐的独立微服务实现，当前提供推荐接口：

`GET /api/videos/recommend?page=1&pageSize=12&categoryId=0&keyword=`

登录用户通过 `X-User-Id` 请求头传递用户编号。视频和观看历史只从 video-service 自己的数据库读取；关注作者和兴趣标签通过 `UserPreferenceClient` 从 user-service 获取。未配置 user-service 时使用游客偏好降级，不跨库查询用户表。

## 本地运行

配置 MySQL 连接环境变量后启动：

```text
DB_URL=jdbc:mysql://localhost:3306/video_db
DB_USERNAME=root
DB_PASSWORD=******
```

数据库表结构见 `src/main/resources/schema.sql`，MySQL 部署脚本见 `sql/003_recommendation_schema.sql`。

默认不调用尚未联调的用户服务，登录请求会降级为游客偏好。用户服务接口就绪后，同时设置
`USER_SERVICE_ENABLED=true` 和 `USER_SERVICE_BASE_URL` 才会启用 HTTP 偏好查询。用户服务容器默认地址为
`http://user-service:8081`；偏好接口路径和返回字段以组内最终契约为准。

## Docker 与真实 MySQL API 测试

PowerShell 中设置仅用于本机的数据库密码，然后启动隔离测试环境：

```powershell
$env:VIDEO_SERVICE_DB_PASSWORD = "<本机测试密码>"
docker compose -f compose.test.yml up -d --build
node scripts/api-smoke-test.mjs
docker compose -f compose.test.yml down -v
```

服务地址为 `http://127.0.0.1:8083`，健康检查地址为
`http://127.0.0.1:8083/actuator/health`。API 测试的原始请求和响应保存在
`target/api-test-results/api-results.json`。

## 验证

```text
mvn -B -ntp test
```

测试包含推荐服务单元测试、控制器单元测试和基于 H2 的 API 集成测试，共 33 项。
