# 分阶段开发与验收

当前工程骨架已建立，启动代码调整为直接位于后端根工程，需要在本机完成启动验收。

| 阶段 | 工作范围 | 验收方式 |
| --- | --- | --- |
| 0：工程骨架 | 根目录、Maven、Wrapper、Git 与文档 | Maven 能识别后端工程 |
| 1：后端启动 | 启动类、运行配置、健康接口 | 启动成功，HTTP 健康接口返回 JSON |
| 2：前端初始化 | Vue 工程与请求封装 | 页面能展示后端健康接口结果 |
| 3：用户与应用 | 数据库、注册登录、创建应用和列表 | 登录并创建应用，刷新后数据仍在 |
| 4：单文件生成 | 模型适配、SSE、HTML 保存和预览 | 输入需求能生成并预览网页 |
| 5：持续修改 | 历史、上下文、应用级串行控制和任务状态 | 多轮修改可复现，失败明确反馈 |
| 6：构建与发布 | 多文件、Vue 工程、隔离构建和产物版本 | 工程能构建、下载和发布 |
| 7：服务演进 | 按实际需求拆分独立服务、监控与故障恢复 | 服务调用和故障场景验证通过 |

## 当前验收

在 `appforge-backend` 中执行 `mvnw.cmd clean package` 和 `mvnw.cmd spring-boot:run`。

访问 http://localhost:8123/api/health ，HTTP 200 且返回 `status=UP`、`service=appforge-backend` 即通过。详细步骤见 [后端说明](../appforge-backend/README.md)。

当前不需要数据库、消息队列或注册中心。性能指标和上线能力只有经过测试后才记入成果描述。

## 结构调整说明

- 启动类直接位于 `appforge-backend/src/main/java/com/yuelei/appforge`。
- 配置直接位于 `appforge-backend/src/main/resources`。
- 后端 POM 使用 `jar` 打包，不再充当聚合父工程。
- 启动命令不再携带 `-pl appforge-app`。
