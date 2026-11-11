# 自动封面：启用与验证

生成、恢复或部署成功版本时，平台将截图任务写入同一数据库事务。一个有界工作队列串行打开 Chrome，截图固定的版本快照，再上传私有 COS 对象。失败保留原封面；没有封面时继续显示渐变占位。部署接口仍返回 deployKey，模型配置与生成类型不变。

## 已有项目升级

1. 备份数据库，在 AppForge 数据库中执行 **一次** `sql/migrations/20261110_app_cover.sql`。新建数据库使用最新 `sql/create_table.sql`，不要重复执行迁移。
2. 后端设置 COS 环境变量。现有 `application.yml` 已读取 `COS_SECRET_ID`、`COS_SECRET_KEY` 等变量，真实密钥只放本机或部署平台，不能提交 Git。
3. 确认 Chrome 已安装。默认 WebDriverManager 下载匹配的驱动；离线环境可设置 `APP_COVER_DRIVER_PATH` 和 `APP_COVER_CHROME_BINARY`。
4. 在启动后端的 PowerShell 或 IDE 运行配置中加入以下变量，然后重启后端。变量只对从该终端启动的进程生效；IDE 启动请写 IDE 环境配置。

```powershell
$env:APP_COVER_ENABLED = "true"
$env:APP_COVER_BASE_URL = "http://127.0.0.1:8123/api"
$env:APP_COVER_PUBLIC_BASE_URL = "http://localhost:8123/api"
```

`BASE_URL` 是截图工作进程访问后端的地址，不能填前端 5173。`PUBLIC_BASE_URL` 是用户浏览器访问 API 的地址，应与前端 `VITE_API_BASE_URL` 对齐，避免 localhost 和 127.0.0.1 的登录 Cookie 不一致。若后端使用 6389，两项端口都改为 6389。容器内 localhost 指当前容器，请按实际后端地址设置。

默认 `APP_COVER_ENABLED=false`，不访问新任务表。启用却未迁移时，启动会给出迁移文件提示。旧版 `app.screenshot.base-url` 不再控制新任务。

## COS 权限

复用现有 `cos.client` 配置；示例桶为 `appforge-1317627961`，地域 `ap-shanghai`，请以实际控制台为准。为服务账号授予该桶 `app-covers/` 前缀的对象上传、读取、删除和设置对象 ACL 权限。上传显式使用私有 ACL；桶策略也不能额外允许匿名读取这一前缀。无需配置 CDN 或公开整个桶。

自动封面保存对象键，列表和详情响应生成 `/app/cover/image?appId=...&revision=...` 地址。每次读取重新校验应用权限：私有作品仅本人和管理员可读，公开作品按既有可见性规则读取。响应 `Cache-Control: no-store`。部署分享链接的既有访问规则不变。

## 使用与故障处理

- 成功生成、恢复版本或部署后自动排队，不阻塞生成流或部署请求。应用详情新增封面状态、重新生成、自定义链接和切回自动截图入口。
- **迁移保留已有非空 cover 为 MANUAL**。以前误截的 404/错误页不会自动覆盖：打开该应用的“应用详情”，点击“使用自动截图”。没有成功版本时先生成。
- 同版本自动事件复用任务或成功图片；人工重新生成使用请求幂等标识，单应用间隔至少 30 秒、单用户每分钟最多 5 次。
- 最多尝试 3 次，临时失败后等待 30 秒、120 秒；永久 COS 权限错误直接失败。COS 403 暂停新任务，避免持续错误调用。修正实际运行进程的密钥/权限后重启，再重新生成失败任务。管理员也可调用 `POST /api/app/cover/admin/resume` 恢复队列；恢复队列不会自动修改终态失败任务。
- 前端每 3 秒查询进行中的任务，页面隐藏时暂停，离开时取消，最长 2 分钟；超过时间可点击“刷新封面状态”，后台仍继续运行。首页共享一个列表刷新定时器，图片加载失败只降级显示，不触发截图。
- 新版本或手工封面会取消旧目标。旧任务即使上传完成，也不能覆盖新封面；应用删除后不会被任务重新写回。

## 执行与资源

数据库任务是权威队列，内存队列默认容量 20，满时留在数据库等待；每个后端实例只有一个截图执行线程。执行租约默认 120 秒，每 15 秒续租，单次上限 90 秒。重启后扫描失效租约恢复任务。每次尝试拥有独立浏览器，按自身进程 ID 关闭，不能结束其他浏览器。

截图入口只接受有效任务的短期随机凭据；数据库仅保存其 SHA-256，完成或取消即失效。截图读取不可变数据库快照，与当前部署指向和用户登录状态无关。HTML、CSS、JS 使用现有私有资源内联器；Vue 使用该版本已经构建好的 dist。

页面运行在 `sandbox allow-scripts` 的 iframe 中，不赋予同源权限，禁止 fetch、表单、对象和任意 iframe。图片/字体仅允许 data URL 和 `app.cover.image-hosts` 配置中的明确 HTTPS 域名，默认 Unsplash、Pexels、Picsum。截图专用文档用内存 history 状态兼容 Vue Router 在 opaque origin 下的初始化，不修改保存的源码或预览页面。应用依赖额外外部域名时，管理员需审查后加入白名单；任意远程 API 不在本截图入口能力范围内。

桌面首屏固定 1600×900，等待页面挂载、字体和图片（图片最多等待 2.5 秒），压缩为质量 0.75 的 JPEG，上限 1 MiB。页面必须先通过 HTTP 200、任务标记和挂载检查，不能把连接失败页面当作成功截图。

上传前持久化候选对象清理意图；绑定成功后删除该意图，替换的旧对象延迟 24 小时清理。删除失败有数据库重试；删除应用也记录清理意图。终态任务保留至少 30 天，当前目标任务不清除。本地任务目录在 finally 中清理；每天北京时间 02:00 只清理指定目录下带平台标记、超过 24 小时的 UUID 子目录，不删除其他文件或符号链接。

当前适合单后端实例；数据库租约和条件发布可防止重复绑定，但没有声称具备跨实例统一浏览器并发配额。需要水平扩展时再将调度和截图工作节点分离。

## 验证

本次增加任务事务、并发认领、幂等/限频、版本/人工/删除隔离、租约恢复、有限重试、清理、HTTP 权限、队列容量以及前端状态测试。实际 Chrome 测试校验 JPEG 尺寸，并截图真正构建的 Vue Router 工程；CI 另启动专用 MySQL 8.4 验证真实迁移与行锁，并复用现有 Docker 工程构建回归。

```bash
cd appforge-backend
bash mvnw -Dtest=CoverTaskTest,CoverWorkerTest,CoverHttpTest test
# 安装 Chrome 后设置 APPFORGE_COVER_CHROME_TEST=true，运行 CoverChromeTest。
# APPFORGE_COVER_PROJECT_DIST 指向已构建的 dist 可验证实际 Vue 工程截图。
cd ../appforge-vue
npm test
npm run build
```

MySQL 测试仅在 `APPFORGE_COVER_MYSQL_TEST=true` 时运行，且强制限定 loopback 地址的专用 `appforge_cover_test` 数据库，会删除该库内测试表。不要填业务数据库。真实 COS 上传需要本机有效凭据，本次自动验证不会使用你的密钥或调用付费生成模型。

新增 Selenium/COS 的传递依赖可能改变 HTTP 客户端自动选择。因此流式模型显式使用 LangChain4j 官方 JDK HTTP 客户端，并保留真实 HTTP 取消测试，避免停止生成后底层连接仍占用。模型名称、token 上限、API 地址和凭据配置保持原样。
