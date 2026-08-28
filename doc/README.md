# UC-03 浏览推荐视频测试交付说明

## 1. 交付内容

本目录包含 UC-03“浏览推荐视频”的测试代码、测试计划、测试报告、追溯记录、CI 配置和原始测试结果。文件保留了项目中的相对路径，汇总时可按路径放回项目。

| 测试层次 | 用例数 | 通过 | 失败 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| 单元测试 | 24 | 24 | 0 | 0 |
| API 测试 | 9 | 9 | 0 | 0 |
| 端到端测试 | 5 | 5 | 0 | 0 |
| 合计 | 38 | 38 | 0 | 0 |

测试覆盖推荐排序、关键词与分类筛选、分页、关注作者、兴趣标签、观看历史、异常参数、游客访问、私密视频过滤以及页面交互等分支。

## 2. 代码文件

### 单元测试

- `backend/src/test/java/com/example/demo/controller/VideoControllerRecommendUnitTest.java`
- `backend/src/test/java/com/example/demo/service/impl/VideoRecommendationServiceUnitTest.java`
- `backend/src/test/java/com/example/demo/service/impl/VideoServiceImplRecommendationEdgeTest.java`

### API 测试

- `backend/src/test/java/com/example/demo/controller/VideoRecommendationApiIntegrationTest.java`

### 端到端测试

- `frontend/demo/e2e/recommendation.spec.js`
- `frontend/demo/e2e/fixtures/recommendation.sql`
- `frontend/demo/playwright.config.js`
- `frontend/demo/package.json`
- `frontend/demo/package-lock.json`
- `frontend/demo/.gitignore`

## 3. 报告文件

- `reports/UC-03-浏览推荐视频测试计划.md`
- `reports/UC-03-浏览推荐视频测试报告.md`
- `reports/课程要求核对表.md`
- `reports/CI-运行记录.md`
- `reports/raw-reports/`：GitHub Actions 生成的 Surefire、Playwright 和后端运行日志
- `docs/UC03_TRACEABILITY.md`：需求、三层模型、代码和测试结果追溯记录
- `.github/workflows/ci.yml`：包含 UC-03 三类测试任务和部署前置条件

## 4. 运行环境

- Java 21
- Spring Boot 4.0.6
- Maven Wrapper
- MySQL 8.0，数据库名 `hangyin_video`
- Node.js 22
- Playwright 1.62.1 和 Chromium

## 5. 运行入口

在完整项目中进入 `backend` 目录运行单元测试：

```bash
./mvnw -B -ntp -Dtest=VideoControllerRecommendUnitTest,VideoRecommendationServiceUnitTest,VideoServiceImplRecommendationEdgeTest test
```

数据库已按项目 `demo.sql` 初始化，并设置 `RUN_UC03_API_TESTS=true` 后，在 `backend` 目录运行 API 测试：

```bash
./mvnw -B -ntp -Dtest=VideoRecommendationApiIntegrationTest test
```

端到端测试需先启动 MySQL、后端和前端，并执行 `e2e/fixtures/recommendation.sql` 初始化测试数据。随后进入 `frontend/demo` 目录运行：

```bash
npm ci
npx playwright install chromium
npm run test:e2e:recommendation
```

## 6. CI 证据

- GitHub Actions：<https://github.com/Ve1bae/demo/actions/runs/33073287958>
- 测试提交：`156579c test: 完善UC-03视频推荐三类测试`
- 测试分支：`uc03-recommendation-tests`

CI 中的“UC-03 视频推荐单元测试”“UC-03 视频推荐 API 测试”和“UC-03 视频推荐端到端测试”均已成功，三个 Artifact 已放入 `reports/raw-reports/`。失败记录见测试报告：早期运行 `33036904983` 的 API Job 失败后，流水线标记为失败且部署没有执行。本包中的 `ci.yml` 用于组长汇总时对照，不应直接覆盖其他成员已经修改的共享流水线。
