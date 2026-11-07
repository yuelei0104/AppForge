# 对话历史与管理页面

## 使用

- 本人应用或管理员进入对话页时，先查询最近 10 条历史。消息按创建时间、ID 升序展示；顶部点击“加载更多历史消息”查询更早记录，保留阅读位置。
- 只有本人应用、历史成功加载且为空、当前没有生成任务时，自动发送初始提示词。查询失败可重试，不会误触发模型请求。旧地址中的 `view` 查询参数会移除；卡片和编辑页不再生成该参数。
- 至少两条历史记录或已有版本时展示网站预览。对其他人的公开应用保留只读浏览；历史内容仅创建者或管理员可读。
- 管理员通过导航的“对话管理”或 `/admin/chatManage` 进入，支持按消息、应用 ID、用户 ID、类型查询，分页排序、查看完整消息和确认删除。

## 数据库

如果已创建 `chat_history` 表，无需为本次前端接入重建表。新环境先执行 `sql/migrations/20261107_chat_history.sql`；初始化脚本 `sql/create_table.sql` 也已包含此表。脚本使用 `appforge` 库，其他库名应先调整 `USE`。已有数据库的应用扩展字段仍按 `docs/03-app-workspace.md` 升级。

## 接口

- `GET /chatHistory/app/{appId}?pageSize=10`：最近历史，后端按 `createTime DESC, id DESC` 返回。
- 继续向前加载同时传 `lastCreateTime` 和 `lastId`，值为已加载页最旧记录的原始时间和字符串 ID；兼容旧客户端仅传时间。只有时间的旧客户端在同秒记录跨页时仍可能跳过消息，新前端使用复合游标避免此问题。
- `POST /chatHistory/admin/list/page/vo`：管理员条件分页查询；最多每页 50 条，排序字段限制为 `id`、`createTime`、`updateTime`。
- `DELETE /chatHistory/remove/{id}`：管理员逻辑删除，沿用已有接口的布尔响应。
- 生成器留下的 save/update/list/getInfo/page/remove 接口均要求管理员权限，避免公开访问任意应用历史。

## 与原有生成流程的兼容

对话记录仍通过现有工作区生成链路写入：任务认领后才保存用户消息，完整响应和版本保存完成后保存 AI 消息；失败或停止记录经过处理的说明。不再绕过版本快照和停止生成机制调用旧生成入口。

API 重新生成时应保留 ID 字段的字符串兼容性。当前提供 `src/api/chatHistoryController.ts` 和独立历史类型；`workspace.ts` 保留现有组件使用的兼容入口。大整数 ID 不应转换成 Number。

## 验证

前端 `npm test`、`npm run build`；后端测试包含 `ChatHistoryIntegrationTest`、`AppWorkspaceTest`、`AppServiceAccessTest`、`AppMetadataTest`、`CancellableCodeStreamTest`。测试使用 H2 和模拟模型，不需要访问真实模型或用户数据库。
