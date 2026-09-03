# Live Service

直播服务负责直播间生命周期、SRS 推拉流地址、直播弹幕历史、WebSocket 实时广播和直播点赞。服务只维护自己的三张表：`live_room`、`live_danmu`、`room_likes`。用户身份由网关/JWT 解析后通过 `X-User-Id` 传入，服务不读取用户中心表。

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/live/rooms` | 创建并开始直播间 |
| GET | `/api/live/rooms` | 查询在线直播间，支持分页和分类 |
| GET | `/api/live/rooms/{roomId}` | 查询直播间和推拉流地址 |
| POST | `/api/live/rooms/{roomId}/close` | 主播结束自己的直播 |
| GET | `/api/live/rooms/{roomId}/danmus` | 查询弹幕历史 |
| GET | `/api/live/rooms/{roomId}/like` | 查询累计点赞数 |
| GET | `/api/live/srs/health` | 查询 SRS API 探活结果 |
| WS | `/ws/live/{roomId}` | 在线人数、弹幕和点赞实时广播 |

创建直播间：

```http
POST /api/live/rooms
X-User-Id: 10
Content-Type: application/json

{"title":"测试直播间","categoryId":1}
```

WebSocket 消息：

```json
{"type":"danmu","userId":10,"username":"主播","content":"晚上好","color":"#ffffff"}
{"type":"like","userId":11}
```

服务端会广播 `online_count`、`danmu`、`like` 和 `error` 消息。弹幕和点赞都要求有效的 `userId`，不能依赖客户端任意声明的匿名身份。

## Local Run

需要 Java 21 和 MySQL。数据库初始化脚本为 `sql/001_live_schema.sql`：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/live_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<local-password>"
$env:LIVE_SRS_RTMP_BASE_URL = "rtmp://localhost:1935/live"
$env:LIVE_SRS_HTTP_BASE_URL = "http://localhost:8081"
$env:LIVE_SRS_API_BASE_URL = "http://localhost:1985"
$env:LIVE_SRS_PROBE_ENABLED = "true"
mvn spring-boot:run
```

SRS 探活默认关闭；接入 SRS API 后设置 `LIVE_SRS_PROBE_ENABLED=true`。关闭时服务仍会生成播放地址，便于等待推流后重试。

## Docker Compose

Compose 启动独立 MySQL 和直播服务，密码只通过环境变量传入：

```powershell
$env:LIVE_DB_PASSWORD = [guid]::NewGuid().ToString("N")
$env:IMAGE_TAG = "0.1.0"
docker compose -f compose.yml up -d --build
docker compose -f compose.yml ps
Invoke-RestMethod http://127.0.0.1:8090/actuator/health
```

默认 SRS 地址指向宿主机；可通过 `LIVE_SRS_*` 环境变量覆盖。停止服务：

```powershell
docker compose -f compose.yml down
```

## Kubernetes and CI

`k8s/` 包含 `live-mysql`、`live-service`、Service、资源限制和健康探针。数据库密码通过运行时创建的 Kubernetes Secret 注入，数据库初始化 SQL 通过 ConfigMap 挂载。示例：

```sh
kubectl create secret generic live-db-secret --from-literal=password="$(openssl rand -hex 24)"
kubectl create configmap live-db-init --from-file=001_live_schema.sql=sql/001_live_schema.sql
kubectl apply -f k8s/mysql.yml
kubectl rollout status deployment/live-mysql
kubectl apply -f k8s/live-service.yml
kubectl apply -f k8s/live-service-service.yml
kubectl rollout status deployment/live-service
```

课程 CI 会执行 JUnit 单元测试和 API/WebSocket 集成测试，构建版本化镜像，部署到临时 Kind 集群，并验证健康接口、直播间创建、播放地址和点赞查询。Kubernetes 示例中的 MySQL 使用 `emptyDir`，正式环境应替换为 PVC、托管 MySQL 或其他持久化方案。

## 专项压力、扩缩容和故障恢复

完整的直播服务非功能测试说明位于根目录 `doc/live-service-专项非功能测试说明-20260903.md`。

准备一个 `online` 状态的直播间后，可执行 REST 与 WebSocket 压力测试：

```powershell
Set-Location live-service
.\pressure\run-pressure.ps1 -BaseUrl http://127.0.0.1:8090 -RoomId <online-room-id> -Runs 3
```

Kubernetes HPA 专项负载和 Pod 故障恢复入口分别为：

```powershell
.\scripts\live-service-hpa-load-test.ps1 -Namespace hangyin
.\scripts\live-service-fault-drill.ps1 -Namespace hangyin
```

微服务 CI 使用根目录中的 `scripts/hpa-smoke.sh` 和 `scripts/fault-handling-smoke.sh` 执行同类专项实验，并上传原始 HPA 采样、请求统计、故障场景 JSON 和恢复日志。

压力测试结果写入 `live-service/pressure-results/`，HPA 和故障演练结果写入 `evidence/`。只有保存原始输出并观察到副本变化或实例恢复，才能在报告中标记为“实测通过”；脚本和配置本身不能替代实测证据。
