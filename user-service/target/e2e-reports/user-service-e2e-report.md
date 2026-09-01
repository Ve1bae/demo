# user-service 端到端测试报告

- 执行时间：2026-09-01T04:07:29.146Z
- 服务地址：http://127.0.0.1:18082
- 结果：通过

## 测试链路

健康检查 -> 注册 Alice/Bob -> 登录 Alice -> 用户资料 -> 修改头像 -> 关注 Bob -> 查询关注/粉丝 -> 内部查询 -> 取消关注 -> 再次查询关注列表。

## 用例明细

### E2E-01 健康检查
- 请求：`GET /actuator/health`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"groups":["liveness","readiness"],"status":"UP"}`
- 断言：通过

### E2E-02 服务信息
- 请求：`GET /actuator/info`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{}`
- 断言：通过

### E2E-03 注册 Alice
- 请求：`POST /api/user/register`
- 输入：`{"body":{"username":"e2e_alice_1788235648867_44631","password":"***","nickname":"E2E Alice 1788235648867_44631"},"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"注册成功","data":null}`
- 断言：通过

### E2E-04 注册 Bob
- 请求：`POST /api/user/register`
- 输入：`{"body":{"username":"e2e_bob_1788235648867_44631","password":"***","nickname":"E2E Bob 1788235648867_44631"},"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"注册成功","data":null}`
- 断言：通过

### E2E-05 登录 Alice
- 请求：`POST /api/user/login`
- 输入：`{"body":{"username":"e2e_alice_1788235648867_44631","password":"***","nickname":"E2E Alice 1788235648867_44631"},"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":{"avatarUrl":null,"id":1,"nickname":"E2E Alice 1788235648867_44631","username":"e2e_alice_1788235648867_44631"}}`
- 断言：通过

### E2E-06 登录 Bob
- 请求：`POST /api/user/login`
- 输入：`{"body":{"username":"e2e_bob_1788235648867_44631","password":"***","nickname":"E2E Bob 1788235648867_44631"},"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":{"avatarUrl":null,"id":2,"nickname":"E2E Bob 1788235648867_44631","username":"e2e_bob_1788235648867_44631"}}`
- 断言：通过

### E2E-07 查询用户资料
- 请求：`GET /api/user/1/profile?viewerId=1`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":{"avatarUrl":null,"following":false,"nickname":"E2E Alice 1788235648867_44631","bio":null,"id":1,"userId":1,"followingCount":0,"followerCount":0,"username":"e2e_alice_1788235648867_44631"}}`
- 断言：通过

### E2E-08 修改头像
- 请求：`PUT /api/user/1/avatar`
- 输入：`{"body":{"avatarUrl":"https://example.test/e2e/1788235648867_44631.png"},"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":{"avatarUrl":"https://example.test/e2e/1788235648867_44631.png"}}`
- 断言：通过

### E2E-09 关注用户
- 请求：`POST /api/user/2/follow`
- 输入：`{"headers":{"X-User-Id":"1"}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"关注成功","data":null}`
- 断言：通过

### E2E-10 查询关注列表
- 请求：`GET /api/user/1/following`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":[{"avatarUrl":null,"following":true,"nickname":"E2E Bob 1788235648867_44631","bio":null,"id":2,"userId":2,"username":"e2e_bob_1788235648867_44631"}]}`
- 断言：通过

### E2E-11 查询粉丝列表
- 请求：`GET /api/user/2/followers`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":[{"avatarUrl":"https://example.test/e2e/1788235648867_44631.png","following":false,"nickname":"E2E Alice 1788235648867_44631","bio":null,"id":1,"userId":1,"username":"e2e_alice_1788235648867_44631"}]}`
- 断言：通过

### E2E-12 内部用户查询
- 请求：`GET /api/user/internal/1`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":{"avatarUrl":"https://example.test/e2e/1788235648867_44631.png","nickname":"E2E Alice 1788235648867_44631","bio":null,"id":1,"userId":1,"username":"e2e_alice_1788235648867_44631"}}`
- 断言：通过

### E2E-13 取消关注
- 请求：`DELETE /api/user/2/follow`
- 输入：`{"headers":{"X-User-Id":"1"}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"取消关注成功","data":null}`
- 断言：通过

### E2E-14 验证取消关注
- 请求：`GET /api/user/1/following`
- 输入：`{"headers":{}}`
- HTTP 状态：200
- 输出：`{"code":200,"message":"success","data":[]}`
- 断言：通过

