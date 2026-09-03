# 微服务版交付说明

## 1. 交付内容

本目录和 `video-service/` 是 `feature/uc03-microservice-delivery` 分支的交付材料，基于 `microservice` 分支整理。当前版本以组长微服务版本为基础，保留三个独立业务服务，并将 UC-03 推荐逻辑、VideoPlayer 播放器交互修复、直播页面修复和微服务联调材料纳入同一工作区。`videoplayer` 分支没有作为独立后端服务再次合并；播放器属于前端表现层，代码位于 `frontend/demo/`。

| 测试层次 | 用例数 | 通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| user-service Maven 单元/API 测试 | 9 | 9 | 0 | 0 | 0 |
| video-service Maven 单元/API 测试 | 24 | 24 | 0 | 0 | 0 |
| live-service Maven 单元/API/WebSocket 测试 | 18 | 18 | 0 | 0 | 0 |
| user-service 真实 HTTP E2E | 14 | 14 | 0 | 0 | 0 |
| UC-03 Playwright 浏览器 E2E | 5 | 5 | 0 | 0 | 0 |
| UC-04/05/06 HTTP E2E | 3 | 3 | 0 | 0 | 0 |
| UC-07 HTTP E2E | 6 | 6 | 0 | 0 | 0 |
| 直播非 SRS 浏览器 E2E | 7 | 7 | 0 | 0 | 0 |

测试覆盖推荐排序、关键词与分类筛选、分页、关注作者、兴趣标签、观看历史、异常参数、游客访问、私密视频过滤以及页面交互等分支。

## 2. 代码与测试文件

### 微服务实现

- `video-service/src/main/java/com/example/video/controller/VideoController.java`
- `video-service/src/main/java/com/example/video/service/impl/VideoServiceImpl.java`
- `video-service/src/main/java/com/example/video/mapper/VideoMapper.java`
- `video-service/src/main/java/com/example/video/client/UserPreferenceClient.java`
- `video-service/sql/003_recommendation_schema.sql`

### JUnit 与 H2 API 测试

- `video-service/src/test/java/com/example/video/recommendation/RecommendationRankingTest.java`
- `video-service/src/test/java/com/example/video/recommendation/RecommendationApiH2Test.java`
- `video-service/src/test/java/com/example/video/VideoServiceUnitTest.java`
- `video-service/src/test/java/com/example/video/VideoApiIntegrationTest.java`

### 端到端测试

- `frontend/demo/e2e/recommendation.spec.js`（微服务网关浏览器回归）
- `frontend/demo/e2e/fixtures/recommendation.sql`
- `video-service/scripts/api-smoke-test.mjs`（真实 MySQL API，默认端口 8082）
- `video-service/e2e/`（完整 video-service 的 Playwright 链路）
- `video-service/pressure/`（k6 压力测试脚本和运行入口）
- `scripts/hpa-load-test.ps1`、`scripts/fault-drill-microservices.ps1`（HPA 和故障演练）
- `evidence/`（Compose、Kubernetes、HPA 和故障演练的真实输出）

## 3. 文档与测试报告

- `doc/UC-03-视频推荐微服务测试报告.md`：测试范围、测试用例、环境、结果、CI 证据和后续工作
- `doc/UC-03-视频推荐微服务迁移设计说明.md`：UC-03 迁移和与完整 video-service 的合并清单
- `doc/UC-03-视频推荐微服务接口契约.md`：接口请求、响应和错误码约定
- `doc/UC-03-video-service.openapi.yaml`：可导入 Swagger Editor 或 Apifox 的 OpenAPI 定义
- `video-service/README.md`：微服务运行、Docker API 测试和测试报告入口
- GitHub Actions 运行产物：由流水线 Artifact 保存，不作为运行生成物提交到 Git

## 4. 测试结果

当前交付分支已重新验证三个服务 Maven 测试合计 `51/51` 通过，并完成 user-service、视频互动、UC-03、UC-07 和直播非 SRS 浏览器真实回归。Compose 联调、Kubernetes 健康检查、故障降级、单体性能和微服务 API 性能均有证据目录。SRS 本机专项因缺少可信 FFmpeg 环境未完成；单体与微服务性能已按同机同参数各重复 3 次完成。

## 5. 运行入口

进入三个服务目录分别运行测试：

```powershell
Set-Location user-service; mvn -B -ntp test
Set-Location ..\video-service; mvn -B -ntp test
Set-Location ..\live-service; mvn -B -ntp test
```

真实 MySQL API 测试使用隔离 Docker 环境：

```powershell
$env:VIDEO_SERVICE_DB_PASSWORD = "<本机测试密码>"
docker compose -f compose.test.yml up -d --build
node scripts/api-smoke-test.mjs
cd e2e
npm install
npx playwright install chromium
npx playwright test
cd ..
docker compose -f compose.test.yml down -v
```

微服务浏览器 E2E 需要先启动微服务 Compose，再进入 `frontend/demo`：

```powershell
npm ci
npx playwright install chromium
npm run test:e2e:uc03
npm run test:e2e:uc07-10
```

## 6. CI 证据

- 历史 GitHub Actions 运行：`33468781654`（提交 `8608450`），仅作为旧 UC-03 分支基线
- 当前工作流：`.github/workflows/uc03-microservices-cicd.yml`；当前提交 push 后补充对应运行页和 Artifact 链接
- 当前交付分支：`feature/uc03-microservice-delivery`

当前工作区还没有提交或推送本地整合结果。任何提交前先核对文件清单并征得确认；任何推送或 PR 操作均需单独获得授权。运行生成的 Surefire、真实 API JSON、容器日志和 Playwright 报告保存在 `target/`、`e2e/report/` 或 CI Artifact，不作为源码提交。答辩验收状态以 `doc/答辩验收清单-微服务版.md` 为准。
