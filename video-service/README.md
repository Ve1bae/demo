# Video Service

视频点播服务，负责视频上传、MinIO 对象存储、点播播放、评论、点播弹幕、播放历史与点赞收藏。技术栈与 `user-service` 保持一致：**Spring Boot 4 + MyBatis Plus + Lombok**。服务只维护自己的数据表：`video`、`comment`、`comment_like`、`danmaku`、`view_history`、`user_video`。用户身份由网关/JWT 解析后通过 `X-User-Id` 传入，服务不读取用户中心表。

## API

| 方法 | 接口 | 说明 |
| --- | --- | --- |
| POST | `/api/videos/upload` | 上传视频（multipart: file/title/description/coverUrl/tags/author/userId/duration） |
| GET | `/api/videos/recommend` | 推荐视频列表，支持分页/分类/关键词 |
| GET | `/api/videos/{videoId}` | 视频详情 |
| GET | `/api/videos/user/{userId}/uploads` | 某用户上传的视频列表 |
| DELETE | `/api/videos/{videoId}` | 删除自己的视频（软删除） |
| POST | `/api/videos/{videoId}/visibility` | 公开 / 仅自己可见 |
| POST | `/api/videos/{videoId}/play` | 播放计数 + 记录观看历史 |
| POST / DELETE | `/api/videos/{videoId}/likes` | 点赞 / 取消点赞 |
| POST / DELETE | `/api/videos/{videoId}/favorites` | 收藏 / 取消收藏 |
| GET | `/api/videos/{videoId}/status` | 当前用户对该视频的点赞/收藏状态 |
| GET | `/api/videos/{videoId}/comments` | 评论列表（分页） |
| POST | `/api/videos/{videoId}/comments` | 发表评论 |
| POST | `/api/comments/{commentId}/likes` | 点赞评论 |
| GET | `/api/videos/{videoId}/danmakus` | 点播弹幕列表 |
| POST | `/api/videos/{videoId}/danmakus` | 发送点播弹幕 |
| GET | `/api/minio/test` | 检查 MinIO 连接 |
| POST | `/api/minio/upload` | 上传任意文件到 MinIO |
| GET | `/api/minio/url` | 生成对象访问地址 |
| DELETE | `/api/minio/delete` | 删除 MinIO 对象 |

上传视频：

```http
POST /api/videos/upload
Content-Type: multipart/form-data

file=<视频文件>
title=校园歌手大赛回放
userId=10
```

删除自己的视频（非本人返回 `403`）：

```http
DELETE /api/videos/123
X-User-Id: 10
```

## 技术栈与分层

- **数据访问**：MyBatis Plus（`mapper/` + `entity/`），`@MapperScan("com.example.video.mapper")`
- **服务层**：`VideoService`（接口 + `impl/VideoServiceImpl`）、`CommentService`、`DanmakuService`
- **控制器**：`VideoController`、`CommentController`、`DanmakuController`、`MinioController`
- **通用**：`ApiResponse`、`PageResult`（Lombok）
- **外部组件**：MinIO 对象存储（桶自动创建并允许匿名读）、FFmpeg 转码（默认关闭）

## Local Run

需要 Java 21、MySQL 和 MinIO。数据库初始化脚本为 `sql/001_video_schema.sql`（库名 `video_db`）：

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3307/video_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME = "root"
$env:SPRING_DATASOURCE_PASSWORD = "<local-password>"
$env:MINIO_ENDPOINT = "http://localhost:9000"
$env:MINIO_PUBLIC_BASE_URL = "http://localhost:8082/video"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_BUCKET = "hangyin-video"
mvn spring-boot:run
```

FFmpeg 转码默认关闭（`VIDEO_TRANSCODE_ENABLED=false`），未安装 FFmpeg 时只保存原始文件。

## Docker Compose

Compose 启动独立 MySQL、MinIO 和视频服务：

```powershell
$env:VIDEO_DB_PASSWORD = [guid]::NewGuid().ToString("N")
docker compose -f compose.yml up -d --build
Invoke-RestMethod http://127.0.0.1:8082/actuator/health
```

## 测试

- **单元/API 测试**：`src/test/java/...`（Mockito + standaloneSetup MockMvc，无需外部依赖）
- **真实端到端测试**：`e2e/`（Playwright，走真实 MySQL + MinIO + 后端，报告记录每一步输入输出）
- **CI/CD**：根目录 `.github/workflows/uc03-microservices-cicd.yml`（三个服务单元测试、Compose 联调、HTTP/浏览器 E2E、版本化镜像和 Kind Kubernetes 部署）；旧的 `video-service-ci.yml` 不属于当前交付分支。

## Kubernetes and CI

`k8s/` 包含 `video-mysql`、`video-service`、Service、资源限制和健康探针，数据库密码通过 Secret 注入，建表 SQL 通过 ConfigMap 挂载。根目录微服务 CI 会执行单元/API 测试、Compose 联调、真实 E2E（Playwright），并在 push 到 `microservice` 或 `feature/**` 分支后构建版本化镜像、部署到 Kind 和执行健康检查。
