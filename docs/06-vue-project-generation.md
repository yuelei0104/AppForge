# Vue 工程生成流程

## 与现有工作区的关系

工程模式的 `codeGenType` 为 `vue_project`，沿用应用鉴权、生成认领、历史记录、版本事务、回退、发布和清理流程。首页默认选择完整网站工程，轻量网站和单页网站仍走原有分支。无需新增数据库字段或修改 Session 配置。

`AppGenerationService` 为每次工程生成建立独立的 `ProjectTask`。`VueProjectRunner` 通过现有工厂建立调用级 AI Service，使用 LangChain4j 原生工具循环；工具实例携带本次目录，不能放入跨请求缓存。历史摘要继续经 `AiCodeGeneratorFacade` 获取，完整会话成功保存后再更新应用记忆。

## 教程中的构建与部署代码放在哪里

当前项目已接入这些能力，不需要将教程片段再次粘贴进处理器或发布方法：

| 教程中的步骤 | 当前实现位置 | 行为 |
| --- | --- | --- |
| 工具流结束后执行构建 | `workspace/vue/VueProjectRunner.stream` | `concatWith` 等待 `VueProjectBuilder.build(task)` 完成；构建在 boundedElastic 执行，完成之前不结束生成流 |
| 安装依赖、打包并收集 dist | `workspace/vue/VueProjectBuilder.build` | 在 Docker 中执行固定命令，校验并收集 `dist/` 产物 |
| 保存可预览的成功版本 | `workspace/AppGenerationService.finish`、`AppWorkspaceService.complete` | 将源码与产物统一提交为成功版本，随后才能保存成功历史并发送 SSE done |
| 点击部署 | `service/impl/AppServiceImpl.deployApp` → `AppWorkspaceService.deploy` | 校验并绑定当前成功版本，不重复构建，不移动或修改历史快照 |
| 预览和部署页面访问 dist | `controller/StaticResourceController.file` | Vue 工程仅提供 `dist/` 下的构建产物；预览读取当前版本，部署读取已发布版本 |

`core/handler/JsonMessageStreamHandler` 只负责将统一消息转换为正文与历史内容，不注入构建器，也不在 `doOnComplete` 中启动脱离生成任务的构建。

`core/builder/VueProjectBuilder` 保留为教程本地构建示例，不注册 Spring Bean；平台唯一构建组件为 `workspace/vue/VueProjectBuilder`。这样避免两个同名 `@Component` 都注册成 `vueProjectBuilder`，也保持现有的停止、构建失败和版本回退行为。

## 创建模式确认

首页通过 `createAppWithMode` 提交显式的 `codeGenType`，随后读取新应用信息确认实际保存的类型。只有所选模式与返回类型一致才进入对话页并自动生成；不一致、缺少类型或确认失败均明确提示，不自动再次创建应用。对话页顶部显示应用的实际模式；单页和轻量网站提供“下载源码”，Vue 工程提供“下载工程”，尚无成功版本时按钮禁用。已有原生网页及其版本保持原类型。

创建和开始生成的后端日志记录 `appId`、`codeGenType`，便于核对本地请求和运行版本，不记录提示词或凭证。`AppCreationModeTest` 用真实 JSON 接口、服务及 MyBatis-Flex 的 INSERT/SELECT 往返验证三种显式模式，并保留未传模式时的兼容默认值。

## 文件契约

模型通过 `readDirectory`、`readFile`、`writeFile`、`modifyFile`、`deleteFile` 操作源码，不能执行命令。修改或删除已有文件前必须读取目录和文件，局部替换要求原文唯一匹配。每次生成只写入独立临时目录，拒绝绝对路径、目录穿越、符号链接、Windows 设备名、环境文件和额外构建配置。

源码最多 29 个文件、100000 个字符。依赖固定为 Vue 3.3.4、Vue Router 4.2.4、Vite 4.4.5、Vue 插件 4.2.3；平台规范化 `package.json` 和 `vite.config.js`，不允许额外依赖或生命周期脚本。配置使用相对 base、`@` 别名、hash 路由以及合并后的模块与样式。图片使用外链，或通过 `src/assets` 导入；本分支不生成 `public` 文件。

模型多轮输出共分配 60000 Token，为平台的简短开场和结束文字预留余量。单次响应最多 16384 Token；缺少模型用量时按 UTF-8 字节及工具开销保守计数。截断、预算耗尽和无文件工具写入均视为失败。工具循环最多 60 轮。

## 构建边界

`VueProjectBuilder` 需要后端运行环境具备 Docker CLI 和可用的 Docker daemon，默认镜像为 `node:20.19.0-bookworm-slim`，可通过 `appforge.vue-build.image` 配置。daemon 必须能够绑定后端临时目录；后端位于容器内时，挂载路径必须与 daemon 所见路径一致。

固定流水线先在隔离容器安装依赖，再在断网容器构建。安装禁用依赖脚本，构建入口由平台固定。容器只绑定任务目录，限制内存、CPU、进程数与权限，根文件系统只读。Docker 不可用时失败，不回退到宿主机执行模型代码。安装和构建分别有 180 秒、90 秒期限，任务还有整体期限；构建输出仅接受受限的文本资源、最多 50 个文件和 3 MB。

停止会阻止后续文件操作、终止构建进程并请求移除任务容器，失败或停止均保留上一成功版本。模型包装器在收到官方带上下文的分片回调时，将 StreamingHandle 交给任务；停止后取消该传输句柄，晚到的句柄也会立即取消。在 SDK 尚未提供句柄之前不能保证即时中断已发出的请求，后续工具和版本写入仍被阻止。原有轻量生成的 HTTP 连接取消行为保持不变。工作区仍采用现有单后端任务认领与重启恢复机制。

## 版本与访问

源码与 `dist/...` 构建产物作为同一版本快照保存。只有源码完整且构建成功才提交版本；回退使用该版本已有的构建产物，不重新构建。已部署版本继续独立于当前预览版本。

预览和发布只提供 `dist` 内容，新增嵌套 `assets` 路由沿用原有可见性、所有者、下线与删除检查。工程首页将模块脚本和样式嵌入为 data URL，兼容现有不授予同源权限的沙箱；Vite 将导入的资源内联，避免沙箱内再次请求受保护的资源。

`GET /app/source/download?appId=...` 仅允许所有者或管理员下载当前成功版本的源码 ZIP。单页和轻量网站导出实际保存的 HTML/CSS/JS，Vue 工程导出完整工程文件而不包含构建产物。原生源码导出时规范化本地资源引用，不改变历史版本；ZIP 路径仍受文件白名单限制。版本比较同样只返回源码，按真实嵌套路径列出文件。

## 原生资源与记忆缓存

轻量网站的首次流式回复如果包含完整 HTML、却缺少 CSS 或 JavaScript，可取消模型流会在同一次生成任务内逐个补齐缺失文件：先 CSS、后 JavaScript，每个文件只请求一次，每次独享配置的输出额度。JavaScript 请求携带已经补齐的 CSS 和原有 HTML、用户上下文，只采纳本次指定的缺失文件，不覆盖已有 HTML/CSS/JS；首次回复完整时不增加调用。补齐仍失败、模型截断或网络失败时不提交新版本。补齐沿用生成任务的停止、超时和输出长度限制，最多增加两次模型请求，可能增加耗时和模型用量。单个请求仍被截断时不接续半截代码，错误中明确标注生成网页、补齐 style.css 或补齐 script.js 阶段。单页与 Vue 工程流程不受影响。

原生解析器兼容代码围栏上的常见文件名，例如 `css style.css`。预览和源码导出会将同一网页的常见本地引用 `styles.css`、`/css/style.css`、`main.js` 等映射到实际保存的 `style.css`、`script.js`，必要时补上引用；外部 URL 不重写。新生成内容引用了本地文件却没有返回该文件时，不提交新版本。已有版本如果缺少文件内容，无法凭空恢复，仍需要重新生成；已有源码快照不会因此被改写。

缓存将一个应用的 AI Service 与 `MessageWindowChatMemory` 放在同一条目中，避免异步淘汰回调通过另一个 Map 删除新记忆。缓存命中、重新加载与并发淘汰均不再将服务和记忆拆开读取，Redis 中的历史和过期策略保持原有行为。

SSE `progress` 事件只包含 `id`、`operation`、`path`、`state`、`message`。前端按 ID 更新进度条目；原有 `done`、`generation-error`、`stopped` 事件继续负责收尾。工具正文与实际源码通过原有默认 SSE 消息的 `d` 字段返回，前端继续使用现有 Markdown 渲染。

## 工具调用流式输出

核心 LangChain4j/OpenAI 固定为 1.11.11，Spring/Reactor 模块为对应的 1.11.11-beta19，Community Redis 使用该系列实际发布的 1.11.0-beta19。使用官方 TokenStream 工具分片与执行前回调，不在项目中覆盖 dev.langchain4j 的同名类。

`TokenStreamFluxAdapter` 把 AI 正文、工具参数分片与执行结果封装为 `ai.model.message` 下的统一 JSON 消息。调用 ID 包含模型轮次和工具 index，避免多轮 index 复用或模型迟到/缺失 ID 导致重复提示；参数分片不作为完整 JSON 解析。执行结果读取任务中实际保存的内容，因此固定配置文件显示的是平台规范化后的源码。

`StreamHandlerExecutor` 对原生网页选择 `SimpleTextStreamHandler`，Vue 工程选择 `JsonMessageStreamHandler`。同一工具首次出现时实时显示“选择工具”；执行后按真实操作类型显示路径和代码块，读取、修改、删除和失败不会全部标成“写入”。源码中的 Markdown 围栏使用更长的外层围栏包裹。

处理器只转换流并收集持久化正文，不直接写数据库。AppServiceImpl 继续在版本成功保存后统一写一条 AI 历史；失败或停止只保存现有的失败/停止提示，不保存半成品工具代码。临时“选择工具”提示不入历史，正文和工具执行结果会在刷新后恢复。模型内部思考不作为正文输出。原生网页补齐、Docker 构建与版本事务保持原有流程。

## 验证范围

`VueProjectTest` 使用真实文件写入、LangChain4j 原生工具循环与确定性模型、H2 版本事务验证路径约束、文件预算、修改前读取、输出截断、停止、构建失败、回退、资源鉴权与源码下载。Docker 命令构造、拒绝宿主机回退和产物校验使用测试替身验证。

最初用受信任的 11 文件阅读馆工程，在 Node 18.20.8 下实际完成依赖安装、开发服务器启动和生产构建；浏览器验证嵌套部署路径、hash 刷新、搜索、沙箱模块加载，以及平台文件进度、下载入口和移动布局。

后续增加 `VueProjectRuntimeTest` 进行真实外部服务验证。真实 DeepSeek 模型已通过同一个 `VueProjectRunner` 和原生工具循环，逐个写入 12 个工程源码文件，并通过源码契约校验。审查后的完整输出保存在 `src/test/resources/vue-live-project.json`，没有模型凭证；Docker 测试读取的就是这份真实输出。

当前交互环境没有 Docker daemon，实际容器验证由 [Vue runtime verification](https://github.com/yuelei0104/AppForge/actions/workflows/vue-runtime-verification.yml) 在 GitHub 执行，调用生产 `VueProjectBuilder`，保留构建后的 `dist` 和测试报告。流水线只启用 `APPFORGE_DOCKER_TEST`，不会调用付费模型。真实模型测试只有显式启用 `APPFORGE_LIVE_MODEL_TEST` 才运行，关闭请求与响应日志；可由 `APPFORGE_LIVE_MODEL_API_KEY`、`APPFORGE_LIVE_MODEL_BASE_URL`、`APPFORGE_LIVE_MODEL_NAME` 提供独立测试配置。模型阶段与容器阶段分别运行，不等于完整在线应用、MySQL 和 Redis 的部署联调。
