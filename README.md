# AppForge

**把想法，锻造成可预览、可修改、可发布的网页应用。**

AppForge 是一个基于 Java 与 Vue 的 AI 应用生成平台。用户描述需求后，在同一个工作台完成代码生成、页面预览、多轮修改、版本对比、源码下载和发布。

> 当前说明对应 `feat/frontend-creation-workspace` 分支，包含 Forge 界面和微服务迁移代码。该分支尚未合并到 `main`。仓库同时保留单体与微服务工程；微服务仍处于拆分和联调阶段，启动方式见下文。

[选择运行方式](#选择运行方式) · [快速启动](#快速启动) · [微服务指南](appforge-microservices/README.md) · [功能说明](#功能说明) · [测试与验证](#测试与验证) · [文档导航](#文档导航)

## 界面预览

### 创作首页

暖纸色背景、橙色行动按钮与作品展示区组成统一的 Forge 视觉系统。首页提供需求输入、场景示例、生成模式选择、我的作品和公开案例。

![AppForge 创作首页](docs/ui-redesign/implemented-home.png)

### 创作工作台

对话、预览与代码在同一工作区中切换，支持文件差异查看、设备预览、生成进度与版本操作。移动端提供独立的对话和预览切换。

![AppForge 创作工作台](docs/ui-redesign/implemented-workspace.png)

截图使用仓库中的界面验收示例，不代表线上运营数据。更多界面与设计说明见 [Forge UI 改版](docs/ui-redesign/README.md)。

## 功能说明

| 模块 | 当前能力 |
| --- | --- |
| 需求创建 | 场景示例填入需求，显式选择生成模式；创建后核对实际保存的模式 |
| AI 生成 | SSE 流式回复、工具操作与文件进度；支持生成中停止、错误提示和继续修改 |
| 对话 | 历史分页、Markdown 与代码展示、单条消息复制、对话导出和记忆摘要 |
| 工作台 | 页面预览、源码与差异查看、桌面/手机预览、分栏调整、可视化选区修改 |
| 版本 | 完整生成后保存新版本、文件对比、历史恢复；失败或停止保留上一成功版本 |
| 交付 | 下载 HTML/CSS/JS 源码或 Vue 工程 ZIP；发布、更新发布与下线 |
| 作品发现 | 私有/公开可见性、名称与标签搜索、精选申请、管理员审核与置顶 |
| 账号 | 注册昵称、个人昵称修改、原密码校验改密、管理员协助找回密码 |
| 附件 | 图片、文本与文档上传；文档文本提取；本地回退与 COS 存储接入 |
| 自动封面 | 按版本排队截图、有限重试、状态查询、手动重试和自定义封面；需配置 Chrome、COS 与任务表 |
| 输入与成本 | 输入护栏、路由缓存、符合条件的首次生成结果缓存、普通 API 压缩 |

### 三种生成模式

| 首页名称 | 后端类型 | 产物 | 额外运行条件 |
| --- | --- | --- | --- |
| 单页站点 | `html` | 单个 HTML 页面 | 可用的模型 API |
| 轻量网站 | `multi_file` | HTML、CSS、JavaScript 多文件 | 可用的模型 API |
| 多页应用 | `vue_project` | 受限的 Vue 工程与构建后的 `dist` | 模型 API、Docker CLI 与运行中的 Docker daemon |

Vue 工程通过受限文件工具读写源码，再由平台固定的 Docker 流水线安装依赖和构建。只有完整源码通过校验且构建成功，才保存新版本并更新预览。模型不能直接执行任意宿主机命令；当前工程模板不支持任意新增依赖。详见 [Vue 工程生成](docs/06-vue-project-generation.md)。

### 公开作品与精选

新作品默认私有。“全部公开应用”展示符合公开条件的作品；“精选案例”由管理员审核和优先级控制，**不是实时热度排行榜**。没有公开作品或审核通过的案例时显示空状态，不填充虚构作品。

### API Key 与用户费用

当前模型凭据由后端统一配置，用户注册后不需要单独填写 Key。实际模型费用计入后端所用 Key 的账户。个人额度、充值支付、用户自带 Key 和跨实例统一总并发配额不在当前交付范围；现有缓存与任务保护不等同于完整计费系统。

## 架构与技术栈

| 层次 | 主要技术与职责 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Ant Design Vue；创作工作台与状态反馈 |
| Web 与业务 | Java 21、Spring Boot 3.5.16、Spring MVC/SSE；认证、应用、版本、发布与访问控制 |
| 微服务改造 | Maven 多模块、Dubbo 3.3.0、Nacos 注册、Higress HTTP 网关；当前拆分边界见下文 |
| AI | LangChain4j 1.11.11 核心、OpenAI 兼容模型接口、工具调用、记忆与输入护栏 |
| 数据 | MySQL、MyBatis-Flex、Redis、Spring Session、Caffeine；业务持久化、会话、记忆与缓存 |
| 工程构建 | Docker；固定安装/构建命令、任务目录挂载和资源限制 |
| 文件与封面 | 本地文件、腾讯云 COS、Apache Tika、Chrome/Selenium；附件与异步截图 |

生成链路为：前端提交需求并取得一次性 ticket → 后端校验身份与应用权限 → 模型流式生成或工具写入 → 校验与按需构建 → 保存版本 → 更新预览。发布绑定一个成功版本，预览版本与已发布版本独立。

### 当前服务划分

| 模块 | 当前实现 |
| --- | --- |
| `user` | 用户服务，HTTP 8124、Triple 50051；注册、登录、昵称和密码修改 |
| `app` | 应用服务，HTTP 8125、Triple 50053；生成、对话、附件、版本、发布和封面任务 |
| `screenshot` | 截图服务，HTTP 8127、Triple 50052；提供截图上传的内部接口 |
| `ai` | 模型、工具、护栏和监控依赖，由 app 进程加载，当前不是独立 AI 服务 |
| `common` / `model` / `client` | 公共能力、数据模型和内部服务契约 |

微服务工程已提供独立启动类与 Dubbo 服务实现；目前仍共享数据库和 Redis Session，app 也保留部分本地用户逻辑及封面渲染。它不是已经完成独立分库、全远程调用和分布式任务调度的最终形态。

生成任务仍包含进程内运行状态。拆分进程不等于支持应用服务任意水平扩容，增加 app 实例前需补充任务路由、跨节点停止与运行文件访问机制。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `appforge-backend/` | 单体 Spring Boot 工程、Maven Wrapper 与现有后端回归测试 |
| `appforge-microservices/` | 微服务聚合父工程；common、model、client、ai、user、app、screenshot 七个子模块 |
| `appforge-vue/` | 实际 Vue 前端、API 客户端、页面、组件与 Vitest 测试 |
| `sql/create_table.sql` | 全新数据库初始化脚本 |
| `sql/migrations/` | 已有数据库的分阶段升级脚本 |
| `docs/` | 功能说明、设计、运行边界与验收方法 |
| `docs/ui-redesign/` | Forge 视觉方案、设计稿与实际界面截图 |
| `.github/workflows/` | Vue 工程构建及相关回归验证流水线 |
| `LICENSES/`、`THIRD_PARTY_NOTICES.md` | 随附第三方工具的许可与声明 |

Git 在仓库根目录管理。单体 Maven 命令在 `appforge-backend` 执行；微服务聚合构建在 `appforge-microservices` 执行；npm 命令在 `appforge-vue` 执行。生成目录与附件位于运行时 `tmp/` 下，请固定工作目录并与数据库成套备份。

## 选择运行方式

| 方式 | 后端入口 | 前端 `/api` 代理目标 | 适用场景 |
| --- | --- | --- | --- |
| 单体 | `AppForgeApplication`，8123 | `http://localhost:8123` | 使用原有工程运行完整产品功能 |
| 微服务 | user 8124、app 8125；截图能力另启 screenshot 8127 | `http://localhost:18080`，需先配置 Higress | 服务拆分、Dubbo/Nacos 与网关联调 |

下面的快速启动以**单体方式**为例；微服务的配置、启动类、端口、路由和验证步骤见 [微服务运行指南](appforge-microservices/README.md)。

当前提交中的 Vite 代理仍指向 `8123`。只启动微服务、却不修改代理，不会自动切换到 Higress。示例中的 `8001` 是 Higress 控制台，`18080` 才是映射到宿主机的 HTTP 网关入口。

单体登录态和微服务登录态也不要默认混用。两套代码的用户模型包名不同，切换后应退出并重新登录；需要并行运行时，隔离两套环境的 Session namespace。

## 快速启动

以下以 Windows PowerShell 和单体后端为例。已有项目先保存本地改动，再更新分支。微服务不要直接照搬本节的后端端口，改用 [专用指南](appforge-microservices/README.md)。

### 1. 获取代码并准备环境

```powershell
git clone --branch feat/frontend-creation-workspace https://github.com/yuelei0104/AppForge.git
cd AppForge
```

| 组件 | 要求 |
| --- | --- |
| JDK | 21 |
| Maven | 单体使用自带 Wrapper，固定 3.9.9；微服务当前使用已安装的 Maven |
| Node.js | `^22.18.0` 或 `>=24.12.0`，以 `appforge-vue/package.json` 为准 |
| 数据库 | MySQL 8.x |
| Redis | 会话、记忆、缓存和密码重置码使用；测试流水线使用 Redis 7.4 |
| 模型 | 可访问的 OpenAI 兼容接口、有效 Key，以及该账户可用的模型名称 |
| Docker | 生成 Vue 工程时必需；Windows 使用 Docker Desktop 的 Linux 容器引擎 |
| Chrome 与 COS | 自动封面截图、真实云存储上传时配置 |

首页默认选择“多页应用”，因此完整体验前先用 `docker info` 确认 Server 可用。暂未准备 Docker 时，可选择“单页站点”或“轻量网站”。

### 2. 初始化或升级数据库

**全新数据库**：先检查 [初始化脚本](sql/create_table.sql)，删除或替换其中的示例管理员授权语句，再从仓库根目录执行：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p --execute="source sql/create_table.sql"
```

脚本创建 `appforge` 数据库及用户、应用、版本、对话、记忆、封面任务和附件等表。部分建表语句不能重复执行，初始化脚本不是升级脚本。

**已有数据库**：先备份数据库与 `tmp/`，只执行尚未应用的迁移。依次参照 [工作台升级](docs/03-app-workspace.md)、[对话历史](docs/04-chat-history.md)、[记忆摘要](docs/05-chat-conversation-extensions.md) 和 [封面升级](docs/08-app-cover.md)。若缺少附件表，按 [生成体验扩展](docs/10-generation-experience-design.md) 补建 `app_attachment`，不要重跑整份初始化 SQL。

### 3. 配置后端

创建本机文件 `appforge-backend/src/main/resources/application-local.yml`。项目默认启用 `local` profile，该文件已被 Git 忽略。以下配置覆盖连接信息，并让四个模型入口读取同一组本机环境变量：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/appforge?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${APPFORGE_DB_USER:root}
    password: ${APPFORGE_DB_PASSWORD}
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${APPFORGE_REDIS_PASSWORD:}

langchain4j:
  open-ai:
    chat-model:
      base-url: ${APPFORGE_MODEL_BASE_URL}
      api-key: ${APPFORGE_MODEL_API_KEY}
      model-name: ${APPFORGE_MODEL_NAME}
      log-requests: false
      log-responses: false
    streaming-chat-model:
      base-url: ${APPFORGE_MODEL_BASE_URL}
      api-key: ${APPFORGE_MODEL_API_KEY}
      model-name: ${APPFORGE_MODEL_NAME}
      log-requests: false
      log-responses: false
    reasoning-streaming-chat-model:
      base-url: ${APPFORGE_MODEL_BASE_URL}
      api-key: ${APPFORGE_MODEL_API_KEY}
      model-name: ${APPFORGE_MODEL_NAME}
      log-requests: false
      log-responses: false
    routing-chat-model:
      base-url: ${APPFORGE_MODEL_BASE_URL}
      api-key: ${APPFORGE_MODEL_API_KEY}
      model-name: ${APPFORGE_MODEL_NAME}
      log-requests: false
      log-responses: false

logging:
  level:
    dev.langchain4j: INFO

# 本地不使用云存储时的非空占位；真实上传前替换为自己的 COS 配置。
cos:
  client:
    host: https://your-bucket.cos.ap-shanghai.myqcloud.com
    secret-id: ${TENCENT_COS_SECRET_ID:local-disabled}
    secret-key: ${TENCENT_COS_SECRET_KEY:local-disabled}
    region: ap-shanghai
    bucket: ${COS_BUCKET:local-disabled}

# 先验证生成主流程，准备好 Chrome、COS 和任务表后再启用封面。
app:
  cover:
    enabled: false
    base-url: http://127.0.0.1:8123/api
    public-base-url: http://localhost:8123/api
```

在**启动后端的同一终端**设置对应变量；使用自己的值替换占位文字：

```powershell
$env:APPFORGE_DB_PASSWORD = "你的数据库密码"
$env:APPFORGE_MODEL_BASE_URL = "https://api.deepseek.com"
$env:APPFORGE_MODEL_API_KEY = "你的模型密钥"
$env:APPFORGE_MODEL_NAME = "你的账户实际可用的模型名称"
```

这些 `APPFORGE_MODEL_*` 变量由上面的本机 YAML 引用；仅设置变量、不添加对应配置，不会自动覆盖仓库中的模型配置。IDEA 启动请将环境变量写入运行配置。数据库或 Redis 映射到其他主机端口时，同时修改本机 YAML。

模型 Key 只留在后端，不要放进 `VITE_*`、前端代码、截图或提交记录。各模型入口也可独立配置不同模型，接口、名称与参数兼容性需按实际供应商确认。

当前单体的仓库配置已设置 `app.cover.enabled: true`，并包含开发机驱动路径；上面的本机模板显式关闭封面，避免沿用其他机器的环境。`CosClientConfig` 仍会创建 COS 客户端，本地占位只用于未使用云存储的调试，不具备上传能力。真实上传须配置 `cos.client.host`、`secret-id`、`secret-key`、`region`、`bucket`；启用截图还需 Chrome、适合本机的驱动、对应数据库表和可访问的后端地址，见 [封面启用与验证](docs/08-app-cover.md)。专题文档中的默认值描述需以实际加载的 YAML 为准。

### 4. 启动后端

```powershell
cd appforge-backend
.\mvnw.cmd -v
.\mvnw.cmd spring-boot:run
```

也可在 IDEA 导入 `appforge-backend/pom.xml`，使用 JDK 21 运行 `com.yuelei.appforge.AppForgeApplication`。同一时间使用一种启动方式，避免重复占用端口。

另开终端检查 [后端健康接口](http://localhost:8123/api/health/)：

```powershell
Invoke-RestMethod http://localhost:8123/api/health/
```

预期返回项目的 BaseResponse，包含 `code: 0` 和 `data: "ok"`。该接口只检查 HTTP 服务能响应，**不代表数据库、Redis、模型或 Docker 已完成联调**。

### 5. 启动前端

另开终端，从仓库根目录执行：

```powershell
cd appforge-vue
npm ci
npm run dev
```

打开终端显示的地址，默认 [http://localhost:5173](http://localhost:5173)。开发服务器将 `/api` 代理到 `http://localhost:8123`，前端默认无需新增环境文件。

更改后端端口时，同步修改 `appforge-vue/vite.config.ts` 的代理目标，以及启用封面时的后端访问地址。前端还支持 `VITE_API_BASE_URL`、`VITE_STATIC_BASE_URL` 和 `VITE_DEPLOY_DOMAIN`，见 [配置定义](appforge-vue/src/config/env.ts)。同一次登录尽量统一使用 `localhost`，不要交替使用 `127.0.0.1`。

### 6. 创建账号并体验

1. 在注册页设置账号、昵称与密码；新账号默认为普通用户，没有通用管理员密码。
2. 输入需求并选择模式，创建应用，等待生成完成后查看预览。
3. 发送修改需求，验证新版本、文件对比、源码下载与发布。
4. 需要管理员时，由数据库维护者核对目标账号后，在数据库中将该用户的 `userRole` 设为 `admin`，再重新登录；不要直接写入明文密码。
5. 点击头像 → 个人设置，可修改昵称与密码。忘记密码使用管理员核验后签发的 10 分钟一次性重置码，当前没有自动邮件或短信找回。

## 构建与部署

两种后端使用同一个前端。前端生产构建：

```powershell
cd appforge-vue
npm run build
```

单体后端打包与运行，在 `appforge-backend` 目录执行：

```powershell
.\mvnw.cmd clean package
java -jar .\target\appforge-backend-0.1.0-SNAPSHOT.jar
```

微服务在 `appforge-microservices` 目录构建，各服务分别生成 JAR：

```powershell
mvn -DskipTests install
```

先为各服务补齐本机配置，再按 [微服务指南](appforge-microservices/README.md) 启动 user、app 和按需使用的 screenshot。内部依赖模块不作为独立服务启动。

前端 `dist/` 交给静态服务器托管，并将 `/api` 反向代理到选定的单体或 Higress 网关；为 Vue 页面路由配置入口回退。SSE 路径应关闭代理缓冲并设置合适的读取超时。应用发布链接仍通过后端资源接口访问，不要直接公开 `tmp/`，否则会绕过私有、下线和删除校验。

本仓库不提供完整的平台一键 Compose 部署。Vue 工程生成所用的 Docker 构建容器，与平台本身的部署是两件事；后端运行环境必须能调用 Docker 并挂载任务目录。生产运行另需配置 HTTPS、持久化备份、密钥管理与资源配额。

## 测试与验证

前端测试与构建：

```powershell
cd appforge-vue
npm test
npm run build
```

后端基础回归，在后端目录执行：

```powershell
.\mvnw.cmd "-Dtest=AppCreationModeTest,AppWorkspaceTest,AppServiceAccessTest,ChatHistoryIntegrationTest,ChatConversationExtensionsTest,CodeParserTest" test
```

现有后端测试与下述 CI 主要覆盖 `appforge-backend` 单体。微服务的 Maven 构建、服务注册、网关转发、跨服务 Session 和真实模型生成，需要单独验证，当前文档不将其标记为已通过。

仓库提供 [Vue runtime verification 流水线](https://github.com/yuelei0104/AppForge/actions/workflows/vue-runtime-verification.yml)，包含前端测试/构建、选定后端回归、真实 Docker 构建及 Chrome/MySQL/Redis 专项验证。各次执行结果以 Actions 页面为准；工作流的 push 触发分支有限，也可从 Actions 手动运行。

外部服务测试需显式开关：

| 开关 | 用途 |
| --- | --- |
| `APPFORGE_DOCKER_TEST` | 真实容器构建验证 |
| `APPFORGE_LIVE_MODEL_TEST` | 真实模型验证，需独立测试凭据，可能产生费用 |
| `APPFORGE_COVER_CHROME_TEST` | Chrome 封面截图验证 |
| `APPFORGE_COVER_MYSQL_TEST` | 专用 MySQL 测试库中的迁移/并发验证 |
| `APPFORGE_PASSWORD_REDIS_TEST` | 隔离 Redis 中的一次性重置码验证 |

不要把专项测试连接到业务数据库或生产 Redis。开关、测试端口与完整命令见对应专题文档和工作流。模拟模型、隔离页面数据、真实模型验证与完整部署验收是不同层次，不能互相替代。

建议在实际运行环境完成：注册 → 创建 → 生成 → 修改 → 版本恢复 → 下载 → 发布 → 公开/私有校验 → 下线。未接入真实环境时，不将单元测试结果表述为全链路上线验收。

## 常见问题

| 现象 | 检查方法 |
| --- | --- |
| HTTP 端口已被占用 | 单体默认 8123，微服务为 8124/8125/8127；停止同一服务的旧进程，或调整配置并同步代理与网关 |
| Docker 找不到 Linux engine / named pipe | 启动 Docker Desktop，等待 `docker info` 的 Server 正常后再生成 Vue 工程 |
| 表或列不存在 | 核对数据库名和已经执行的迁移，不要用重复初始化代替升级 |
| 精选与公开案例为空 | 先生成并公开作品；精选还需管理员审核；搜索条件也会过滤结果 |
| 模型返回 401、无权限或截断 | 检查实际运行进程的 Key、模型名称、余额与输出限制；保留旧版本后调整需求重试 |
| 封面一直是占位图 | 核对截图开关、Chrome、COS 权限、任务状态及后端访问地址 |
| 登录后仍提示未登录 | 核对 Redis 与 Cookie；微服务还需一致的 Redis 逻辑库、Session namespace 与序列化器 |
| 微服务前端提示网络异常 | 仓库 Vite 默认目标仍是 8123，改为已配置的 Higress 18080 并重启；检查绝对 API 地址覆盖 |
| Higress 返回 503 / upstream connect error | 网关已收到请求，但连接上游失败；核对容器可达地址和 HTTP 8124/8125，不要填 Triple 端口 |
| 微服务找回密码 404 | 两条 reset 接口目前在 app，需比 `/api/user` 更优先的精确路由，见微服务指南 |
| GitHub 拒绝包含密钥的推送 | 将密钥改为环境变量或被忽略的本机配置，并从所有待推送提交中移除明文；不能只新增删除提交 |

## 当前边界

- 生成目标是网页及受限 Vue 前端工程，不自动生成并上线任意 Java 后端业务系统。
- 微服务处于迁移联调阶段；AI 尚未独立成进程，用户读取与封面调用仍有本地实现，当前共享数据库。
- 多实例统一调度、跨节点停止、用户计费与完善的全平台成本配额仍需建设。
- 现有生成结果缓存按提示词与生成类型共享，且只覆盖符合条件的首次创建；隐私和近似命中边界见 [成本优化](docs/12-cost-optimization.md)。
- 自动封面与真实云上传依赖外部服务；账号找回依赖管理员核验，尚未绑定邮箱或手机验证。
- 公开运营前还需完成真实环境验收，并评估现有密码摘要方案向现代自适应密码哈希迁移。

## 文档导航

| 主题 | 文档 |
| --- | --- |
| 工程布局与早期路线 | [工程结构](docs/01-project-layout.md) · [路线图](docs/02-roadmap.md) |
| 应用权限、版本、精选与发布 | [工作台扩展](docs/03-app-workspace.md) |
| 历史、导出与记忆摘要 | [对话历史](docs/04-chat-history.md) · [对话扩展](docs/05-chat-conversation-extensions.md) |
| Vue 工具生成与 Docker 构建 | [Vue 工程生成](docs/06-vue-project-generation.md) |
| 自动封面 | [设计](docs/07-app-cover-design.md) · [启用与验证](docs/08-app-cover.md) |
| 可视化修改与附件 | [可视化编辑](docs/09-visual-edit.md) · [生成体验扩展](docs/10-generation-experience-design.md) |
| 输入护栏与缓存 | [护栏扩展](docs/11-guardrail-extensions.md) · [成本优化](docs/12-cost-optimization.md) |
| 创作流程与最新视觉系统 | [创作工作区](docs/13-frontend-creation-workspace.md) · [Forge UI](docs/ui-redesign/README.md) |
| 账号与使用帮助 | [账号设置](docs/14-account-settings.md) · [使用指南](docs/15-usage-guide.md) · [密码找回](docs/16-authentication-and-recovery.md) |
| 后端工程 | [单体说明](appforge-backend/README.md) · [微服务运行、网关路由与排障](appforge-microservices/README.md) |

## 第三方声明

随附工具与第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [LICENSES](LICENSES/)。
