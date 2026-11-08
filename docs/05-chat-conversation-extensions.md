# 对话轮数、Markdown 导出与智能记忆

## 升级与使用

已有数据库先执行 `sql/migrations/20261107_chat_memory_summary.sql`，再重启后端并更新前端。脚本只创建独立的 `chat_memory_summary` 表，不重建或覆盖 `chat_history`。库名不是 `appforge` 时先调整 `USE`。新环境的 `sql/create_table.sql` 已包含此表；既有环境应已完成对话历史和应用工作台迁移。

进入本人应用对话页，工具栏显示轮数，并提供“导出对话”和“智能记忆”。管理员查看应用时有相同历史权限；其他用户浏览公开作品时不请求或展示历史、轮数、摘要和导出入口。

- 轮数按未删除的 `messageType=user` 记录统计。一条已接受并保存的用户请求算一轮，包括随后失败或停止的请求；被拒绝的并发请求不写历史，不计轮数。管理员逻辑删除用户消息会减少可见轮数。统计从数据库计算，不在 `app` 维护冗余字段，已有历史也可直接统计。
- 导出 UTF-8 Markdown，包含应用名称、ID、导出时间、用户和 AI 原始消息及代码。服务查询完整历史，不依赖页面已加载的最近 10 条；按创建时间与 ID 升序分批读取，每批 20 条，排除逻辑删除记录。开始时确定最大消息 ID，不纳入之后新增的消息；导出期间管理员修改或删除以每批查询时的数据为准。空历史也返回有效 Markdown。
- 智能记忆展示已摘要消息数、摘要、更新时间和任务状态，支持手动整理与失败重试。生成中不接受手动整理。关闭弹窗不取消已经提交的后台任务。

## 与现有架构的关系

Controller 继续处理登录和响应；统计和导出位于 `ChatHistoryService`，复用创建者/管理员的访问校验。`ChatMemoryService` 单独负责摘要持久化、后台任务去重和上下文构造。

保留现有“每个应用一个 AI Service + Caffeine + Redis MessageWindowChatMemory”工厂。完整回复和版本都保存成功后，才从数据库最新 20 条重建该应用 Redis 窗口，并检查自动摘要。失败或停止的本轮不提交不完整输出或触发本轮自动摘要；用户请求与经过处理的失败说明仍按原流程保存，后续摘要可能包含这些说明。

`AiChatSummaryService` 使用现有 `ChatModel`，是独立、无会话记忆的调用。每个应用只总结自己的数据，摘要调用不写应用 Redis 窗口和名称生成会话。MySQL 保留完整原文，独立表保存覆盖消息 ID、累计覆盖数、摘要与修订号。

有摘要时，可取消生成流使用“历史摘要 + 最近尚未摘要的窗口 + 本轮要求和当前版本源码”。近期窗口最多 20 条，受序列化字符预算限制。查询限制单条文本读取长度；旧 AI 输出的 Markdown 代码块会省略，过长 AI 文本会截断，实际源码仍从工作区当前版本读取。摘要和窗口是有损上下文，不承诺模型逐字记住所有历史；原始历史始终可查、可导出。

尚无摘要时继续使用原 Redis 窗口。Redis 不可用而数据库有摘要时仍可使用摘要；摘要读取失败则退回原窗口，不阻断正常生成。

## 自动整理与配置

默认累计 20 条未覆盖消息后自动整理，至少保留最近 8 条原始消息。典型一问一答是两条消息、一轮对话。自动检查在完整回复持久化后进行；老应用可点“整理旧对话”主动启动。每次最多处理 100 条旧消息，超过输入预算时分批；成功后积压仍达到阈值会继续处理。

可在已有配置文件顶层添加，无需更改 Redis、Session 或模型配置：

```yaml
appforge:
  chat-memory:
    enabled: true
    trigger-messages: 20
    recent-messages: 8
    max-input-characters: 60000
    max-summary-characters: 6000
```

输入预算限制摘要请求 JSON 和生成时历史 JSON，不包含独立的本轮要求及当前源码。按 Java 字符长度计算，不是精确 Token 计量。摘要会额外调用现有模型，实际费用和节省量取决于模型和历史。近期消息支持 2–20 条，阈值须大于近期条数且不超过 200；输入 10000–200000 字，摘要 200–12000 字且小于输入预算的一半。单条旧用户消息超过预算时任务失败并保留历史，可调整配置或修正异常记录后重试，不跳过该条并谎报覆盖成功。

## 失败、并发与历史修改

任务后台执行，同一进程每个应用最多一个任务。25 秒超时、模型异常、格式不正确或结果过长时保留原始历史和上次摘要。模型计算阶段不写库，超时后迟到结果不会提交。下次成功对话或手动操作可重试。失败提示和运行标记仅保存在当前进程，重启后清除，已提交摘要仍在 MySQL。

管理员通过现有历史接口新增、修改、删除消息时，变更和摘要失效在同一数据库事务中提交。失效清空摘要并增加修订号，后续工作台生成从数据库重建近期上下文；旧任务以修订号比较更新，不能覆盖新状态。删除应用时，在既有删除事务中清理摘要；迟到任务不会复活已删应用摘要。

沿用单后端部署方式，不引入队列和分布式锁。多实例数据库比较更新仍可阻止旧结果覆盖，但任务去重和运行状态只在当前实例有效，不构成跨实例协调。

## 接口

均相对 `/api`，要求登录且为创建者或管理员：

| 方法 | 路径 | 响应 |
| --- | --- | --- |
| GET | `/chatHistory/app/{appId}/stats` | `BaseResponse<ChatStatisticsVO>`，轮数、总消息数、AI 消息数、最后时间 |
| GET | `/chatHistory/app/{appId}/export` | `text/markdown;charset=UTF-8` 附件，`Cache-Control: no-store` |
| GET | `/chatHistory/app/{appId}/memory` | `BaseResponse<ChatMemoryVO>`，摘要和任务状态 |
| POST | `/chatHistory/app/{appId}/memory/compress` | 提交后台任务，返回当前状态 |

ID 与 long 计数沿用现有 JSON 配置输出字符串，不把大整数 ID 转成 Number。导出错误沿用 JSON 业务响应，前端先检查响应类型，避免把登录或权限错误存成 Markdown。

## 验证

前端：`npm test`、`npm run build`，变更文件 ESLint。后端：

```powershell
cd appforge-backend
.\mvnw.cmd '-Dtest=ChatConversationExtensionsTest,ChatHistoryIntegrationTest,AppWorkspaceTest,AiCodeGeneratorFacadeMemoryTest,AppServiceAccessTest,AppMetadataTest,CancellableCodeStreamTest,CodeParserTest' test
```

使用 H2、模拟模型与 MockMvc，覆盖完整分页导出、权限、轮数、应用隔离、重复任务、历史修改使旧结果失效、失败重试、超时迟到结果丢弃及序列化预算。浏览器检查入口权限、下载、摘要轮询与手机布局。上线前仍需用本机 MySQL/Redis 和真实模型配置验收完整对话、导出、摘要与重启后的恢复。
