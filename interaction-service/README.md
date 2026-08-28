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

## Docker Compose

Compose 会构建带版本号的互动服务镜像，启动独立 MySQL 8.0 容器，并自动执行 `sql/001_interaction_schema.sql`。密码只通过当前终端的环境变量传入，不写入仓库：

```powershell
$env:INTERACTION_DB_PASSWORD = [guid]::NewGuid().ToString("N")
$env:IMAGE_TAG = "0.1.0"
docker compose -f compose.yml up -d --build
docker compose -f compose.yml ps
Invoke-RestMethod http://127.0.0.1:8090/actuator/health
```

本地 Compose 默认使用阿里云 Maven 公共镜像完成容器内构建；GitHub Actions 的 `docker build` 默认使用 Maven Central。需要切换时可设置 `MAVEN_MIRROR_URL`。

停止服务：

```powershell
docker compose -f compose.yml down
```

需要同时删除本地测试数据库卷时，使用 `docker compose -f compose.yml down -v`。该操作会删除 Compose 创建的互动服务测试数据。

## Kubernetes

Kubernetes 清单包含 MySQL、互动服务、Service、资源限制以及启动/就绪/存活探针。部署前创建临时 Secret 和数据库初始化 ConfigMap：

```sh
kubectl create secret generic interaction-db-secret \
  --from-literal=password="$(openssl rand -hex 24)"
kubectl create configmap interaction-db-init \
  --from-file=001_interaction_schema.sql=sql/001_interaction_schema.sql
kubectl apply -f k8s/mysql.yml
kubectl rollout status deployment/interaction-mysql
kubectl apply -f k8s/interaction-service.yml
kubectl apply -f k8s/interaction-service-service.yml
kubectl rollout status deployment/interaction-service
```

`k8s/mysql.yml` 使用 `emptyDir`，用于课程流水线和本地 Kind 的可重复部署验证；正式环境应将数据库替换为持久化 MySQL 或把数据卷改为 PVC。

GitHub Actions 会严格按以下顺序运行：测试和打包、使用 Git SHA 构建版本化镜像、部署到临时 Kind 集群、健康检查、发布动态 API 冒烟测试。前一步失败时，后续部署不会执行。
