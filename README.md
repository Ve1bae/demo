# 航音视频 - Hangyin Video

航音视频是一个支持视频播放、弹幕、评论和直播互动的 Web 应用。

## 快速启动（Docker Compose）

### 前置条件
- Docker 24+
- Docker Compose v2+

### 一键启动
```bash
git clone https://github.com/Ve1bae/demo.git
cd demo
docker compose up -d
```

首次启动 MySQL 会自动执行 `demo.sql`（建表）和 `backend/src/test/resources/data.sql`（测试数据）。

### 访问地址
| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8080 |
| 后端 API | http://localhost:9090 |
| MinIO Console | http://localhost:9001 (minioadmin / minioadmin) |
| SRS HTTP API | http://localhost:1985 |
| MinIO 文件代理 | http://localhost:8082 |

### 常用命令
```bash
docker compose ps          # 查看运行状态
docker compose logs -f backend  # 查看后端日志
docker compose down -v     # 停止并清理数据卷
docker compose restart backend  # 重启后端
```

## 本地开发

### 前置条件
- JDK 21+
- Node 22+
- MySQL 8.0+（用户 root，密码 root，数据库 hangyin_video）

### 数据库初始化
```bash
mysql -u root -proot -e "CREATE DATABASE hangyin_video;"
mysql -u root -proot hangyin_video < demo.sql
```

### 后端
```bash
cd backend
./mvnw spring-boot:run
# Windows: .\mvnw.cmd spring-boot:run
```

### 前端
```bash
cd frontend/demo
npm ci
npm run dev
```

### 运行测试
```bash
cd backend
./mvnw test
# Windows: .\mvnw.cmd test
```

## Kubernetes 部署

### 前置条件
- kubectl
- kind 或 Minikube
- 已登录 ghcr.io（`docker login ghcr.io`）

### 构建并推送镜像
```bash
# 构建
docker build -t ghcr.io/ve1bae/demo-backend:local ./backend
docker build -t ghcr.io/ve1bae/demo-frontend:local ./frontend/demo

# 推送
docker push ghcr.io/ve1bae/demo-backend:local
docker push ghcr.io/ve1bae/demo-frontend:local
```

### 部署到 kind
```bash
kind create cluster --name hangyin
kubectl apply -f k8s/
kubectl get pods -n hangyin
kubectl get svc -n hangyin
```

### 健康检查
```bash
kubectl port-forward svc/backend-service 9090:9090 -n hangyin
curl http://localhost:9090/api/videos/recommend
```

## CI/CD 流水线

push 到 `videoplayer` 或 `main` 分支会触发 `.github/workflows/ci-test.yml`：

1. **运行后端测试** — MySQL service + Maven test（47 个测试）
2. **构建镜像** — backend/frontend 镜像推送 GHCR，tag 为 commit SHA
3. **部署到 kind** — 临时 K8s 集群，kubectl apply
4. **健康检查** — curl 验证后端 API

任何一步失败，后续步骤不会继续。

## 目录结构
```
demo/
├── backend/              # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── frontend/demo/        # Vue 3 + Vite 前端
│   ├── Dockerfile
│   ├── package.json
│   └── src/
├── k8s/                  # Kubernetes 部署文件
├── .github/workflows/    # CI/CD 工作流
├── demo.sql              # 数据库建表脚本
├── docker-compose.yml    # 一键本地运行
└── README.md
```

## 测试报告

CI 完成后，在 GitHub Actions 页面下载 Artifacts 中的 `surefire-reports`，解压后打开 `index.html` 查看。

本地测试报告：`backend/target/surefire-reports/index.html`
