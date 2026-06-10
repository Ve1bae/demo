# 航音视频 API 接口规范

文档版本：v0.1  
适用范围：前后端分离开发、后端接口实现、联调测试  
维护规则：接口路径、请求参数、响应字段发生变化时，必须同步更新本文档。

## 1. 通用约定

### 1.1 服务地址

| 环境 | 地址 | 说明 |
| --- | --- | --- |
| 前端本地 | `http://localhost:5173` | Vue3 + Vite |
| 后端本地 | `http://localhost:8080` | Spring Boot |
| 接口前缀 | `/api` | 所有业务接口统一前缀 |

### 1.2 请求规范

- 接口路径使用资源名词，统一小写，多个单词使用中划线连接。
- 查询资源使用 `GET`。
- 创建资源使用 `POST`。
- 更新完整资源使用 `PUT`。
- 删除资源或取消关系使用 `DELETE`。
- 请求和响应字段统一使用 `camelCase`。
- 时间字段统一使用 ISO 8601 格式，例如 `2026-06-03T12:00:00`。

### 1.3 响应结构

所有接口返回统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页接口的 `data` 固定为：

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 10
}
```

### 1.4 错误码

| code | 含义 | 说明 |
| --- | --- | --- |
| 200 | 成功 | 请求处理成功 |
| 400 | 参数错误 | 参数为空、格式错误、范围非法 |
| 401 | 未登录 | 未携带 token 或 token 已失效 |
| 403 | 无权限 | 当前用户无权访问该资源 |
| 404 | 资源不存在 | 用户、视频、评论、直播间不存在 |
| 409 | 数据冲突 | 用户名重复、重复点赞、重复收藏 |
| 500 | 服务异常 | 服务端未预期异常 |

### 1.5 鉴权规范

需要登录的接口必须携带请求头：

```text
Authorization: Bearer <token>
```

未登录接口不得依赖前端传入的用户身份。

## 2. 用户认证

### 2.1 用户注册

```http
POST /api/user/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "viewer",
  "password": "demoPass123",
  "nickname": "普通观众"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 登录账号，唯一 |
| password | string | 是 | 登录密码 |
| nickname | string | 是 | 展示昵称 |

响应 `data`：

```json
{
  "userId": 1,
  "username": "viewer",
  "nickname": "普通观众",
  "avatarUrl": "",
  "roles": ["viewer"]
}
```

### 2.2 用户登录

```http
POST /api/user/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "viewer",
  "password": "demoPass123"
}
```

响应 `data`：

```json
{
  "token": "token-value",
  "user": {
    "userId": 1,
    "username": "viewer",
    "nickname": "普通观众",
    "avatarUrl": "",
    "roles": ["viewer"]
  }
}
```

### 2.3 当前用户信息

```http
GET /api/user/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "userId": 1,
  "username": "viewer",
  "nickname": "普通观众",
  "avatarUrl": "",
  "roles": ["viewer"]
}
```

## 3. 视频资源

### 3.1 视频推荐列表

```http
GET /api/videos/recommend?page=1&pageSize=12&categoryId=0&keyword=
```

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| page | number | 否 | 1 | 页码，从 1 开始 |
| pageSize | number | 否 | 12 | 每页数量 |
| categoryId | number | 否 | 0 | `0` 表示全部分类 |
| keyword | string | 否 | 空 | 搜索关键词 |

响应 `data.list` 单项：

```json
{
  "videoId": 1,
  "title": "校园歌手大赛回放",
  "description": "校园活动完整回放",
  "coverUrl": "",
  "duration": "12:30",
  "playCount": 1200,
  "likeCount": 88,
  "favoriteCount": 23,
  "commentCount": 5,
  "category": {
    "categoryId": 1,
    "categoryName": "音乐"
  },
  "author": {
    "userId": 2,
    "nickname": "校学生会",
    "avatarUrl": ""
  },
  "recommendReason": "近期热度较高",
  "createdAt": "2026-06-03T12:00:00"
}
```

### 3.2 视频详情

```http
GET /api/videos/{videoId}
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| videoId | number | 是 | 视频 ID |

响应 `data`：

```json
{
  "videoId": 1,
  "title": "校园歌手大赛回放",
  "description": "校园活动完整回放",
  "coverUrl": "",
  "playUrl": "http://localhost:8080/static/videos/demo.mp4",
  "duration": "12:30",
  "playCount": 1200,
  "likeCount": 88,
  "favoriteCount": 23,
  "commentCount": 5,
  "liked": false,
  "favorited": false,
  "category": {
    "categoryId": 1,
    "categoryName": "音乐"
  },
  "author": {
    "userId": 2,
    "nickname": "校学生会",
    "avatarUrl": ""
  },
  "createdAt": "2026-06-03T12:00:00"
}
```

### 3.3 相关推荐

```http
GET /api/videos/{videoId}/related?page=1&pageSize=6
```

### 3.4 视频分类列表

```http
GET /api/video-categories
```

响应 `data`：

```json
[
  {
    "categoryId": 1,
    "categoryName": "音乐"
  }
]
```

## 4. 视频上传

### 4.1 创建视频

```http
POST /api/videos
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 视频文件 |
| title | string | 是 | 视频标题 |
| description | string | 否 | 视频简介 |
| categoryId | number | 是 | 分类 ID |
| cover | file | 否 | 视频封面 |

响应 `data`：

```json
{
  "videoId": 10,
  "title": "我的校园 vlog",
  "coverUrl": "http://localhost:8080/static/covers/10.jpg",
  "playUrl": "http://localhost:8080/static/videos/10.mp4",
  "status": "published",
  "createdAt": "2026-06-03T12:00:00"
}
```

### 4.2 当前用户视频列表

```http
GET /api/users/me/videos?page=1&pageSize=10
Authorization: Bearer <token>
```

## 5. 视频互动

### 5.1 点赞视频

```http
POST /api/videos/{videoId}/likes
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": 1,
  "liked": true,
  "likeCount": 89
}
```

### 5.2 取消点赞

```http
DELETE /api/videos/{videoId}/likes
Authorization: Bearer <token>
```

### 5.3 收藏视频

```http
POST /api/videos/{videoId}/favorites
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": 1,
  "favorited": true,
  "favoriteCount": 24
}
```

### 5.4 取消收藏

```http
DELETE /api/videos/{videoId}/favorites
Authorization: Bearer <token>
```

## 6. 评论与弹幕

### 6.1 评论列表

```http
GET /api/videos/{videoId}/comments?page=1&pageSize=20
```

响应 `data.list` 单项：

```json
{
  "commentId": 1,
  "content": "这个视频很有意思",
  "likeCount": 0,
  "user": {
    "userId": 1,
    "nickname": "普通观众",
    "avatarUrl": ""
  },
  "createdAt": "2026-06-03T12:00:00"
}
```

### 6.2 发布评论

```http
POST /api/videos/{videoId}/comments
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "content": "这个视频很有意思",
  "parentId": null
}
```

### 6.3 弹幕列表

```http
GET /api/videos/{videoId}/danmakus?startTime=0&endTime=60
```

### 6.4 发送弹幕

```http
POST /api/videos/{videoId}/danmakus
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "content": "前方高能",
  "timeSeconds": 15,
  "color": "#ffffff"
}
```

## 7. 用户关系

### 7.1 关注用户

```http
POST /api/users/{userId}/follow
Authorization: Bearer <token>
```

### 7.2 取消关注

```http
DELETE /api/users/{userId}/follow
Authorization: Bearer <token>
```

### 7.3 用户公开信息

```http
GET /api/users/{userId}
```

响应 `data`：

```json
{
  "userId": 2,
  "nickname": "校学生会",
  "avatarUrl": "",
  "bio": "",
  "followed": false,
  "followerCount": 20,
  "followingCount": 5,
  "videoCount": 8
}
```

## 8. 直播

### 8.1 直播间列表

```http
GET /api/live/rooms?page=1&pageSize=12&categoryId=0
```

### 8.2 创建直播间

```http
POST /api/live/rooms
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "title": "航音社团招新直播",
  "categoryId": 1,
  "coverUrl": ""
}
```

响应 `data`：

```json
{
  "roomId": 1,
  "title": "航音社团招新直播",
  "pushUrl": "rtmp://localhost/live/demo-stream-key",
  "pullUrl": "http://localhost:8080/live/demo-stream-key.m3u8",
  "status": "online"
}
```

### 8.3 直播间详情

```http
GET /api/live/rooms/{roomId}
```

### 8.4 关闭直播间

```http
POST /api/live/rooms/{roomId}/close
Authorization: Bearer <token>
```

### 8.5 直播消息列表

```http
GET /api/live/rooms/{roomId}/messages?page=1&pageSize=50
```

### 8.6 发送直播消息

```http
POST /api/live/rooms/{roomId}/messages
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "content": "主播晚上好"
}
```

## 9. 文档变更规则

- 新增接口前必须先确认接口所属模块。
- 修改接口路径、请求字段、响应字段时，必须同步修改本文档。
- 删除接口前必须确认没有前端页面或其他后端模块依赖。
- 本文档只描述稳定接口契约，不记录开发排期、验收优先级和个人任务分工。
