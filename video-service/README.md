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

## 验证

```text
mvn -B -ntp test
```

测试包含推荐服务单元测试、控制器单元测试和基于 H2 的 API 集成测试，共 33 项。
