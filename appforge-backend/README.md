# 后端父工程

坐标：`com.yuelei:appforge-backend:0.1.0-SNAPSHOT`。

- Java 目标版本：21。
- Maven：通过当前目录的 Wrapper 固定为 3.9.9。
- `packaging`：`pom`。
- Java 基础包名约定：`com.yuelei.appforge`。
- 当前子模块数量：0，后续每添加一个子模块才更新 `<modules>`。

父工程负责统一管理模块、依赖版本与构建插件，不承担业务请求，也没有启动类。Spring Boot、AI 框架与微服务组件的具体版本，在创建对应模块时一起核对并锁定；当前没有引入它们。

PowerShell 校验：

```powershell
.\mvnw.cmd validate
```

后续编译 Java 子模块时使用 JDK 21。不要在父目录直接建立 `src/main/java`，实际代码属于子模块。
