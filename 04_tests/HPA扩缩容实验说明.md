# HPA 扩缩容实验说明

## 实验对象

`live-service` Deployment，HPA 目标 CPU 利用率 50%，最小 1 个 Pod，最大 3 个 Pod。

## 自动执行

GitHub Actions 的 `Live Service CI/CD` 在完成全栈冒烟后：

1. 安装 metrics-server 并等待 `kubectl top` 可用。
2. 应用 [`k8s/hpa.yml`](../k8s/hpa.yml)。
3. 通过 [`scripts/hpa-smoke.sh`](../scripts/hpa-smoke.sh) 对直播间列表 API 施加 64 并发、45 秒负载。
4. 记录请求总数、成功数、错误数、吞吐量、平均/P95 延迟和错误率。
5. 采样 HPA desired/current replicas、CPU、ready replicas 和条件状态。
6. 要求负载期间 ready replicas 从 1 扩展到至少 2，停止负载后回到 1。
7. 重复 3 次，原始 CSV/TSV 作为 Actions artifact 保存。

## 结果填写

每次运行后把 Actions artifact 中的 `summary.csv` 和 `hpa-samples.csv` 下载到本目录，并在此补充运行编号、提交 SHA、三次结果和结论。当前脚本与配置已提交，实验数据须以实际 Actions 运行产物为准，不能预填模拟数字。
