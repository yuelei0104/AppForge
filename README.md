# 智构 AI · AppForge

智能应用生成与发布平台。逐步实现需求描述、AI 代码生成、在线预览、多轮修改和应用发布。

## 当前进度

工程骨架与启动代码已准备好，当前采用一个可运行的后端工程。启动类、Java 源码和配置都直接位于 `appforge-backend/src` 下。

当前提供 `GET /api/health`，用于确认 HTTP 服务可以响应。用户功能、数据库、AI 调用与前端页面仍按阶段开发。

## 项目结构

| 路径 | 职责 |
| --- | --- |
| `appforge-backend/pom.xml` | 可运行后端工程，打包类型为 `jar` |
| `appforge-backend/src/main/java/` | 启动类与 Java 业务代码 |
| `appforge-backend/src/main/resources/` | `application.yml` 等运行配置 |
| `appforge-web/` | 前端预留目录 |
| `docs/` | 工程约定与分阶段验收说明 |
| `LICENSES/` | 随附第三方工具的许可文件 |

总目录是本 README 所在的 `AppForge` 文件夹。Git 在总目录统一管理，Maven 在 `appforge-backend` 中构建。

## IDEA 导入

1. 打开总目录，例如 `D:\AppForge-project-skeleton\AppForge`。
2. 将 `appforge-backend/pom.xml` 添加为 Maven 项目并重新加载。
3. 项目 SDK、Maven Importer 与 Maven Runner 使用 JDK 21。
4. 打开 `appforge-backend/src/main/java/com/yuelei/appforge/AppForgeApplication.java`，运行 `main` 方法。

本次布局将原 `appforge-app` 子模块中的启动代码移到了后端根工程。`appforge-backend/pom.xml` 已改为 `jar`，不再登记子模块。需要将 IDEA 中旧的 `appforge-app` Maven 项目取消关联后重新加载后端 POM。

## 命令行验收

```powershell
cd D:\AppForge-project-skeleton\AppForge\appforge-backend
.\mvnw.cmd -v
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

Wrapper 固定使用 Maven 3.9.9；首次构建需要下载依赖。运行时终端持续占用是正常现象，按 `Ctrl+C` 停止服务。

浏览器访问 http://localhost:8123/api/health ，预期返回：

```json
{"status":"UP","service":"appforge-backend"}
```

启动、配置和 JAR 运行方式见 [后端说明](appforge-backend/README.md)。本阶段无需 MySQL、Redis 或 Docker。

## Git 管理

后端变更验收通过后，在总目录执行：

```powershell
cd D:\AppForge-project-skeleton\AppForge
git status
git add README.md docs appforge-backend
git diff --cached --stat
git commit -m "refactor: run backend directly from its source root"
```

检查暂存列表后再提交，需要同步远程时再执行 `git push`。仓库为 https://github.com/yuelei0104/AppForge 。

## 后续工作

按 [工程结构](docs/01-project-layout.md) 和 [开发路线](docs/02-roadmap.md) 逐步开发。先完成本机启动验收，再添加下一项功能。

第三方工具说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
