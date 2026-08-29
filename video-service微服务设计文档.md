# 视频点播服务(video-service)微服务设计文档

## 一、服务接口清单(共 29 个 HTTP 接口)

### A. 视频核心 `/api/videos`(VideoController,14 个)

| # | 方法 | 路径 | 功能 | 关键参数 |
|---|------|------|------|---------|
| 1 | GET | `/api/videos/recommend` | 推荐视频流(分页) | `page` `pageSize` `categoryId` `keyword`、Header `X-User-Id` |
| 2 | GET | `/api/videos/{videoId}` | 按 ID 查视频详情 | `videoId` |
| 3 | GET | `/api/videos/url?videoUrl=` | 按播放地址查视频 | `videoUrl` |
| 4 | GET | `/api/videos/user/{userId}/uploads` | 用户上传列表 | `userId` |
| 5 | GET | `/api/videos/user/{userId}/favorites` | 用户收藏列表 | `userId` |
| 6 | POST | `/api/videos/upload` | 上传视频(MinIO+FFmpeg 转 480P/720P/1080P) | `file` `title` `description` `coverUrl` `tags` `userId` `duration` |
| 7 | POST | `/api/videos/{videoId}/visibility` | 设置公开/仅自己可见 | body: `userId` `visible` |
| 8 | DELETE | `/api/videos/{videoId}` | 删除视频(仅作者) | `videoId` + `userId` |
| 9 | POST | `/api/videos/{videoId}/likes` | 点赞/取消(toggle) | `videoId` + body `userId` |
| 10 | DELETE | `/api/videos/{videoId}/likes` | 取消点赞 | `videoId` + body `userId` |
| 11 | POST | `/api/videos/{videoId}/favorites` | 收藏/取消(toggle) | `videoId` + body `userId` |
| 12 | DELETE | `/api/videos/{videoId}/favorites` | 取消收藏 | `videoId` + body `userId` |
| 13 | POST | `/api/videos/{videoId}/play` | 播放计数 +1 | `videoId` + Header `X-User-Id` |
| 14 | GET | `/api/videos/{videoId}/status` | 查当前用户点赞/收藏状态 | `videoId` `userId` |

### B. 评论 `/api/videos/{videoId}/comments`(CommentController,5 个)

| # | 方法 | 路径 | 功能 | 关键参数 |
|---|------|------|------|---------|
| 15 | GET | `/{videoId}/comments` | 评论分页列表(含回复、点赞状态) | `page` `pageSize` `userId` |
| 16 | POST | `/{videoId}/comments` | 发布评论(需登录,支持楼中楼 `parentId`) | body: `content` `parentId` `userId` |
| 17 | POST | `/{videoId}/comments/{commentId}/like` | 评论点赞(防重复,重复返回 409) | `commentId` + `userId` |
| 18 | DELETE | `/{videoId}/comments/{commentId}/like` | 取消评论点赞 | `commentId` + `userId` |
| 19 | DELETE | `/{videoId}/comments/{commentId}` | 删除评论(评论作者或视频作者) | `commentId` + `userId` |

### C. 弹幕 `/api/videos/{videoId}/danmakus`(DanmakuController,6 个)

| # | 方法 | 路径 | 功能 | 关键参数 |
|---|------|------|------|---------|
| 20 | GET | `/{videoId}/danmakus` | 取弹幕(按时间段过滤) | `startTime` `endTime` |
| 21 | POST | `/{videoId}/danmakus` | 发送弹幕 | body: `content` `timeSeconds` `color` `userId` |
| 22 | GET | `/danmaku/video?videoUrl=` | (旧兼容)按视频 URL 取弹幕 | `videoUrl` |
| 23 | POST | `/danmaku` | (旧兼容)直接保存弹幕 | body: Danmaku 对象 |
| 24 | GET | `/danmaku/user?userId=` | (旧兼容)按用户取弹幕 | `userId` |
| 25 | DELETE | `/danmaku/{id}` | 删除弹幕 | `id` |

### D. 对象存储 `/api/minio`(MinioController,4 个)

| # | 方法 | 路径 | 功能 | 关键参数 |
|---|------|------|------|---------|
| 26 | POST | `/api/minio/upload` | 通用文件上传 | `file` |
| 27 | DELETE | `/api/minio/delete?objectName=` | 删除文件 | `objectName` |
| 28 | GET | `/api/minio/url?objectName=` | 获取文件访问 URL | `objectName` |
| 29 | GET | `/api/minio/test` | MinIO 连通性测试 | - |

## 二、数据表归属方案

### video-service 独占 5 张表(MySQL Schema:`video_db`)

| 表名 | 内容 | 谁能写 | 谁不能碰 |
|------|------|--------|---------|
| `video` | 视频主表(标题/封面/播放地址/三清晰度URL/播放数/点赞数/收藏数/评论数) | **仅 video-service** | user-service、live-service |
| `comment` | 视频评论(`videoId`+`userId`+`content`+`parentId` 楼中楼) | **仅 video-service** | user-service、live-service |
| `comment_like` | 评论点赞关系(`commentId`+`userId`,防重复) | **仅 video-service** | user-service、live-service |
| `danmaku` | 视频弹幕(以 `videoUrl` 关联视频,含 `time`/`color`/`userId`) | **仅 video-service** | user-service、live-service |
| `view_history` | 观看历史(`userId`+`videoId`+`viewCount`+`progressSeconds`) | **仅 video-service** | user-service、live-service |

### 归属规则说明

- **`user`、`user_follow`、`user_interest` 归 user-service**:本服务不建、不读、不写这三张表。评论/弹幕里的"用户昵称头像"通过调 `GET /api/users/{userId}` 接口获取,`author` 字段在发布时冗余存储,不回查用户库。
- **`live_room`、`live_danmu`、`room_likes` 归 live-service**:本服务不碰。视频弹幕(`danmaku` 表)与直播弹幕(`live_danmu` 表)是两套独立数据,Schema 隔离。
- **可同库不同 Schema**:三个服务可以共用一台 MySQL 实例,但 `user_db` / `video_db` / `live_db` 三个 Schema 严格隔离,**SQL 层面禁止跨 Schema JOIN**。

### 跨服务数据操作(遵守"不联表"规则)

| 场景 | 微服务做法 | 失败处理 |
|------|-----------|---------|
| 发评论前验证用户存在 | HTTP 调 `GET /api/users/{userId}` | 用户服务不可用 → 返回 503 拒绝发评论(登录态必须强校验) |
| 评论区显示昵称/头像 | 调用户服务批量查询接口,结果缓存 5 分钟 | 降级显示"匿名用户",不阻塞评论列表 |
| 发布评论后 `video.commentCount+1` | 本服务内完成(comment 和 video 同属 video-service) | 本地事务保证 |
