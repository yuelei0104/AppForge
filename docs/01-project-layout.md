# 工程结构与演进规划

## 当前布局

当前采用前后端独立目录、后端单个可运行 Maven 工程的结构。

| 路径 | 职责 |
| --- | --- |
| 总目录 `AppForge` | Git 仓库、说明文档、前后端目录 |
| `appforge-backend/pom.xml` | 后端依赖与构建管理，`packaging=jar` |
| `appforge-backend/src/main/java/com/yuelei/appforge/AppForgeApplication.java` | 启动类 |
| `appforge-backend/src/main/java/com/yuelei/appforge/controller/HealthController.java` | HTTP 健康接口 |
| `appforge-backend/src/main/resources/application.yml` | 服务名称、端口和上下文路径 |
| `appforge-web` | 前端预留目录 |

后端直接拥有 `src`，其中资源目录按 Maven 标准命名为 `resources`。当前不包含 `appforge-app` 子模块，也不包含 `<modules>`。

## 后续 Java 业务包规划

下面是组织规划，尚未实现的包不计为项目能力。包均位于 `com.yuelei.appforge` 下。

| 候选包 | 计划职责 |
| --- | --- |
| `common` | 通用响应、异常及公共工具 |
| `user` | 用户、登录与权限 |
| `application` | 应用管理 |
| `generation` | 生成任务、状态与编排 |
| `ai` | 模型适配、提示词与工具 |
| `deployment` | 构建产物与发布 |
| `screenshot` | 截图与封面 |

按每项功能的实际需要增加 Controller、Service、DTO 等，不一次性创建全部空目录。

## 演进约定

1. 当前 `appforge-backend` 是可运行的后端工程，直接包含启动类与配置。
2. 业务按包逐步拆分。Java 包和独立 Maven 模块是两种不同的组织方式。
3. 后续确实需要独立服务或共享库时，再重新设计聚合父工程与模块关系；当前 JAR 工程不登记子模块。
4. AI 输出、构建缓存、发布产物和本地密钥配置不进入 Git。
5. 构建隔离、应用级并发控制与产物版本在后续阶段实现。

## 命名约定

- 项目：智构 AI / AppForge。
- 仓库：`yuelei0104/AppForge`。
- 后端 artifactId：`appforge-backend`。
- Java groupId：`com.yuelei`。
- Java 基础包名：`com.yuelei.appforge`。
- Git 默认分支：`main`。
