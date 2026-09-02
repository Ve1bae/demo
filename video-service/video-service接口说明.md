`video-service` 当前提供以下接口，服务默认端口为 `8082`，统一前缀为 `/api/video`（视频资源）与 `/api/minio`（MinIO 工具）。用户身份通过 `X-User-Id` 请求头传入，不读取用户中心表。

| 方法   | 接口                                 | 说明                         |
| ------ | ------------------------------------ | ---------------------------- |
| POST   | `/api/videos/upload`                 | 上传视频（multipart）        |
| GET    | `/api/videos/recommend`              | 推荐视频列表（分页/分类/关键词） |
| GET    | `/api/videos/{videoId}`              | 查询视频详情                 |
| GET    | `/api/videos/user/{userId}/uploads`  | 查询某用户上传的视频列表     |
| DELETE | `/api/videos/{videoId}`              | 删除自己的视频（软删除）     |
| POST   | `/api/videos/{videoId}/visibility`   | 设置公开 / 仅自己可见        |
| POST   | `/api/videos/{videoId}/play`         | 播放计数 + 记录观看历史      |
| POST   | `/api/videos/{videoId}/likes`        | 点赞视频                     |
| DELETE | `/api/videos/{videoId}/likes`        | 取消点赞                     |
| POST   | `/api/videos/{videoId}/favorites`    | 收藏视频                     |
| DELETE | `/api/videos/{videoId}/favorites`    | 取消收藏                     |
| GET    | `/api/videos/{videoId}/status`       | 查询当前用户点赞/收藏状态    |
| GET    | `/api/videos/{videoId}/comments`     | 查询评论列表（分页）         |
| POST   | `/api/videos/{videoId}/comments`     | 发表评论                     |
| POST   | `/api/comments/{commentId}/likes`    | 点赞评论                     |
| GET    | `/api/videos/{videoId}/danmakus`     | 查询点播弹幕列表             |
| POST   | `/api/videos/{videoId}/danmakus`     | 发送点播弹幕                 |
| GET    | `/api/minio/test`                    | 检查 MinIO 连接              |
| POST   | `/api/minio/upload`                  | 上传任意文件到 MinIO         |
| GET    | `/api/minio/url`                     | 生成对象访问地址             |
| DELETE | `/api/minio/delete`                  | 删除 MinIO 对象              |
