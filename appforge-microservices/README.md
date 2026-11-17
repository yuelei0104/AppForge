# AppForge Microservices

单体 `appforge-backend` 的微服务拆分版本，单体代码保持不动。

## 模块

| 模块 | 作用 | 默认端口 |
|---|---|---|
| `common` | 通用返回、异常、COS、工具与基础配置 | - |
| `model` | 实体、VO/DTO、工作区模型、MyBatis Mapper 依赖 | - |
| `client` | 服务间内部接口 | - |
| `user` | 用户、登录、注册、资料 | 8124 |
| `ai` | LangChain4j 模型配置、AI Service、工具、监控 | - |
| `app` | 应用、对话、工作区、版本、发布、封面任务 | 8125 |
| `screenshot` | Selenium 截图与上传 | 8127 |

## 本地启动

前置：MySQL `appforge` 库、Redis `127.0.0.1:6379`。

```bash
cd appforge-microservices
mvn -DskipTests install

cd user && mvn spring-boot:run
cd ../app && mvn spring-boot:run
cd ../screenshot && mvn spring-boot:run
```

接口文档：

- user: `http://127.0.0.1:8124/api/doc.html`
- app: `http://127.0.0.1:8125/api/doc.html`
- screenshot: `http://127.0.0.1:8127/api/doc.html`

构建可执行 jar：

```bash
mvn -DskipTests package
```

## 当前拆分边界

- `user` 与 `app` 使用同一个 Redis 保存 Spring Session，登录态可跨服务读取；两边必须使用相同的 `RedisSessionConfig` 序列化器。
- `app` 目前保留了用户读取/密码恢复所需的本地 `UserService` 副本，尚未改为通过 `client` 远程调用用户服务。
- `app` 的封面任务仍使用本地 `CoverRenderer`；`screenshot` 已独立可启动，但尚未替换封面任务中的调用。
- `appforge-backend` 是单体基线，不应在微服务改造中修改。
