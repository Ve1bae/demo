# AI 与开源使用说明

## AI 工具

本项目使用生成式 AI 辅助整理 Kubernetes 清单、GitHub Actions、测试断言和文档结构。所有改动均由组员检查，并通过 Maven、前端构建和 GitHub Actions 实际运行验证；AI 输出不作为未经验证的最终结论。

## 开源组件与来源

| 组件 | 用途 | 来源/许可证 |
| --- | --- | --- |
| Spring Boot、MyBatis-Plus | 后端框架和持久化 | 官方发行版及其许可证 |
| Vue、Vite、Nginx | 前端构建和静态服务 | 官方发行版及其许可证 |
| MySQL、MinIO、SRS | 数据库、对象存储、流媒体 | 官方镜像/项目许可证 |
| Kind、Kubernetes、local-path-provisioner | CI 集群和 PVC 验证 | 官方文档/项目许可证 |

使用第三方代码、模板或数据时，应在此表补充版本、许可证和链接；不得把密码、Token、数据库口令或云平台密钥提交到仓库。
