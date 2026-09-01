# 航音项目正式文档（02_docs）

本目录是按《软件工程基础实践》任务书整理的正式交付文档区。`docs/` 仅作为临时文件目录，不放最终交付材料；自动化测试源码、原始报告和实验数据统一放在仓库根目录的 [`04_tests`](../04_tests/)。

## 文档索引

| 编号 | 文档 | 对应任务书要求 |
| --- | --- | --- |
| 01 | [需求说明与业务场景](01_requirements.md) | 需求编号、用例清单、用例说明 |
| 02 | [概要设计说明](02_architecture.md) | 组件图、服务边界、部署拓扑 |
| 03 | [详细设计说明](03_detailed_design.md) | 类/对象职责、关键流程、数据归属 |
| 04 | [测试计划与报告索引](04_test_plan.md) | 单元、集成/API、E2E、CI 证据 |
| 05 | [需求-设计-代码-测试追溯表](05_traceability.md) | 统一追溯表 |
| 06 | [交付缺口与验收清单](06_delivery_checklist.md) | 对照 PDF 的缺漏、证据入口 |
| 07 | [AI 与开源使用说明](07_ai_and_sources.md) | AI 工具、开源来源、人工复核 |

## 模型源文件

可编辑的 Mermaid 模型源文件位于 [`models/`](models/)：

- `system-context.mmd`：系统级组件/上下文图
- `use-case-live-room.mmd`：直播间核心用例系统级顺序图
- `component-live-room.mmd`：直播间组件级顺序图
- `object-live-room.mmd`：直播间对象级顺序图

性能对比实验入口：[`scripts/performance-compare.sh`](../scripts/performance-compare.sh)，实验说明见 [`04_tests/性能对比实验说明.md`](../04_tests/性能对比实验说明.md)。

Git 基线标签：`monolith-start`（改造前原系统基线）。

## 使用规则

接口、字段、测试编号或部署配置发生变化时，必须同步更新本目录的需求、设计和追溯表。每次验收记录应写明提交 SHA、Actions run URL、运行环境和原始报告位置。
