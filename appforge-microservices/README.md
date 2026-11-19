# AppForge 微服务运行指南

本目录是 AppForge 的 Maven 多模块改造版本。现阶段提供用户、应用和截图三个 Spring Boot 进程；AI 能力仍作为应用服务的依赖模块运行。原单体位于 [appforge-backend](../appforge-backend/)，两种运行方式分别配置。

本文根据当前源码整理启动步骤。服务启动、Nacos 注册、网关转发、共享登录态和真实模型生成需要分别验证；仓库中的单体测试不代表微服务全链路已验收。

## 模块与端口

| 目录 | 类型与职责 | HTTP 端口 | Dubbo Triple 端口 | 启动类 |
| --- | --- | --- | --- | --- |
| `common` | 公共返回、异常、注解、COS 和工具依赖 | — | — | 无 |
| `model` | 实体、DTO/VO、工作区模型 | — | — | 无 |
| `client` | 内部服务接口 | — | — | 无 |
| `ai` | LangChain4j 模型、工具、护栏和监控依赖 | — | — | 无独立服务入口 |
| `user` | 注册、登录、昵称和密码修改 | 8124 | 50051 | `com.yuelei.UserApplication` |
| `app` | 应用、对话、附件、版本、发布、封面任务和密码找回 | 8125 | 50053 | `com.yuelei.Appslication` |
| `screenshot` | Selenium 截图与上传的内部服务 | 8127 | 50052 | `com.yuelei.ScreenshotApplication` |

`Appslication` 是当前源码中的类名。父 POM 的 `packaging` 为 `pom`；`common`、`model`、`client`、`ai` 是依赖模块，不需要分别启动。目录中的示例 `Main.java` 不能代替服务启动类。

当前父 POM 使用 Java 21、Spring Boot 3.5.16、Dubbo 3.3.0，并管理 Spring Cloud 2025.0.0 与 Spring Cloud Alibaba 2025.0.0.0 的依赖版本；引入 BOM 不等于已启用所有 Spring Cloud 功能。

### 基础设施地址

以下是本地开发示例，不是仓库提供的一键部署配置。

| 组件 | 本机访问地址或端口 | 用途 |
| --- | --- | --- |
| 前端 Vite | `http://localhost:5173` | 用户使用的页面 |
| Higress 控制台 | `http://localhost:8001` | 配置服务来源与路由 |
| Higress HTTP 入口 | `http://localhost:18080` | 转发前端 `/api` 请求 |
| Nacos 3 控制台 | `http://localhost:8080` | 注册中心管理页面 |
| Nacos 注册服务 | `127.0.0.1:8848` | Dubbo 注册连接；另需对应 gRPC 端口可达 |
| MySQL / Redis | `3306` / `6379` | 以实际连接配置为准 |

`18080` 来自示例容器映射 `-p 18080:8080`。Nacos 控制台的 `8080`、Higress 控制台的 `8001` 均不能作为前端 API 代理目标。Dubbo 的 `50051/50052/50053` 也不是 Spring MVC 的 HTTP 端口。

## 1. 准备数据库与本机配置

数据库初始化及已有数据库迁移见 [根目录 README](../README.md#快速启动)。当前 `user` 与 `app` 仍连接同一个 `appforge` 数据库，没有完成按服务独立分库。

为三个可运行模块分别创建本机文件：

- `user/src/main/resources/application-local.yml`
- `app/src/main/resources/application-local.yml`
- `screenshot/src/main/resources/application-local.yml`

这些文件已被根目录 `.gitignore` 忽略。下面命令统一显式启用 `local` profile；本机文件只覆盖连接、密钥等配置，其余设置仍继承模块的 `application.yaml`。

### user 与 app 的共享连接配置

将以下内容分别写入 `user` 与 `app` 的本机 YAML。两服务必须连接同一个 Redis 实例、逻辑库和 Session namespace，并保持 Session 序列化配置一致：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/appforge?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${APPFORGE_DB_USER:root}
    password: ${APPFORGE_DB_PASSWORD}
  data:
    redis:
      host: ${APPFORGE_REDIS_HOST:127.0.0.1}
      port: ${APPFORGE_REDIS_PORT:6379}
      database: 0
      password: ${APPFORGE_REDIS_PASSWORD:}
  session:
    redis:
      namespace: spring:session

dubbo:
  registry:
    address: nacos://${NACOS_HOST:127.0.0.1}:8848
    parameters:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD}
```

`screenshot` 不需要上述 MySQL/Session 配置，但同样需要覆盖 `dubbo.registry` 的地址与账号密码。Nacos 密码是你实际初始化的登录密码，不是节点 identity 或 token secret。

当前 `user` 与 `app` 均提供 `RedisSessionConfig`。更换序列化格式后，旧会话可能无法继续读取，联调时先退出并重新登录。

### app 的模型、COS 与封面配置

将以下内容继续追加到 `app` 的本机 YAML：

```yaml
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

cos:
  client:
    host: ${COS_HOST:https://your-bucket.cos.ap-shanghai.myqcloud.com}
    secretId: ${TENCENT_COS_SECRET_ID:local-disabled}
    secretKey: ${TENCENT_COS_SECRET_KEY:local-disabled}
    region: ${COS_REGION:ap-shanghai}
    bucket: ${COS_BUCKET:local-disabled}

app:
  cover:
    enabled: false
    base-url: http://127.0.0.1:8125/api
    public-base-url: http://localhost:18080/api

logging:
  level:
    dev.langchain4j: INFO
```

四个模型入口可以分别设置不同模型；上面为了本地调试统一引用一组变量，模型名称必须是当前账户实际可用的名称。

当前 `CosCoverObjectStore` 仍要求创建 COS 客户端，因此关闭封面不等于可以直接删除所有 COS 配置。`local-disabled` 仅用于不使用云存储的启动调试，不具备上传能力。启用真实上传或封面前，必须配置有效 COS 凭据、桶和域名；启用封面还需 Chrome、驱动和相关数据库表。微服务的封面回调地址应指向 `8125`，不要沿用单体的 `8123`。

`screenshot` 的本机 YAML 还需加入有效的 `cos.client` 配置；可以复用上面的属性模板。真实截图上传必须替换本地占位值。

仓库原始配置目前存在不同的 COS 环境变量名：

| 配置位置 | Secret ID / Secret Key |
| --- | --- |
| 单体、screenshot 默认 YAML | `TENCENT_COS_SECRET_ID` / `TENCENT_COS_SECRET_KEY` |
| app 默认 YAML | `COS_SECRET_ID` / `COS_SECRET_KEY` |
| 本文的本机覆盖模板 | 统一使用 `TENCENT_COS_SECRET_ID` / `TENCENT_COS_SECRET_KEY` |

只设置环境变量不会覆盖任意 YAML 字段，必须与实际配置中的占位符对应。不要在占位符默认值中写真实密钥。

### 设置运行环境变量

在启动对应服务的同一 PowerShell 终端中设置，示例值需要替换：

```powershell
$env:APPFORGE_DB_PASSWORD = "你的数据库密码"
$env:NACOS_PASSWORD = "你的 Nacos 登录密码"
$env:APPFORGE_MODEL_BASE_URL = "https://api.deepseek.com"
$env:APPFORGE_MODEL_API_KEY = "你的模型密钥"
$env:APPFORGE_MODEL_NAME = "你的账户实际可用的模型名称"
```

真实云上传还需设置 `TENCENT_COS_SECRET_ID`、`TENCENT_COS_SECRET_KEY`、`COS_HOST`、`COS_REGION`、`COS_BUCKET`。IDEA 启动则将相应变量填入运行配置。新开的终端不会继承另一个已打开终端中临时设置的变量。

## 2. 构建与启动服务

先启动 MySQL、Redis 和 Nacos；需要 Vue 工程生成时，还要确认 `docker info` 的 Server 正常。进入仓库根目录后，使用已安装的 Maven：

```powershell
cd appforge-microservices
mvn -v
mvn -DskipTests install
```

此聚合工程当前没有自己的 Maven Wrapper。`install` 将内部依赖装入本地 Maven 仓库，供各模块独立运行；`-DskipTests` 仅跳过测试，构建成功不能替代运行验收。

在不同终端分别执行，每个终端先配置对应环境变量。从仓库根目录开始：

用户服务：

```powershell
cd appforge-microservices/user
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

应用服务：

```powershell
cd appforge-microservices/app
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

截图服务：

```powershell
cd appforge-microservices/screenshot
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

截图服务用于独立截图能力联调；当前应用封面任务仍在 `app` 中执行，不会因为启动了 `screenshot` 就自动切换为远程调用。

也可以在 IDEA 导入父 `pom.xml`，使用表格中对应的启动类，设置 Active profiles 为 `local`。不要再同时从命令行启动相同服务。

打包后的服务 JAR 位于各模块 `target/` 下，例如：

```powershell
java -jar .\user\target\user-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

这条命令在 `appforge-microservices` 目录执行。其余服务同理，JAR 名以实际构建产物为准。运行时生成目录与工作目录有关；固定各服务的工作目录，并持久化对应 `tmp/`，切换运行方式前核对已有作品的文件位置。

## 3. 配置 Higress 的 HTTP 路由

容器内访问本机服务，推荐使用 Docker Desktop 的 `host.docker.internal`，或使用容器能够访问的宿主机局域网 IP。容器中的 `127.0.0.1` 指向容器自身。

Nacos 服务来源填写注册中心主机名/IP，访问端口为 `8848`，并按实际情况开启认证、填写 namespace 与 group。连接 Nacos 2.x 客户端协议时，还需确保 `9848` 可达；Nacos 3 与当前 Higress 版本的接口兼容性需结合发现日志确认。

**Dubbo 注册信息不等于已经存在可用的 HTTP 上游。** 当前服务的 HTTP 和 Triple 端口不同。最直接的 HTTP 联调方式是在 Higress 中创建固定地址服务：

| HTTP 上游名称示例 | 容器访问地址 | 目标端口 |
| --- | --- | --- |
| `appforge-user-http` | `host.docker.internal` | 8124 |
| `appforge-app-http` | `host.docker.internal` | 8125 |

使用 Nacos 自动发现时，同样需要确认发现到的实例地址和协议适合 HTTP 转发，不能把 Triple 的 `50051` 当成用户 REST 接口。

按当前控制器归属设置路由：

| 匹配方式 | 路径 | 上游 |
| --- | --- | --- |
| 精确 | `/api/user/password/reset-code` | app HTTP 8125 |
| 精确 | `/api/user/password/reset` | app HTTP 8125 |
| 前缀 | `/api/user` | user HTTP 8124 |
| 前缀 | `/api/app` | app HTTP 8125 |
| 前缀 | `/api/chatHistory` | app HTTP 8125 |
| 前缀 | `/api/file` | app HTTP 8125 |
| 前缀 | `/api/static` | app HTTP 8125 |
| 前缀 | `/api/deploy` | app HTTP 8125 |
| 精确 | `/api/health/` | app HTTP 8125 |

两条密码找回路由必须优先于通用 `/api/user` 路由。密码修改 `/api/user/password/update` 则仍在 user 服务中，不能将整个 `/api/user/password` 都转给 app。

各服务已有 `server.servlet.context-path: /api`，转发时保留 `/api` 前缀。截图服务当前主要提供内部 Dubbo 接口，不必为了登录和生成流程增加截图 REST 路由。内部封面渲染走 `app.cover.base-url` 直连应用服务，不需要公开转发内部渲染入口。

SSE 生成路由应关闭响应缓冲，并设置适合模型长响应的超时；如果启用网关认证插件，还需与项目已有登录机制协调。

## 4. 切换前端到网关

当前仓库的 `appforge-vue/vite.config.ts` 默认仍代理到单体 `8123`。使用微服务时，将其中的代理目标改为：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:18080',
      changeOrigin: true,
    },
  },
},
```

保留该文件已有的其他 Vite 配置。开发环境继续使用相对路径：

```dotenv
VITE_API_BASE_URL=/api
VITE_STATIC_BASE_URL=/api/static
VITE_DEPLOY_DOMAIN=/api/deploy
```

如需覆盖，将这些内容放入 `appforge-vue/.env.local`。不要直接复制指向 `8123` 的旧环境示例后继续使用微服务；绝对地址会绕过 Vite 代理。

然后在前端目录执行：

```powershell
npm ci
npm run dev
```

修改代理或环境文件后重启 Vite，打开终端显示的前端地址，通常为 `http://localhost:5173`。同一次登录统一使用同一个主机名。

## 5. 按层验证

先验证用户服务直连：

```powershell
curl.exe -i --noproxy "*" http://localhost:8124/api/user/get/login
```

再验证应用服务：

```powershell
curl.exe -i --noproxy "*" http://localhost:8125/api/health/
```

然后验证网关与 Vite 代理：

```powershell
curl.exe -i --noproxy "*" http://localhost:18080/api/user/get/login
curl.exe -i --noproxy "*" http://localhost:5173/api/user/get/login
```

未登录时，用户接口应返回项目的 JSON 错误响应，通常包含 `code: 40100`；健康接口应返回 `code: 0`、`data: "ok"`。业务响应体中的 code 与 HTTP 状态码是两件事。

| 现象 | 排查方向 |
| --- | --- |
| 直连服务也连接失败 | 服务没有启动成功、HTTP 端口不对或监听地址不对 |
| 直连有 JSON，网关 503 / `upstream connect error` | Higress 上游地址、端口、容器到宿主机连通性或防火墙 |
| 网关返回 404 | 路由路径、前缀重写、控制器实际归属 |
| 网关正常，Vite 代理异常 | `vite.config.ts`、前端环境覆盖与是否重启 Vite |
| 登录成功，app 请求仍未登录 | Redis 实例/逻辑库/namespace、Session 序列化和 Cookie |
| 注册和登录可用，找回密码 404 | 两条找回密码接口仍由 app 提供，检查精确路由 |
| 模型 401 或生成失败 | app 进程的模型 Key、名称、账户权限、余额与日志 |
| 生成 Vue 工程时 Docker 连接失败 | Docker Desktop Linux 引擎、目录挂载和构建镜像 |

最后在浏览器完成：注册或登录 → 创建应用 → 生成 → 继续修改 → 对比/恢复版本 → 下载源码 → 发布 → 公开/私有校验 → 下线。另行检查昵称修改、密码修改与管理员重置码找回流程。接口可达只证明路由的一部分，不能替代这些业务验收。

## 当前迁移边界

- `user`、`app` 和 `screenshot` 已有独立启动类、HTTP 端口与 Dubbo 配置；`ai` 仍由 `app` 在进程内调用。
- `client` 有内部用户、应用、截图接口，相关服务已提供 `@DubboService` 实现；应用侧仍保留本地 `UserService`/Mapper，并非所有用户读取都经远程接口。
- `app` 的封面任务仍使用本地 `CoverRenderer`；独立截图服务尚未接替这条任务链。
- 当前共享数据库与 Redis Session；未完成独立数据所有权、跨服务一致性和多实例任务调度。
- Higress 路由需在部署环境配置，仓库没有提供与这些说明对应的一键平台编排。
- [现有 CI](../.github/workflows/vue-runtime-verification.yml) 主要验证单体与前端，尚未提供微服务聚合构建和网关全链路验收结果。
- 生产化前仍需完成密钥外置、真实环境验收、持久化备份、资源配额与跨节点任务控制。

完整产品能力、界面预览和功能文档见 [根目录 README](../README.md)。
