# 概要设计说明

## 1. 组件划分

系统由前端、业务后端、直播服务、关系数据库、对象存储和流媒体服务组成。直播服务只管理直播域数据；主 backend 管理用户、视频、评论、关注等业务。

| 组件 | 技术 | 责任 | 部署 |
| --- | --- | --- | --- |
| frontend | Vue 3 + Nginx | 页面、API/WebSocket/SRS 反向代理 | Kubernetes Deployment |
| backend | Spring Boot + MyBatis-Plus | 用户、视频、评论、关注、MinIO 文件 | Kubernetes Deployment |
| live-service | Spring Boot + JDBC/WebSocket | 直播间、直播弹幕、直播点赞 | Kubernetes Deployment |
| mysql | MySQL 8 | 主业务库 | PVC-backed Deployment |
| live-mysql | MySQL 8 | 直播业务库 | PVC-backed Deployment |
| minio | MinIO | 视频对象存储 | PVC-backed Deployment |
| srs | SRS 5 | RTMP 接入、HTTP-FLV、SRS API | Kubernetes Deployment |

## 2. 服务接口与数据归属

| 服务 | 主要接口 | 负责表 |
| --- | --- | --- |
| backend | `/api/user/**`、`/api/videos/**`、`/api/minio/**` | `sys_user`、`video`、`user_video`、`user_follow`、`view_history`、`comment`、`danmaku` |
| live-service | `/api/live/**`、`/ws/live/**` | `live_room`、`live_danmu`、`room_likes` |
| SRS | `/api/v1/versions`、RTMP 1935、HTTP 8080 | 不持久化业务表 |

跨服务访问通过 HTTP/API 或 SRS 协议完成；禁止跨服务直接查询对方数据库表。当前 live-service 的 SRS 可达性通过 `LIVE_SRS_API_BASE_URL` 探测，探测失败时返回 `reachable=false`，不让业务进程崩溃。

## 3. 部署拓扑

正式模型源文件见 [`models/system-context.mmd`](models/system-context.mmd)。Kubernetes 清单位于 [`k8s/`](../k8s/)，CI 使用 `helm/kind-action` 创建一次性集群，PVC 使用 `local-path` provisioner。

## 4. 设计约束

- 镜像使用 `${{ github.sha }}` 版本号，禁止以 `latest` 作为发布版本。
- Secret 由流水线运行时生成，不提交口令或 Token。
- MySQL/MinIO 数据目录必须挂载 PVC；开发环境可使用 local-path，生产环境应替换为高可用存储。
- 前端通过 Nginx 将 `/api/live/`、`/ws/live/`、`/srs/` 分别代理至 live-service 和 SRS。
