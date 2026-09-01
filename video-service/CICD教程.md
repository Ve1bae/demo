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

| 文件 | 路径 | 用途 |
|------|------|------|
| CI 配置 | `.github/workflows/video-service-ci.yml` | GitHub Actions 工作流 |
| K8s 部署 | `video-service/k8s/` | 6 个 K8s 资源文件 |
| Dockerfile | `video-service/Dockerfile` | 多阶段构建 |
| 测试 | `video-service/src/test/` | 46 个测试 (H2, 无需 MySQL) |

---

## 二、CI/CD 流水线详解

### 阶段 1: 测试 (mvn verify)

```yaml
- name: Run tests and package service
  working-directory: video-service
  run: |
    chmod +x mvnw
    ./mvnw -B -ntp verify
```

- 使用 H2 内存数据库, **不需要 MySQL 服务**
- 运行 46 个测试 (单元 22 + API 18 + E2E 6)
- 测试报告保存在 `target/surefire-reports/`

### 阶段 2: 构建 Docker 镜像

```yaml
- name: Build versioned container image
  run: |
    docker buildx build --load --platform linux/amd64 --provenance=false \
      --tag "${IMAGE_NAME}:${GITHUB_SHA}" video-service
```

- 镜像名: `ghcr.io/{owner}/demo-video-service:{sha}`
- 使用 `${GITHUB_SHA}` 作为版本标签 (唯一可追溯)

### 阶段 3: 创建 K8s 集群

```yaml
- name: Create disposable Kubernetes cluster
  uses: helm/kind-action@v1
  with:
    cluster_name: video-ci
```

- 每次运行创建临时 Kind 集群, 结束后自动销毁

### 阶段 4: 部署 MySQL + video-service

```yaml
- name: Deploy database and video service
  run: |
    kind load docker-image "${IMAGE_NAME}:${GITHUB_SHA}" --name video-ci
    kubectl apply -f video-service/k8s/00-namespace.yaml
    kubectl apply -f video-service/k8s/01-configmap.yaml
    kubectl apply -f video-service/k8s/02-mysql-init-configmap.yaml
    kubectl apply -f video-service/k8s/03-mysql-statefulset.yaml
    kubectl -n video-service rollout status statefulset/mysql --timeout=240s
    sed -i "s|ghcr.io/ve1bae/demo-video-service:latest|${IMAGE_NAME}:${GITHUB_SHA}|g" video-service/k8s/04-video-service-deployment.yaml
    kubectl apply -f video-service/k8s/04-video-service-deployment.yaml
    kubectl -n video-service rollout status deployment/video-service --timeout=240s
```

- 先部署 MySQL, 等待就绪
- 再部署 video-service (替换镜像 tag 为本次构建版本)
- 等待 video-service 就绪

### 阶段 5: API 冒烟测试 (9 个接口)

```yaml
- name: Verify health, video and comment APIs
  run: |
    # 1. 健康检查
    curl -fsS http://127.0.0.1:18082/actuator/health | jq -e '.status == "UP"'
    # 2. 版本号
    curl -fsS http://127.0.0.1:18082/actuator/info | jq -e '.app.name == "video-service"'
    # 3. 推荐接口
    curl -fsS http://127.0.0.1:18082/api/videos/recommend | jq -e '.code == 200'
    # 4. 视频详情
    curl -fsS http://127.0.0.1:18082/api/videos/1 | jq -e '.code == 200 and .data.id == 1'
    # 5. 播放计数
    curl -fsS -X POST http://127.0.0.1:18082/api/videos/1/play | jq -e '.code == 200'
    # 6. 发评论
    curl -fsS -X POST -H 'Content-Type: application/json' \
      -d '{"content":"CI comment","userId":1}' \
      http://127.0.0.1:18082/api/videos/1/comments | jq -e '.code == 200'
    # 7. 评论列表
    curl -fsS "http://127.0.0.1:18082/api/videos/1/comments?page=1&pageSize=20" | jq -e '.code == 200'
    # 8. 发弹幕
    curl -fsS -X POST -H 'Content-Type: application/json' \
      -d '{"content":"CI danmaku","timeSeconds":15,"userId":1}' \
      http://127.0.0.1:18082/api/videos/1/danmakus | jq -e '.code == 200'
    # 9. 弹幕列表
    curl -fsS "http://127.0.0.1:18082/api/videos/1/danmakus?startTime=0&endTime=60" | jq -e '.code == 200'
```

### 阶段 6: 失败时收集诊断信息

```yaml
- name: Collect Kubernetes diagnostics on failure
  if: failure()
  run: |
    kubectl -n video-service get pods -o wide || true
    kubectl -n video-service describe pods || true
    kubectl -n video-service logs statefulset/mysql --tail=200 || true
    kubectl -n video-service logs deployment/video-service --tail=200 || true
```

- **部署失败时自动打印**: Pod 状态、Describe、MySQL 日志、video-service 日志
- 用于现场排查部署失败原因

### 阶段 7: 上传测试报告

```yaml
- name: Upload test reports
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: video-service-test-reports
    path: video-service/target/surefire-reports/
```

- 无论成功失败都上传
- 在 Actions 页面可下载 Artifacts

---

## 三、本地运行 CI 步骤

### 1. 运行测试

```powershell
cd video-service
.\mvnw.cmd -B -ntp clean verify
```

测试使用 H2 内存数据库, 不需要 MySQL。共 46 个测试全部通过。

### 2. 构建 Docker 镜像

```powershell
cd video-service
docker build -t demo-video-service:latest .
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

---

## 四、查看日志和排查部署失败

### 查看服务日志

```powershell
kubectl -n video-service logs -l app=video-service --tail=100
```

### 查看健康检查

```powershell
kubectl -n video-service exec deployment/video-service -- curl -s localhost:8082/actuator/health
```

### 查看就绪检查

```powershell
kubectl -n video-service describe pod -l app=video-service | findstr -i "readiness\|liveness\|probe"
```

### 查看版本号

```powershell
kubectl -n video-service exec deployment/video-service -- curl -s localhost:8082/actuator/info
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
kubectl -n video-service get pods
kubectl -n video-service logs statefulset/mysql --tail=100
```

5. **CI 中自动收集诊断信息**
   - `failure()` 时自动打印 Pod 状态、describe、日志
   - 测试报告上传为 Artifact, 可在 Actions 页面下载

---

## 五、GitHub Actions 触发 CI

### 推送代码触发

```powershell
git add video-service/
git commit -m "feat: update video-service"
git push origin video-service
```

### 查看 CI 运行结果

1. 打开 GitHub 仓库 → Actions 页面
2. 选择 "Video Service CI" workflow
3. 查看最新运行:
   - Test 步骤: 46 个测试结果
   - Build 步骤: Docker 镜像构建
   - Deploy 步骤: K8s 部署和冒烟测试
   - Artifacts: 下载 surefire-reports

### 下载测试报告

1. 在 Actions 运行页面底部找到 "Artifacts"
2. 下载 `video-service-test-reports`
3. 解压后查看 `surefire-reports/` 下的 XML 报告

---

## 六、K8s 资源清单

| 文件 | 内容 | 说明 |
|------|------|------|
| `00-namespace.yaml` | Namespace `video-service` | 独立命名空间 |
| `01-configmap.yaml` | ConfigMap + Secret | 配置和数据库密码 |
| `02-mysql-init-configmap.yaml` | 建表 SQL | 6 张表自动建表 |
| `03-mysql-statefulset.yaml` | MySQL 8.0 + PVC | 数据库 StatefulSet |
| `04-video-service-deployment.yaml` | Deployment + Service | 2 副本, 健康探针 |
| `05-hpa.yaml` | HPA | min=2, max=6, CPU>50% 扩容 |

### 环境变量

| 变量名 | 来源 | 默认值 |
|--------|------|--------|
| `SPRING_DATASOURCE_URL` | ConfigMap | `jdbc:mysql://mysql-service:3306/video_db?...` |
| `SPRING_DATASOURCE_USERNAME` | ConfigMap | `root` |
| `SPRING_DATASOURCE_PASSWORD` | **Secret** | `root` |
| `SERVER_PORT` | application.yml | `8082` |
| `MINIO_ENDPOINT` | ConfigMap | `http://minio-service:9000` |
| `USER_SERVICE_BASE_URL` | ConfigMap | `http://user-service:8081` |

### 健康探针

| 探针 | 路径 | 端口 | 用途 |
|------|------|------|------|
| readinessProbe | `/actuator/health` | 8082 | 就绪检查, 决定是否接收流量 |
| livenessProbe | `/actuator/health` | 8082 | 存活检查, 决定是否重启 Pod |

---

## 七、API 冒烟测试清单 (CI 中自动执行)

| # | 接口 | 方法 | 输入 | 预期输出 |
|---|------|------|------|---------|
| 1 | `/actuator/health` | GET | 无 | `{"status":"UP"}` |
| 2 | `/actuator/info` | GET | 无 | `{"app":{"name":"video-service"}}` |
| 3 | `/api/videos/recommend` | GET | 无 | `{"code":200}` |
| 4 | `/api/videos/1` | GET | 无 | `{"code":200,"data":{"id":1}}` |
| 5 | `/api/videos/1/play` | POST | 无 | `{"code":200}` |
| 6 | `/api/videos/1/comments` | POST | `{"content":"CI comment","userId":1}` | `{"code":200}` |
| 7 | `/api/videos/1/comments` | GET | `?page=1&pageSize=20` | `{"code":200}` |
| 8 | `/api/videos/1/danmakus` | POST | `{"content":"CI danmaku","timeSeconds":15,"userId":1}` | `{"code":200}` |
| 9 | `/api/videos/1/danmakus` | GET | `?startTime=0&endTime=60` | `{"code":200}` |
