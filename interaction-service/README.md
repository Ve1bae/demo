# Interaction Service

互动服务负责社区动态、动态中的用户提及和提醒。服务只维护自己的表，不直接读取用户服务的 `sys_user` 表；用户身份由网关或调用方通过 `X-User-Id` 传入。

## Local Run

需要 Java 21 和 MySQL。先执行 `sql/001_interaction_schema.sql`，再设置数据库连接环境变量：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/hangyin_video?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<local-password>"
mvn spring-boot:run
```

服务默认监听 `8090`，健康检查地址为 `GET /actuator/health`（当前服务的最小版本也可以用 `GET /api/interactions/dynamics` 验证应用是否启动）。

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/interactions/dynamics` | 发布动态，同时为被提及用户创建提醒 |
| GET | `/api/interactions/dynamics` | 查询动态广场 |
| GET | `/api/interactions/users/{userId}/dynamics` | 查询用户动态 |
| GET | `/api/interactions/notifications` | 查询当前用户提醒，支持 `unreadOnly` |
| GET | `/api/interactions/notifications/unread-count` | 查询未读提醒数 |
| POST | `/api/interactions/notifications/{id}/read` | 将提醒标记为已读 |

发布动态示例：

```http
POST /api/interactions/dynamics
X-User-Id: 10
Content-Type: application/json

{
  "content": "欢迎来看直播 @用户11",
  "mentionedUserIds": [11]
}
```

服务会去重提及用户、忽略作者提及自己，并为每个有效的被提及用户写入一条 `MENTION` 类型未读提醒。
