# live-service 专项非功能测试说明

## 1. 目的

本材料把直播微服务的三类非功能验证从视频服务测试中独立出来：

1. REST 与 WebSocket 专项压力测试；
2. Kubernetes HPA 扩缩容实测；
3. live-service Pod 故障停止、Service 转发和实例恢复实测。

三类测试均针对 `live-service`，不能用 video-service 的推荐接口结果替代。

## 2. 覆盖范围

| 类别 | 被测内容 | 证据目录 | 判定重点 |
| --- | --- | --- | --- |
| REST 压力 | 房间列表、房间详情、弹幕历史、点赞数、服务健康、SRS 健康 | `live-service/pressure-results/` | 吞吐量、平均响应、P95、错误率、断言通过率 |
| WebSocket 压力 | 连接建立、弹幕广播、点赞广播 | `live-service/pressure-results/` | 握手成功率、广播收到率、连接耗时 |
| HPA 扩缩容 | `live-service` CPU 负载下的副本变化 | `evidence/hpa/live-service/` | 副本 `1 -> 2/3 -> 1`、CPU 指标和稳定窗口 |
| 故障恢复 | 删除一个 live-service Pod 后继续访问，再确认新 Pod 就绪 | `evidence/fault-drill/live-service/` | 故障期间健康与房间接口可用，恢复后副本就绪 |

## 3. REST/WebSocket 压力测试

前提：Kubernetes 或 Compose 中的 `live-service` 已启动，并准备一个状态为 `online` 的直播间。取得房间编号后执行：

```powershell
Set-Location live-service
.\pressure\run-pressure.ps1 -BaseUrl http://127.0.0.1:8090 -RoomId <online-room-id> -Runs 3
```

脚本默认每轮执行 REST 和 WebSocket 两个场景。REST 场景包含 1、10、30、50 虚拟用户梯度，并同时检查房间查询、弹幕历史、点赞数及健康接口；WebSocket 场景验证连接、弹幕广播和点赞广播。

每轮会保存 k6 的 JSON 摘要和原始文本输出。不得只填写汇总数字，应保留三轮原始文件以及测试时间、机器配置和服务资源限制。

## 4. live-service HPA 实测

前提：Kind/Kubernetes 中已部署 `live-service`、`live-service` Service、Metrics Server 和 `k8s/microservices-hpa.yaml`。当前分支中的 `live-service` HPA 使用 CPU 50% 目标、1-3 个副本，并采用 15 秒扩容/缩容策略。执行：

```powershell
.\scripts\live-service-hpa-load-test.ps1 -Namespace hangyin -DurationSeconds 180 -ScaleDownWaitSeconds 180
```

脚本只向 `live-service` 的 `/api/live/rooms` 施加负载，等待 Metrics Server 提供指标，使用 HPA/Deployment 的 JSONPath 采样记录 desired/current/ready 副本、CPU、目标值和条件状态，并汇总请求吞吐、平均/P95 延迟和错误率。通过标准是负载期间副本数增加、停止负载后经过稳定窗口回落；若只看到 HPA 配置而没有副本变化，不能写成扩缩容实测通过。

CI 使用 `scripts/hpa-smoke.sh` 重复执行单次可审计实验；原始 CSV/TSV 保存在 `hangyin-hpa/` Artifact 中。

## 5. live-service 故障停止与恢复实测

执行：

```powershell
.\scripts\live-service-fault-drill.ps1 -Namespace hangyin
```

当前分支的 CI 入口为 `scripts/fault-handling-smoke.sh`。脚本依次验证 SRS 正常、SRS Deployment 缩容到 0 后的降级、SRS 恢复、将 SRS API 临时改为 `192.0.2.1` 后的有界超时降级，以及删除 `live-service` Pod 后 Deployment 自动补回。每次探测记录场景、期望/实际 `reachable`、HTTP 是否成功、耗时、判定和 UTC 时间戳。

通过标准是：

- 基线 SRS 健康接口返回 `reachable=true`；
- SRS 下线和不可路由地址场景返回 HTTP 成功且 `reachable=false`，请求不会无限阻塞；
- SRS 恢复后返回 `reachable=true`；
- 删除 Pod 后 Deployment 自动创建替代 Pod，健康检查最终恢复；
- `fault-results.csv` 和各场景响应 JSON 同时存在。

CI 将 HPA 与故障实验的原始输出和端口转发日志上传为 `microservices-kubernetes-reports` Artifact。配置和脚本只能证明实验可复现，答辩中应以实际副本变化、指标和 `fault-results.csv` 为实测证据。

## 6. 当前仓库状态说明

当前仓库已包含上述可执行脚本和证据目录约定。生成的压力测试和故障演练结果属于运行时证据，不应手工伪造；应在 Docker/Kubernetes/k6 可用的环境执行后，再把原始输出和汇总材料纳入答辩附件。
