# 详细设计说明

## 1. 直播核心对象

| 对象 | 关键字段/职责 | 代码位置 |
| --- | --- | --- |
| `LiveRoomView` | 房间、主播、推流地址、播放地址、状态、`streamActive` | `live-service/src/main/java/com/example/live/model/LiveRoomView.java` |
| `LiveService` | 创建、查询、列表、关闭、弹幕/点赞前置规则 | `live-service/src/main/java/com/example/live/service/LiveService.java` |
| `LiveRepository` | `live_room`、`live_danmu`、`room_likes` SQL 读写 | `live-service/src/main/java/com/example/live/repository/LiveRepository.java` |
| `SrsHealthService` | SRS `/api/v1/versions` 可达性和流状态探测 | `live-service/src/main/java/com/example/live/service/SrsHealthService.java` |
| `LiveController` | REST 入口和统一响应 | `live-service/src/main/java/com/example/live/controller/LiveController.java` |
| `LiveWebSocketHandler` | 弹幕/点赞消息校验与广播 | `live-service/src/main/java/com/example/live/websocket/LiveWebSocketHandler.java` |

## 2. 状态模型

`live_room.status` 初始为 `online`，仅房主可转为 `offline`。离线状态禁止新增弹幕和点赞；查询仍可返回房间历史信息。SRS 的 `streamActive` 是外部探测值，可能短暂滞后于 HTTP-FLV，因此 CI 将 FLV 有效数据作为播放链路的权威证据，同时记录 `streamActive` 供诊断。

## 3. 关键流程

模型源文件：

- 系统级：[`models/use-case-live-room.mmd`](models/use-case-live-room.mmd)
- 组件级：[`models/component-live-room.mmd`](models/component-live-room.mmd)
- 对象级：[`models/object-live-room.mmd`](models/object-live-room.mmd)

## 4. 失败处理

- 参数失败：统一返回 `code=400`，不写数据库。
- 权限失败：返回业务错误，保留原房间状态。
- SRS 不可达：健康接口返回 `reachable=false`；业务接口保留地址并允许上层重试。
- CI 失败：GitHub Actions 的验证步骤失败后不执行发布收尾以外的部署步骤，并上传 Kubernetes 诊断日志。
