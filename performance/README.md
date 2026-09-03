# 性能测试证据

课程要求单体版和微服务版分别完成至少 3 次性能测试。测试必须在相同机器、相同数据、相同并发梯度和相同持续时间下执行，保存原始 CSV、Markdown 汇总、容器状态和测试参数。

## 微服务版

先启动 `docker-compose.microservices.yml`，再执行：

```powershell
.\scripts\run-api-benchmark.ps1 -BaseUrl http://127.0.0.1:8080/api -Concurrency '1,10,50,100' -DurationSeconds 30 -WarmupSeconds 10 -Runs 3
```

结果默认写入 `benchmark-results/`。答辩材料至少展示吞吐量、平均响应时间、P95、错误率、CPU 和内存。

当前已保存一轮有效的微服务结果：`benchmark-results/microservices/api-benchmark-2026-09-02T18-07-33-149Z.csv` 和同名 Markdown。该轮使用 1、10、50、100 并发梯度，每档 3 次，三个接口错误率均为 0；此前的 `2026-09-02T18-02-25-635Z` 文件为空，仅作失败执行记录，不得用于分析。

## 单体版

单体基线使用从历史单体提交恢复的 `backend/`、独立 MySQL/MinIO/SRS 和固定测试数据。启动方式：

```powershell
docker compose -f docker-compose.monolith-benchmark.yml -p hangyin-monolith-benchmark up -d --build
$env:BENCHMARK_OUTPUT_DIR = 'benchmark-results/monolith'
$env:BENCHMARK_BASE_URL = 'http://127.0.0.1:18080/api'
$env:BENCHMARK_CONCURRENCY = '1,10,50,100'
$env:BENCHMARK_DURATION_SEC = '5'
$env:BENCHMARK_WARMUP_SEC = '2'
$env:BENCHMARK_RUNS = '3'
node scripts/benchmark-api.mjs
```

当前已保存单体结果：`benchmark-results/monolith/api-benchmark-2026-09-02T19-40-30-821Z.csv` 和同名 Markdown。单体与微服务使用相同机器、接口、数据规模、并发梯度、预热时长、测试时长和重复次数；对照汇总见 `benchmark-results/性能对照汇总-20260903.md`。

## 证据要求

- 保留每次运行生成的 CSV 和 Markdown；
- 记录 Git 分支、提交号、测试时间、机器配置和 Docker/Kubernetes 资源限制；
- 同时保存 `docker compose ps`、服务日志摘要和测试命令；
- 汇总时报告平均值，并保留三次原始结果，不要手工修改原始数据。

答辩表述：当前可以报告“单体版和微服务版均完成 3 次同参数性能测试，原始 CSV 和资源证据已保存”。应同时说明结果只代表本机和当前数据集，不把一次本地测量概括为普遍的架构性能结论。
