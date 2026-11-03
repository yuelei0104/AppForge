# AppForge 后端工程

坐标：`com.yuelei:appforge-backend:0.1.0-SNAPSHOT`。当前为可独立运行的 Spring Boot 工程。

| 配置项 | 值 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Maven | Wrapper 固定为 3.9.9 |
| 打包类型 | `jar` |
| 启动类 | `com.yuelei.appforge.AppForgeApplication` |
| 源码位置 | `src/main/java` |
| 配置位置 | `src/main/resources/application.yml` |
| 端口 / 上下文路径 | `8123` / `/api` |

## 启动与打包

以下命令均从本目录执行：

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

也可以运行打包后的 JAR：

```powershell
java -jar .\target\appforge-backend-0.1.0-SNAPSHOT.jar
```

同一时间只使用一种启动方式。切换前停止已有进程，避免占用同一个端口。

## 验收接口

浏览器访问 http://localhost:8123/api/health ，或在另一个 PowerShell 执行：

```powershell
Invoke-RestMethod http://localhost:8123/api/health
```

预期 HTTP 200：

```json
{"status":"UP","service":"appforge-backend"}
```

该接口只确认 Web 进程能响应请求，不检查数据库、Redis 或模型服务。

## IDEA 运行配置

重新加载本目录的 POM 后，模块类路径应选择 `appforge-backend`，主类选择 `com.yuelei.appforge.AppForgeApplication`，JRE 选择 Java 21。旧 `appforge-app` 运行配置需要移除或改为上述配置。

`@SpringBootApplication` 扫描 `com.yuelei.appforge` 及其子包。后续业务先在此范围内按包组织；独立服务的 Maven 拆分在对应阶段再决定。
