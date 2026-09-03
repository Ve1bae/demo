# 测试计划与报告索引

## 1. 测试分层

| 层级 | 目标 | 当前入口 | 证据 |
| --- | --- | --- | --- |
| 单元测试 | 关键规则、边界和异常分支 | `backend/src/test/.../LiveUnitTest.java`、`live-service/src/test/.../LiveServiceUnitTest.java` | Surefire XML/控制台日志 |
| 集成/API | Controller、数据库、WebSocket、SRS 健康接口 | `ApiIntegrationTest.java`、`LiveApiIntegrationTest.java` | Maven 报告 |
| E2E | 从真实 API、WebSocket 或页面走完完整业务流程 | `LiveE2ETest.java`、`live-service/src/test/.../LiveServiceE2ETest.java`、`frontend/demo/e2e/live-ui.spec.js` | Surefire、Playwright HTML/JSON、`e2e-io.json` 和 Java E2E JSON |
| Kubernetes 冒烟 | 镜像、PVC、rollout、API、RTMP、FLV | `.github/workflows/live-service-ci.yml` | Actions run 和 artifact |
| HPA 扩缩容实验 | CPU 加压、扩容、降载、缩容 | `scripts/hpa-smoke.sh` + `k8s/hpa.yml` | `hpa-samples.csv`、`pod-samples.csv`、`summary.csv` |
| 性能对比实验 | 单体与微服务同条件压测 | `scripts/performance-compare.sh` | `performance-results.csv` 及原始请求/进程采样 |
| 故障处理实验 | SRS 下线/恢复、探测超时、Pod 自愈 | `scripts/fault-handling-smoke.sh` | `fault-results.csv`、响应 JSON、Kubernetes 日志 |

## 2. 有效性标准

- 每个测试必须有断言，不能只检查进程退出码。
- 每个用例覆盖主成功、至少一个备选或异常路径。
- 报告必须列出原始请求体/请求头、响应体、WebSocket 收发帧和页面最终状态；Playwright 由 `e2e/reporting.js` 自动采集，Java E2E 写入 `target/e2e-artifacts/`。
- 测试失败必须让流水线停止后续部署验证。
- 报告需记录总数、通过数、失败数、失败原因、运行环境和提交 SHA。
- HPA 报告需记录并发数、负载时长、吞吐量、平均/P95 延迟、错误率、目标/实际 CPU、扩容前后 Pod 数量，并至少重复 3 次。

## 3. 已验证结果

### 原系统 CI

历史报告：[`04_tests/直播测试报告.md`](../04_tests/直播测试报告.md)，提交 `6ca62fe`，17/17 通过。

### Kubernetes 全栈 CI

Workflow：[`../.github/workflows/live-service-ci.yml`](../.github/workflows/live-service-ci.yml)。最近成功运行：

- [Live Service CI/CD #9](https://github.com/Ve1bae/demo/actions/runs/33372890154)
- 提交：`5fe0c0b`
- 通过步骤：构建、Kind、PVC、MySQL、SRS、MinIO、三应用 rollout、API、RTMP、HTTP-FLV、直播状态、点赞和关闭直播。

## 4. 仍需补充的测试证据

PDF 要求“清单中的全部业务场景（用例）”均有测试。当前直播核心链路证据较完整，但用户注册、上传、评论、关注和视频收藏等场景还需要在 `04_tests` 中补充对应 API/E2E 测试编号与原始报告，不能用已有直播报告替代。
