# 微服务完整项目运行说明

当前项目由旧 Vue 前端、API 网关、用户服务、视频服务和直播服务组成。统一编排文件为 `docker-compose.microservices.yml`，旧 `backend/` 目录仅保留作历史代码，不参与微服务启动链路。

## 启动

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-microservices-local.ps1
```

启动脚本会为当前 PowerShell 进程生成本地临时密码。若需要在多个终端中执行 Compose 命令，请先在当前终端设置 `MYSQL_ROOT_PASSWORD`、`MYSQL_VIDEO_PASSWORD`、`MYSQL_LIVE_PASSWORD` 和 `MINIO_ROOT_PASSWORD`，不要把这些值提交到仓库。

脚本会启动三个独立数据库（用户/视频/直播）、MinIO、SRS、三个微服务和网关。前端使用本地开发模式运行：

```powershell
Set-Location frontend/demo
npm install
npm run dev
```

访问前端：`http://127.0.0.1:5173`。前端所有 API 通过网关 `http://127.0.0.1:8080` 转发。

## 查看状态和日志

```powershell
docker compose -f docker-compose.microservices.yml -p hangyin-microservices ps
docker compose -f docker-compose.microservices.yml -p hangyin-microservices logs -f user-service
docker compose -f docker-compose.microservices.yml -p hangyin-microservices logs -f video-service
docker compose -f docker-compose.microservices.yml -p hangyin-microservices logs -f live-service
```

## 停止

```powershell
powershell -ExecutionPolicy Bypass -File scripts/stop-microservices-local.ps1
```

首次启动会初始化 MySQL、MinIO 和 SRS 数据卷；已有数据卷不会重复执行初始化脚本。
