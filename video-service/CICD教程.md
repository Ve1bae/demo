# Video Service CI/CD 教程

## 一、CI/CD 流水线概述

### 触发条件
- 推送到 `video-service` 分支且改动 `video-service/**` 路径下的文件
- 对 `video-service/**` 路径发起 Pull Request
- 手动触发 (workflow_dispatch)

### 流水线阶段

```
代码提交 → 安装 JDK 21 → 运行测试 (H2, 46 个) → 构建 Docker 镜像
    → 创建 Kind K8s 集群 → 部署 MySQL + video-service → API 冒烟测试 → 上传测试报告
```

### 文件位置
- CI 配置: `.github/workflows/video-service-ci.yml`
- K8s 部署: `video-service/k8s/`
- Dockerfile: `video-service/Dockerfile`

---

## 二、本地运行 CI 步骤

### 1. 运行测试 (本地)

```powershell
cd video-service
.\mvnw.cmd -B -ntp clean verify
```

测试使用 H2 内存数据库，不需要 MySQL。共 46 个测试：
- UC-04 播放视频: 15 个 (单元 8 + 集成 5 + 端到端 2)
- UC-05 发送评论: 16 个 (单元 8 + 集成 6 + 端到端 2)
- UC-06 发送弹幕: 15 个 (单元 6 + 集成 7 + 端到端 2)

### 2. 构建 Docker 镜像

```powershell
cd video-service
docker build -t ghcr.io/ve1bae/demo-video-service:latest .
```

### 3. 本地 K8s 部署 (Docker Desktop)

```powershell
# 创建命名空间和配置
kubectl apply -f video-service/k8s/00-namespace.yaml
kubectl apply -f video-service/k8s/01-configmap.yaml

# 部署 MySQL
kubectl apply -f video-service/k8s/02-mysql-init-configmap.yaml
kubectl apply -f video-service/k8s/03-mysql-statefulset.yaml
kubectl -n video-service rollout status statefulset/mysql --timeout=240s

# 部署 video-service
kubectl apply -f video-service/k8s/04-video-service-deployment.yaml
kubectl -n video-service rollout status deployment/video-service --timeout=240s

# (可选) 部署 HPA
kubectl apply -f video-service/k8s/05-hpa.yaml
```

### 4. 验证部署

```powershell
# 端口转发
kubectl -n video-service port-forward svc/video-service 18082:8082

# 另开终端验证
curl.exe http://localhost:18082/actuator/health
curl.exe http://localhost:18082/actuator/info
```

健康检查返回:
```json
{"status":"UP","components":{"db":{"status":"UP"},"ping":{"status":"UP"}}}
```

版本号返回:
```json
{"app":{"name":"video-service","version":"0.0.1-SNAPSHOT"}}
```

---

## 三、CI/CD 配置详解

### 环境变量

| 变量名 | 用途 | 默认值 |
|--------|------|--------|
| `SPRING_DATASOURCE_URL` | 数据库连接 | `jdbc:mysql://localhost:3306/video_db?...` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `root` |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 (Secret) | `liyuhao2006.` |
| `SERVER_PORT` | 服务端口 | `8082` |
| `MINIO_ENDPOINT` | MinIO 地址 | `http://localhost:9000` |
| `USER_SERVICE_BASE_URL` | user-service 地址 | `http://localhost:8081` |
| `VIDEO_TRANSCODE_ENABLED` | 转码开关 | `false` |

### K8s 资源清单

| 文件 | 内容 |
|------|------|
| `00-namespace.yaml` | 命名空间 `video-service` |
| `01-configmap.yaml` | 配置 + 数据库密码 Secret |
| `02-mysql-init-configmap.yaml` | 6 张表建表 SQL |
| `03-mysql-statefulset.yaml` | MySQL 8.0 StatefulSet + PVC |
| `04-video-service-deployment.yaml` | video-service Deployment + Service (2 副本) |
| `05-hpa.yaml` | HPA (min=2, max=6, CPU>50% 扩容) |

### 健康探针

| 探针 | 路径 | 端口 | 用途 |
|------|------|------|------|
| readinessProbe | `/actuator/health` | 8082 | 就绪检查, 决定是否接收流量 |
| livenessProbe | `/actuator/health` | 8082 | 存活检查, 决定是否重启 Pod |

---

## 四、查看日志和排查部署失败

### 查看服务日志
```powershell
kubectl -n video-service logs -l app=video-service --tail=100
```

### 查看健康检查
```powershell
kubectl -n video-service exec -it deployment/video-service -- curl -s localhost:8082/actuator/health
```

### 查看就绪检查
```powershell
kubectl -n video-service describe pod -l app=video-service | findstr -i "readiness\|liveness\|probe"
```

### 查看版本号
```powershell
kubectl -n video-service exec -it deployment/video-service -- curl -s localhost:8082/actuator/info
```

### 部署失败排查步骤

1. **查看 Pod 状态**
```powershell
kubectl -n video-service get pods -o wide
```

2. **查看 Pod 事件 (镜像拉取失败、探针失败等)**
```powershell
kubectl -n video-service describe pod -l app=video-service
```

3. **查看应用日志 (启动报错)**
```powershell
kubectl -n video-service logs -l app=video-service --tail=200
```

4. **查看 MySQL 是否就绪**
```powershell
kubectl -n video-service get pods -l app=video-mysql
kubectl -n video-service logs -l app=video-mysql --tail=100
```

5. **CI 中自动收集诊断信息**
   - `failure()` 时自动打印 Pod 状态、describe、日志
   - 测试报告上传为 Artifact, 可在 Actions 页面下载

---

## 五、GitHub Actions 触发 CI

### 推送代码触发

```powershell
git add video-service/
git commit -m "feat: update video-service CI"
git push origin video-service
```

### 查看 CI 运行结果

1. 打开 GitHub 仓库 → Actions 页面
2. 选择 "Video Service CI" workflow
3. 查看最新运行的:
   - Test 步骤: 46 个测试结果
   - Build 步骤: Docker 镜像构建
   - Deploy 步骤: K8s 部署和冒烟测试
   - Artifacts: 下载 surefire-reports

### 下载测试报告

1. 在 Actions 运行页面底部找到 "Artifacts"
2. 下载 `video-service-test-reports`
3. 解压后查看 `surefire-reports/` 下的 XML 报告

---

## 六、API 冒烟测试清单

CI 部署后自动执行以下验证:

| # | 接口 | 方法 | 输入 | 预期输出 |
|---|------|------|------|---------|
| 1 | `/actuator/health` | GET | 无 | `{"status":"UP"}` |
| 2 | `/actuator/info` | GET | 无 | `{"app":{"name":"video-service"}}` |
| 3 | `/api/videos/recommend` | GET | 无 | `{"code":200}` |
| 4 | `/api/videos/1` | GET | 无 | `{"code":200,"data":{"id":1}}` |
| 5 | `/api/videos/1/play` | POST | 无 | `{"code":200,"data":{"playCount":1}}` |
| 6 | `/api/videos/1/comments` | POST | `{"content":"CI comment","userId":1}` | `{"code":200}` |
| 7 | `/api/videos/1/comments?page=1&pageSize=20` | GET | 无 | `{"code":200,"data":{"total":1}}` |
| 8 | `/api/videos/1/danmakus` | POST | `{"content":"CI danmaku","timeSeconds":15,"userId":1}` | `{"code":200}` |
| 9 | `/api/videos/1/danmakus?startTime=0&endTime=60` | GET | 无 | `{"code":200,"data":[{...}]}` |
