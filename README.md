# 智构 AI · AppForge

智能应用生成与发布平台。逐步实现需求描述、AI 代码生成、在线预览、多轮修改和应用发布。

## 当前进度

后端位于 `appforge-backend`，前端位于 `appforge-vue`。当前已接入 MySQL、Redis、DeepSeek 与 COS，支持应用生成、在线预览、版本管理、部署、对话记忆和自动封面。

`GET /api/health` 用于确认 HTTP 服务可以响应；完整功能需要先准备数据库、Redis 和模型 API Key。

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

启动、配置和 JAR 运行方式见 [后端说明](appforge-backend/README.md)。运行完整功能需要 MySQL、Redis 和 DeepSeek API Key；COS 与 Chrome 用于附件持久化和自动封面，可按需启用。

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


## 应用工作台扩展

版本管理、私有/公开权限、标签搜索、停止生成、部署下线与精选审核的升级步骤见 [应用工作台扩展](docs/03-app-workspace.md)。已有数据库必须先执行其中的一次性升级 SQL，再重启后端。

对话轮数统计、Markdown 导出与智能记忆摘要的升级、使用和验证方式见 [对话扩展说明](docs/05-chat-conversation-extensions.md)。

## 自动应用封面

生成、版本恢复和部署后自动排队截图，支持有限重试、版本隔离、私有图片读取、手动重新生成、自定义封面和资源清理。失败保留原图或显示默认占位。

已有数据库先执行 `sql/migrations/20261110_app_cover.sql`，配置 COS 与 Chrome 后设置 `APP_COVER_ENABLED=true`。详细步骤见 [封面启用与验证](docs/08-app-cover.md)，架构约束见 [任务设计](docs/07-app-cover-design.md)。

## 生成体验扩展

创建/修改模式分离、SSE `file` 事件与代码视图、图片/文本/文档附件、聊天 ticket 已实现，说明与实施状态见 [生成体验扩展设计](docs/10-generation-experience-design.md)。

已有数据库需执行 `sql/create_table.sql` 中的 `app_attachment` 建表语句。主要接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/file/upload` | 上传聊天附件 |
| GET | `/api/file/content/{id}` | 读取本人附件（本地回退时） |
| POST | `/api/app/chat/request` | 换取一次性 SSE ticket |
| GET | `/api/app/chat/gen/code?requestId=` | 生成 SSE（`progress/file/done`） |
