# 视频推荐压力测试

## 测试范围

被测接口为真实运行的 `GET /api/videos/recommend?page=1&pageSize=12`，通过 `X-User-Id: 42` 模拟登录用户。测试应连接 MySQL，不使用 Mock 或仅使用 H2。

## 执行前提

1. 启动 `video-service` 和 MySQL，并确认 `http://127.0.0.1:8082/actuator/health` 返回成功。
2. 准备固定的视频数据，不在三次运行之间修改数据。
3. 安装 k6，并在 `video-service` 目录执行脚本。

## 执行

```powershell
.
pressure\run-pressure.ps1
```

脚本默认重复 3 次，每次从 1、10、30 增加到 50 个虚拟用户，再降回 0。可以通过 `-BaseUrl` 和 `-Runs` 覆盖默认值。

## 资源记录

Docker 环境运行期间另开窗口记录：

```powershell
docker stats --no-stream
```

Kubernetes 环境运行期间记录：

```powershell
kubectl get hpa -n hangyin -w
kubectl get pods -n hangyin -w
kubectl top pods -n hangyin
```

每次实验应记录并发数、吞吐量、平均响应时间、P95 响应时间、错误率、CPU 和内存。至少保留三次原始 k6 输出，不只填写汇总表。



