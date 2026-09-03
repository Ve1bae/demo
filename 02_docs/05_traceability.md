# 需求—设计—代码—测试追溯表

表中编号遵循 `REQ / UC / SYS-SEQ / COMP-SEQ / OBJ-SEQ / TC` 规则。测试结果必须引用可复核的源码、报告或 Actions run，不以截图单独作为证据。

| 需求 | 用例 | 系统级模型 | 组件级模型 | 对象级模型 | 代码模块 | 单元/集成/E2E 测试 | 当前结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-06 | UC-05 创建直播间 | SYS-SEQ-05 | COMP-SEQ-05 | OBJ-SEQ-05 | `LiveController`、`LiveService`、`LiveRepository` | `LS-UT-01`、`LS-IT-01`、`LS-E2E-01` | 已通过 |
| REQ-06 | UC-06 观看与互动 | SYS-SEQ-06 | COMP-SEQ-06 | OBJ-SEQ-06 | `LiveWebSocketHandler`、`LiveService` | `LS-UT-02..05`、`LS-IT-02..06`、`LS-E2E-01` | 已通过 |
| REQ-06 | UC-07 结束直播 | SYS-SEQ-07 | COMP-SEQ-07 | OBJ-SEQ-07 | `LiveService.closeRoom`、`LiveRepository.closeRoom` | `LS-UT-06`、`LS-IT-01/04`、`LS-E2E-01` | 已通过 |
| REQ-07 | UC-08 自动部署 | SYS-SEQ-08 | COMP-SEQ-08 | OBJ-SEQ-08 | `.github/workflows/live-service-ci.yml`、`k8s/*.yml` | `K8S-TC-01` | 已通过，run #9 |
| REQ-08 | UC-08 自动部署故障处理 | SYS-SEQ-08 | COMP-SEQ-08 | OBJ-SEQ-08 | `SrsHealthService`、`scripts/fault-handling-smoke.sh`、`.github/workflows/live-service-ci.yml` | `K8S-FAULT-01` | 已通过，Actions run `33608439523`（commit `026ffc2`） |
| REQ-01 | UC-01 注册登录推荐 | 待补 | 待补 | 待补 | `UserController`、`VideoController` | `BE-TC-USER-*` 待补 | 部分完成 |
| REQ-02/03 | UC-02 上传播放 | 待补 | 待补 | 待补 | `VideoController`、`MinioController` | `BE-TC-VIDEO-*` 待补 | 部分完成 |
| REQ-04 | UC-03 评论互动 | 待补 | 待补 | 待补 | `CommentController`、`DanmakuController` | `BE-TC-COMMENT-*` 待补 | 部分完成 |
| REQ-05 | UC-04 关注关系 | 待补 | 待补 | 待补 | `UserController`、`UserFollowMapper` | `BE-TC-FOLLOW-*` 待补 | 部分完成 |

## 测试编号定义

- `LS-UT-*`：live-service 单元测试，源码 `live-service/src/test/java/com/example/live/LiveServiceUnitTest.java`。
- `LS-IT-*`：live-service API/WebSocket 集成测试，源码 `LiveApiIntegrationTest.java`。
- `LS-E2E-*`：live-service 端到端测试，源码 `LiveServiceE2ETest.java`。
- `K8S-TC-01`：GitHub Actions 完整 Kubernetes 冒烟，验证脚本位于 workflow 的 `Verify the deployed full stack` 步骤；最近成功 run `33608439523`，commit `026ffc2`。
- `K8S-FAULT-01`：SRS 下线/恢复、探测超时和 live-service Pod 自愈；最近成功 run `33608439523`，原始 CSV 在 `full-stack-test-and-deployment-reports` artifact。
- `BE-TC-*`：待补齐主 backend 全业务回归后分配的编号。

## 追溯维护要求

任何新增接口、表、用例或测试必须先增加编号，再更新本表；删除功能时保留“废弃”记录和对应提交 SHA，避免追溯链断裂。
