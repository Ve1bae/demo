# PDF 要求对照与交付清单

| PDF 要求 | 当前状态 | 证据/下一步 |
| --- | --- | --- |
| 原系统 Git 标签且不可修改 | 已完成 | `monolith-start` |
| 完整业务场景清单与用例说明 | 已建立 | `01_requirements.md`；需和组员/教师确认最终范围 |
| 需求、概要设计、详细设计 | 直播域已建立 | 本目录 01~03；其他主业务用例需补模型图 |
| 三层模型图且有可编辑源文件 | 直播域已建立 | `models/*.mmd`；需补 UC-01~04 的对应图 |
| 统一追溯表 | 已建立但有缺口 | `05_traceability.md`；补齐主 backend 测试编号后闭环 |
| 单元、集成/API、E2E 测试 | 直播域完整，主业务部分不足 | `04_tests/` 和源码测试目录；补用户/视频/评论/关注回归 |
| 测试报告和原始流水线报告 | 直播域已有 | `04_tests/直播测试报告.md`、Actions artifacts；每次新 run 更新 SHA/URL |
| Dockerfile、数据库脚本、README | 已完成 | `backend/Dockerfile`、`live-service/Dockerfile`、`frontend/demo/Dockerfile`、`demo.sql`、`k8s/README.md` |
| Kubernetes、PVC、SRS、自动部署 | 已完成 | `k8s/*.yml`、`.github/workflows/live-service-ci.yml`，run #9 成功 |
| 至少 3 个业务微服务 | 当前明确 2 个业务后端服务 | 需继续拆分至少第 3 个业务服务，不能把网关/前端/数据库计入 |
| HPA 扩缩容实验 | 未完成 | 增加 HPA、压力脚本、至少 3 次原始数据 |
| 故障处理实验 | 未完成 | 注入依赖故障/延迟，补超时/备用结果证据 |
| 单体与微服务性能对比 | 未完成 | 同机、同数据、同脚本运行至少 3 次并记录 P95/错误率/资源 |
| 项目管理材料 | 未纳入仓库 | 准备 `05_management`：站会、看板截图、任务证据、权重确认 |
| 答辩材料 | 未纳入仓库 | 准备 `06_defense`：PPT、技术总结、演示备用材料 |

## 交付目录约定

```text
01_source/       代码或仓库清单（可由仓库链接替代）
02_docs/         本目录：需求、设计、测试计划、追溯、模型源文件
03_devops/       Docker、CI、Kubernetes、数据库和部署/回滚脚本
04_tests/        自动化测试、压力脚本、原始报告、实验数据
05_management/   站会、看板、任务证据、贡献与权重
06_defense/      PPT、技术总结、备用演示材料
```

`docs/` 目前只作为临时目录使用；正式文档不要再放回该目录。
