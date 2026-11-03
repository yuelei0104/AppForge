# 智构 AI · AppForge

智能应用生成与发布平台。从需求描述出发，逐步实现 AI 代码生成、在线预览、多轮修改和应用发布。

## 当前进度

当前为第 0 阶段：工程骨架。已建立仓库规范、后端 Maven 父工程、Maven Wrapper、前端预留目录与开发路线。

本阶段没有 Java 启动类、HTTP 接口、数据库连接、AI 调用或 Vue 页面。Maven 父工程的校验成功，表示工程描述正确，不表示已经启动 Web 服务。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `appforge-backend/` | Maven 父工程，后续统一管理 Java 子模块与依赖版本 |
| `appforge-web/` | 前端预留目录，后续独立初始化 Vue 工程 |
| `docs/` | 工程结构、服务边界规划和阶段验收说明 |
| `LICENSES/` | 随附第三方工具的许可文件 |

Git 仓库根目录是本文件所在的 `AppForge/`。根目录不再套一层同名 `AppForge/`，也不需要根目录 `pom.xml`。

## Windows 上打开工程

1. 将 ZIP 内唯一的 `AppForge` 文件夹解压到 `D:\`。
2. 确认本文件位置为 `D:\AppForge\README.md`，后端 POM 为 `D:\AppForge\appforge-backend\pom.xml`。
3. IDEA 通过“文件 → 打开”打开 `D:\AppForge`，用于浏览整个仓库。
4. 右键 `appforge-backend/pom.xml`，选择“添加为 Maven 项目”。如果只想打开 Maven 工程，也可以直接打开这个 POM。
5. 将 IDEA 项目 SDK、Maven Importer 和 Maven Runner 的 JDK 统一设为 21。

本次工程由文件直接构建，不需要再用 Spring Initializr 创建另一个项目。

## 本阶段验收

在 PowerShell 执行：

```powershell
cd D:\AppForge\appforge-backend
.\mvnw.cmd -v
.\mvnw.cmd validate
```

预期：版本信息中的 Java 为 21，随后出现 `BUILD SUCCESS`。Wrapper 固定使用 Maven 3.9.9；首次运行需要访问 Maven Central 下载 Maven，成功后复用本地缓存。

此阶段无需运行 `spring-boot:run`，无需启动 MySQL、Redis 或 Docker。父工程的 `packaging` 是 `pom`，后续具体服务子模块才会加入 Spring Boot 启动类。

## Git 管理

验收通过后，在总目录初始化 Git：

```powershell
cd D:\AppForge
git init
git branch -M main
git add .
git status
git commit -m "chore: initialize AppForge project structure"
```

确认文件列表后再提交。GitHub 仓库建议命名 `appforge-ai`；远程仓库创建和推送另行执行，本压缩包不含 `.git` 目录或远程配置。

## 后续工作

先阅读 [工程结构](docs/01-project-layout.md) 与 [开发路线](docs/02-roadmap.md)。下一次只添加首个可运行模块并验收，不同时接入全部服务。

第三方工具说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
