# 生成体验扩展设计

> 目标：在不重写现有「工作区 + 版本快照 + SSE 增量输出」主链路的前提下，把生成过程从“只能看结果”升级为“可控的工程协作”：创建与修改使用不同策略、代码生成过程可见、图片和文本可以作为需求附件。

## 0. 实施状态（2026-11-11）

本文档最初是设计方案，当前 P0/P1/P2 已落地，P3 的文本提取已接入，OCR 未启用。实际实现与设计稿的差异以本节为准：

| 能力 | 实现 |
| --- | --- |
| 创建/修改分离 | `GenerationMode` + `ProjectTask` 模式校验；`AppGenerationService.begin` 用 `ensureBaseline` 返回值判定 CREATE/EDIT |
| Vue prompt / AI Service | 保留一个 `VueProjectAiService`，拆成 `create(...)` / `edit(...)` 两个方法，分别绑定 `codegen-vue-create-system-prompt.txt` / `codegen-vue-edit-system-prompt.txt` |
| 工具集 | `ToolManager.getTools(task, mode)`：CREATE 只给 `readDirectory` + `writeFile`，EDIT 给五个工具 |
| SSE | 新增 `file` 事件（path/operation/content/truncated），`done` 返回 `{"version":N}`；`progress` 仍不携带源码 |
| 前端代码视图 | `CodeWorkspace.vue`，版本文件为最终权威；实时事件合并、删除文件和截断文件单独处理 |
| 附件 | `app_attachment` 表；`POST /api/file/upload`、`GET /api/file/content/{id}`；图片/文本/Office/PDF 限制见 `AttachmentServiceImpl` |
| 聊天请求 | `POST /api/app/chat/request` 换取一次性 ticket，`GET /api/app/chat/gen/code?requestId=` 消费；旧 `appId&message` 保留 |
| 文本提取 | `DocumentTextExtractor` 使用 Tika 3.2.3；图片第一版只作为素材 URL，不做 OCR |
| 历史附件 | `ChatHistory` 增加不落库的 `attachments`，查询时批量回填，Markdown 导出附带链接 |

尚未实现：孤儿附件定时清理、附件删除接口、图片 OCR、原生 HTML/MULTI_FILE 的创建/修改严格分离。

## 1. 现状与约束

| 环节 | 当前实现 | 扩展时的约束 |
| --- | --- | --- |
| 生成入口 | `AppGenerationService.generate(...)` → `ProjectTask` / `CancellableCodeStream` | 不改生成主流程和版本保存规则 |
| Vue 生成 | `VueProjectRunner` + `VueProjectAiService`，固定使用一份 `codegen-vue-project-system-prompt.txt` | 创建和修改需要拆 prompt、拆工具集 |
| 工具 | `ToolManager` 注册 `FileWrite/Read/Modify/Delete/DirRead` 五个工具 | 创建阶段不应暴露修改、删除工具 |
| 工作区 | `ProjectTask` 以当前版本源码初始化临时目录，限制 29 个文件、100000 字符 | 修改前必须读目录、读文件；创建阶段不能误改旧文件 |
| 进度 | SSE `progress` 只含 `id/operation/path/state/message` | 不把源码塞进 `progress`，新增独立事件 |
| 代码获取 | 生成完成后的版本文件接口 `/app/version/files` | 实时预览复用该接口做最终校正 |
| 输入 | 纯文本 textarea，最多 2000 字；聊天接口是 GET query | 附件和结构化元素信息需要请求体 |
| 文件存储 | 只有封面截图使用 `CosManager` | 需要通用上传、鉴权、类型校验和孤儿清理 |

设计原则：

1. **服务端判定模式**，不信任前端传入的 `mode`。
2. **源码只在需要时传输**：`progress` 轻量，代码走 `file` 事件或版本文件接口。
3. **附件先上传、再引用**：消息里只传附件 ID，不直接信任客户端 URL。
4. **能力分级**：先做图片/文本“作为素材”，不承诺模型能识别图片文字。

## 2. 总体架构

```text
ChatComposer
  ├─ 选择/粘贴/拖拽文件
  ├─ POST /api/file/upload            -> attachmentId + url
  └─ POST /api/app/chat/request       -> requestId
                     │
                     ▼
        GET /api/app/chat/gen/code?requestId=...   (SSE)
                     │
        AppGenerationService
          ├─ 解析 requestId + 附件 + 元素上下文
          ├─ GenerationModeResolver：CREATE / EDIT
          ├─ PromptAssembler：用户文本 + 附件 + 选中元素
          ├─ AiServiceFactory：创建服务 / 修改服务
          └─ ProjectTask：按模式限制工具与写操作
                     │
        SSE: progress / file / done / generation-error
                     │
        AppChatPage
          ├─ ChatMessageList（含附件缩略图）
          ├─ GenerationProgress（原有）
          └─ CodeWorkspace + PreviewPanel
```

## 3. 创建与修改分离

### 3.1 模式判定

在 `AppGenerationService.begin(...)` 完成 `workspace.ensureBaseline(id)` 之后判定。`ensureBaseline` 已经返回基线版本号（0 表示没有可用旧代码）：

```java
int baseline = workspace.ensureBaseline(id);
GenerationMode mode = baseline > 0
        ? GenerationMode.EDIT
        : GenerationMode.CREATE;
```

不要用 `app.initPrompt != null` 或前端参数判断。原因：

- 首次生成可能失败，应用仍没有版本，下一次仍应是 `CREATE`。
- 历史应用可能通过 `ensureBaseline` 导入旧代码，导入后有版本，应走 `EDIT`。
- 用户删除文件不等于清空版本，仍应走 `EDIT`，由工具处理删除。

把这个结果放进一个上下文对象，后续 Runner、Prompt、Task 都使用它：

```java
public record GenerationContext(
        App app,
        String userMessage,
        GenerationMode mode,
        List<Attachment> attachments,
        ElementContext element
) {}
```

### 3.2 Prompt 拆分

把现有 `codegen-vue-project-system-prompt.txt` 拆成两份，保留公共约束的复制或通过模板拼接：

| 文件 | 内容重点 | 禁止内容 |
| --- | --- | --- |
| `prompt/codegen-vue-create-system-prompt.txt` | 从空目录规划页面、组件、路由、样式；先列计划，再逐个写文件；写完后简述结构 | 不要求读旧文件，不出现“局部替换”“保留原有代码”等修改规则 |
| `prompt/codegen-vue-edit-system-prompt.txt` | 先读目录、再读目标文件；优先 `modifyFile` 局部替换；保留无关代码；选中元素优先 | 不从零重写整个项目；不删除未要求的文件 |

建议两份 prompt 都保留一段统一的“工程与预算约束”，避免复制后漂移：

```text
- 工程最多 29 个源码文件，源码总量不超过 100000 字符。
- 必须保留 package.json、vite.config.js 和入口文件。
- 工具调用失败时先读文件确认，不要连续重复同一个失败调用。
```

### 3.3 AI Service 与工具集

当前 `VueProjectAiService` 只有一个 `generate` 方法，所有工具一次性注入。建议拆成两个接口和两个工厂方法：

```java
public interface VueProjectCreateAiService {
    @SystemMessage(fromResource = "prompt/codegen-vue-create-system-prompt.txt")
    TokenStream generate(@UserMessage String request);
}

public interface VueProjectEditAiService {
    @SystemMessage(fromResource = "prompt/codegen-vue-edit-system-prompt.txt")
    TokenStream generate(@UserMessage String request);
}
```

```java
public VueProjectCreateAiService vueCreateService(Object[] tools, StreamingChatModel model) {
    return AiServices.builder(VueProjectCreateAiService.class)
            .streamingChatModel(model)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(80))
            .tools(tools)
            .maxSequentialToolsInvocations(60)
            .build();
}

public VueProjectEditAiService vueEditService(Object[] tools, StreamingChatModel model) {
    // 同上，只是返回 Edit 接口
}
```

工具集按模式裁剪：

```java
Object[] tools = switch (mode) {
    case CREATE -> toolManager.getCreateTools(task); // DirRead + Write
    case EDIT   -> toolManager.getAllTools(task);    // 五个工具
};
```

`ToolManager` 新增：

```java
public Object[] getCreateTools(ProjectTask task) {
    return new Object[]{ dirReadTool.bind(task), writeTool.bind(task) };
}
```

注意：这只是“减少误用”，不能作为安全边界。真正的边界仍在 `ProjectTask`。

### 3.4 ProjectTask 的模式约束

给 `ProjectTask` 增加 `TaskMode`，并在写操作上落实：

```java
public enum TaskMode { CREATE, EDIT }
```

| 操作 | CREATE | EDIT |
| --- | --- | --- |
| `directory()` | 允许（返回空目录/固定文件） | 允许，必须先调用 |
| `read(name)` | 允许但通常不需要 | 允许；修改已有文件前必须调用 |
| `write(name, content)` | 只允许新建；不允许覆盖已有文件 | 允许新建；覆盖已有文件前必须已读 |
| `modify(name, before, after)` | 直接抛错 | 允许，保留 `editing()` 检查 |
| `delete(name)` | 直接抛错 | 允许，保留入口文件保护 |
| `seal()` | 至少写入一个文件 | 至少产生一次变更 |

实现上，`write` 在 EDIT 模式下如果目标文件已存在，要调用现有的 `editing(name)`；CREATE 模式下如果目标已存在则抛“创建阶段不允许覆盖已有文件”。这样即使模型误调用了工具，也会得到明确错误，而不是悄悄破坏基线。

### 3.5 减少修改阶段的上下文

当前 Vue 修改已经不需要把全量源码塞进 prompt：`ProjectTask` 的临时目录就是当前版本，模型通过工具按需读取。需要保持这一点，并明确：

- `contextForProject` 只注入：应用最初目标、最近对话摘要、本轮用户需求、选中元素信息。
- 不把 `workspace.previewFiles(...)` 的全部内容拼进 prompt。
- 编辑 prompt 明确要求“当前版本文件以工具读取结果为准，历史消息中的代码可能过期”。

原生 HTML / MULTI_FILE 目前没有文件工具，仍需要把当前代码拼入上下文。建议 P0 只对 Vue 做严格模式拆分；原生模式至少在 prompt 中增加“已有代码/首次创建”的分支，后续再统一。

## 4. 代码生成过程可见

### 4.1 新增 SSE `file` 事件

保持 `progress` 不变，新增：

```text
event: file
data: {"path":"src/App.vue","operation":"UPDATE","content":"<完整新内容>","truncated":false}
```

```java
public record FileChangeEvent(
        String path,
        FileOperation operation, // CREATE / UPDATE / DELETE
        String content,          // DELETE 时为 null
        boolean truncated
) {}
```

产生时机：在 `ProjectTask.write/modify/delete` 成功落盘之后，由 Task 统一发出，而不是让每个 Tool 自己拼事件。这样能保证：

- `modifyFile` 发出的是替换后的完整文件，不是 before/after 片段。
- 删除事件不会带旧内容。
- 工具失败时不发 `file`，只发失败 `progress`。

`ProjectTask` 增加一个可选回调：

```java
public ProjectTask(..., Consumer<FileChangeEvent> onFileChange) {}
```

`AppGenerationService` 把它接到 SSE 的 `onFile` 回调；`AppController` 新增 `emitter.send(SseEmitter.event().name("file").data(event))`。

### 4.2 大小与可靠性

- 单文件事件上限建议 128 KB；超过则 `truncated=true`、`content=null`，前端在 `done` 后通过版本文件接口补全。
- `file` 事件只用于“显示过程”，**版本文件接口是最终权威**。
- `done` 事件增加版本号，前端据此请求 `/app/version/files?appId=...&version=...`：

```text
event: done
data: {"version":3}
```

- 断线重连不保证补发 `file`；重新打开页面时直接加载当前版本文件。生成中的半成品不持久化，避免把未完成代码当成版本。

### 4.3 前端代码视图

新增组件，避免继续膨胀 `AppChatPage.vue`：

```text
AppChatPage.vue
  ├─ ChatMessageList.vue
  ├─ ChatComposer.vue
  ├─ GenerationProgress.vue（保留）
  ├─ CodeWorkspace.vue
  │    ├─ FileTree.vue
  │    └─ CodeViewer.vue（highlight.js，已有依赖）
  └─ PreviewPanel.vue
```

交互：

1. 右侧顶部使用 `预览 / 代码` 两个 Tab。
2. 代码 Tab 左侧文件树，右侧当前文件；顶部文件 Tab 可保留最近打开的文件。
3. 收到第一个 `file` 事件时，如果用户没有手动切换过，自动打开代码 Tab 并定位到该文件。
4. 用户手动切回预览后，不再强制跳转，只在代码 Tab 上显示“有新变更”标记。
5. `done` 后按版本号拉取完整文件，覆盖事件累积的内容；避免事件截断或丢失导致代码不完整。
6. 删除文件时从文件树移除；如果当前文件被删，自动切到相邻文件。

`CodeViewer` 使用已有的 `highlight.js`，加行号和复制按钮即可；第一版不引入 Monaco，减少包体和构建复杂度。`diff` 依赖可用于第二阶段显示“本次修改了哪些行”，不是 P1 的必需项。

安全：代码内容来自模型，展示时必须由 `highlight.js` 转义后再 `v-html`，不能直接插入原始 HTML；预览 iframe 的 sandbox 策略不变。

## 5. 多媒体与文本附件

### 5.1 能力边界

先明确“上传”解决什么问题：

| 附件类型 | 第一版能力 | 不承诺 |
| --- | --- | --- |
| 图片 | 作为素材 URL 写入生成代码；替换选中图片的 `src` | 不承诺模型能识别图片中的文字 |
| 文本文件 | 读取内容作为需求/资料拼入 prompt | 不保证超大文件完整进入上下文 |
| Word/PDF | 后续用 Tika 提取文本 | 第一版不支持 |
| 图片 OCR | 后续接 OCR 服务，结果可编辑 | 不在当前技术栈内直接实现 |

截图里模型说“无法直接读取图片内容”，这是符合当前文本模型的真实情况。设计上应把“图片作为素材”和“图片文字识别”拆成两个能力，避免做出无法兑现的交互。

### 5.2 上传接口

```http
POST /api/file/upload
Content-Type: multipart/form-data

file=<binary>
appId=<可选，聊天附件必传>
purpose=CHAT
```

返回：

```json
{
  "id": "478...",
  "kind": "IMAGE",
  "name": "cover.png",
  "mimeType": "image/png",
  "size": 182734,
  "url": "https://<cos-host>/appforge/uploads/.../uuid.png"
}
```

服务端校验：

- 必须登录；带 `appId` 时校验应用归属。
- 图片只允许 `image/png`、`image/jpeg`、`image/webp`、`image/gif`，最大 5 MB；用 `ImageIO.read` 或魔数校验，不能只看扩展名。
- 文本允许 `txt/md/json/csv/html/css/js/vue`，最大 256 KB；第一版由前端 `FileReader` 读取，后端只接收最终文本，减少二进制解析。
- 文件名清洗，COS key 使用 `appforge/uploads/{userId}/{yyyyMM}/{uuid}.{ext}`，不使用原文件名作为路径。
- 图片设置 `Content-Disposition: inline`、合理的 `Cache-Control`；不允许 SVG（XSS 风险）。
- 上传成功后写 `app_attachment`，未绑定消息的记录由定时任务清理（例如 24 小时）。

存储继续复用 `CosManager`，新增一个接收 `InputStream`、`contentType` 的方法；不要让业务代码直接操作 `COSClient`。

### 5.3 数据模型

新增表：

```sql
CREATE TABLE app_attachment (
  id BIGINT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  chat_history_id BIGINT NULL,
  kind VARCHAR(16) NOT NULL,          -- IMAGE / TEXT
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size BIGINT NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  url VARCHAR(1024) NOT NULL,
  extracted_text MEDIUMTEXT NULL,     -- 可选：OCR/Tika 结果
  sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  is_delete TINYINT NOT NULL DEFAULT 0,
  KEY idx_app (app_id),
  KEY idx_chat_history (chat_history_id)
);
```

不直接把附件数组塞进 `chat_history` 的 JSON 字段，原因：

- 附件需要独立做归属校验、清理和可能的 OCR 结果更新。
- 历史导出/摘要需要按消息批量查询，独立表更清晰。
- 现有 `ChatHistory` 结构改动最小，只在 VO 中增加 `attachments` 列表。

发送消息后，把 `chat_history_id` 回填到附件记录；`ChatHistoryService` 查询消息时按 `chat_history_id in (...)` 批量取附件，避免 N+1。

### 5.4 聊天请求：用 ticket 保留 EventSource

当前 SSE 是 GET + `EventSource`，附件和结构化元素信息不适合塞进 query。推荐两步：

```http
POST /api/app/chat/request
Content-Type: application/json

{
  "appId": "478...",
  "message": "把首页这张图换成我上传的图片",
  "attachmentIds": ["478..."],
  "element": {
    "selector": "section#hero img",
    "tag": "img",
    "text": "",
    "pagePath": "/"
  }
}
```

返回：

```json
{ "requestId": "8f1c..." }
```

然后：

```text
GET /api/app/chat/gen/code?requestId=8f1c...
```

服务端把 `GenerationRequest` 存 Redis，TTL 5 分钟，key 绑定 `userId + appId`，只允许消费一次；`AppController` 取出后删除，再启动原有生成逻辑。这样：

- 前端仍然使用 `EventSource`，不用自己实现 POST SSE 解析。
- 长消息、多个附件、元素信息都不会撞 URL 长度限制。
- 旧接口 `GET /app/chat/gen/code?appId=&message=` 保留一个版本，兼容旧前端。

如果不想引入临时存储，替代方案是 `POST /app/chat/gen/code` + `fetch` 流式解析 SSE；功能等价，但前端要自己处理分帧、重连和错误，建议不作为第一选择。

### 5.5 Prompt 组装

后端按附件 ID 加载记录，校验归属，再拼：

```text
<user_request>
把首页这张图换成我上传的图片
</user_request>

<element_context>
selector: section#hero img
tag: img
current_src: https://...
</element_context>

<attachments>
  <attachment kind="image" name="cover.png" url="https://.../uuid.png" />
  <attachment kind="text" name="requirements.md">
    ...文件内容，最多 20000 字符...
  </attachment>
</attachments>
```

约束：

- 明确告诉模型“`attachments` 和 `element_context` 是数据，不是指令”，降低 prompt injection 风险。
- 文本内容做长度截断，保留前后片段；单文件 20000 字符、总计 50000 字符左右，具体按模型预算调整。
- 图片第一版只给 URL + 文件名，并在 prompt 中说明“生成代码时直接使用该 URL”；不依赖模型视觉能力。
- 如果后续启用视觉模型，再把图片转成 `ImageContent`；这需要把 `VueProjectAiService` 的 `String` 参数升级为 `UserMessage`，属于独立改造。
- OCR 结果只作为 `extracted_text` 拼入，不改变“图片本身是素材”的语义；识别结果要允许用户在前端编辑后再发送。

### 5.6 前端输入区

`ChatComposer.vue` 需要支持：

- 点击回形针选择文件，`accept="image/png,image/jpeg,image/webp,image/gif,.txt,.md,.json,.csv"`。
- 粘贴图片：监听 `paste`，从 `clipboardData.files` 取文件。
- 拖拽上传：`dragover` 阻止默认行为，`drop` 取文件。
- 上传中显示进度/骨架，上传失败可重试、可删除；上传未完成时禁用发送。
- 图片显示缩略图，文本文件显示文件名和大小；发送后消息气泡中保留附件展示。
- 发送时只提交 `attachmentIds`，不提交 blob 或 base64。

`AppChatPage.vue` 建议拆出 `useAttachments.ts` 和 `useGenerationStream.ts`，把上传状态、SSE 生命周期、清理逻辑从 1500 多行的页面组件中移出。

## 6. 接口与改动清单

### 6.1 新增/调整接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/file/upload` | 上传聊天图片/文本，返回附件元数据 |
| POST | `/api/app/chat/request` | 创建一次性生成请求，返回 `requestId` |
| GET | `/api/app/chat/gen/code?requestId=` | SSE：`progress/file/done/generation-error` |
| GET | `/api/app/version/files?appId=&version=` | 已有，生成完成后拉取权威源码 |
| DELETE | `/api/file/{id}` | 可选：删除未发送附件 |

### 6.2 后端改动点

- `AppGenerationService`：解析 `GenerationRequest`，判定 `GenerationMode`，把 `onFile` 回调传给 `ProjectTask`。
- `GenerationMode` / `GenerationContext`：新增。
- `VueProjectRunner`：按模式选择 `vueCreateService` / `vueEditService` 和工具集。
- `AiCodeGeneratorServiceFactory`：新增两个构建方法；保留旧 `vueProjectService` 一个版本做兼容。
- `ToolManager`：新增 `getCreateTools`。
- `ProjectTask`：新增 `TaskMode`、`onFileChange`、写操作模式校验。
- `FileChangeEvent`：新增；在 `ProjectTask.write/modify/delete` 成功后发出。
- `FileController` / `FileService`：上传、校验、COS 写入、附件落库。
- `AttachmentService`：按 ID 批量加载、归属校验、绑定消息、清理孤儿。
- `ChatHistoryService`：消息 VO 增加附件列表；导出/摘要兼容无附件历史。
- `AppController`：新增 `/chat/request`，SSE 增加 `file` 事件；旧 GET 保留。

### 6.3 前端改动点

- `src/components/chat/ChatComposer.vue`：附件选择、粘贴、拖拽、上传状态。
- `src/components/chat/ChatMessageList.vue`：附件缩略图/文件卡片。
- `src/components/code/CodeWorkspace.vue`：文件树、文件 Tab、代码高亮。
- `src/components/code/CodeViewer.vue`：`highlight.js` + 行号 + 复制。
- `src/composables/useAttachments.ts`：上传、删除、重试。
- `src/composables/useGenerationStream.ts`：ticket、EventSource、`file` 事件、done 后拉版本文件。
- `src/pages/app/AppChatPage.vue`：编排上述组件，保留现有编辑模式、部署、下载、版本历史入口。

## 7. 实施顺序与验收

### P0：创建/修改分离（优先）

范围：`GenerationMode`、两份 Vue prompt、两个 AI Service、工具集裁剪、`ProjectTask` 模式校验。

验收：

- 新建应用首次生成只使用 DirRead/Write，不出现 modify/delete 调用。
- 已有版本的应用生成时至少读取一次目标文件，修改不要求全量重写。
- 创建阶段覆盖已有文件会得到明确错误，不会静默破坏基线。
- 现有 Vue 生成、版本保存、预览、部署链路不回归。

### P1：代码视图

范围：`file` 事件、`CodeWorkspace`、`CodeViewer`、done 后版本文件校正。

验收：

- 生成过程中能按文件看到新增/修改内容，事件乱序或丢失后最终代码仍完整。
- 大文件触发截断时前端不白屏，完成后能拉取完整内容。
- 代码展示不执行生成代码，预览 iframe 的 sandbox 不变。

### P2：图片/文本附件

范围：上传接口、附件表、ticket 请求、Composer 附件 UI、prompt 组装。

验收：

- 图片上传后生成代码包含可访问的 COS URL；替换选中图片时使用新 URL。
- 文本附件内容进入 prompt，超长时有明确截断提示。
- 非本人应用、非法 MIME、超大文件被拒绝；未绑定附件会被清理。
- 旧聊天记录（无附件）仍能打开、导出、摘要。

### P3：文档提取/OCR（可选）

- Word/PDF 用 Tika 提取文本，结果可预览、可编辑。
- 图片 OCR 接云服务，识别结果作为 `extracted_text`，不改变图片素材本身。
- 明确失败降级：OCR 不可用时提示用户手动输入图片中的文字。

## 8. 风险与取舍

| 风险 | 处理 |
| --- | --- |
| 创建/修改 prompt 复制后漂移 | 公共约束抽成模板片段，单测校验关键约束存在 |
| 模型在创建阶段仍尝试 modify | 工具集裁剪 + `ProjectTask` 抛错 + prompt 明确禁止 |
| `file` 事件过大拖慢 SSE | 128 KB 上限、truncated、最终以版本文件为准 |
| 附件进入 prompt 后超出预算 | 单文件/总文本上限，优先保留用户需求和选中元素 |
| 图片被当作“可读文字” | 产品文案明确“图片作为素材”，OCR 单独开关 |
| ticket 被重复消费/越权 | Redis TTL、绑定 userId/appId、一次性消费 |
| 上传文件 XSS/SSRF | 禁止 SVG、MIME+魔数校验、只存 COS、不让模型主动抓取任意 URL |
| 多实例部署 | ticket 放 Redis；上传走 COS；不要用单机 Caffeine 存 ticket |

## 9. 待确认的产品决策

1. 第一版是否只对 Vue 工程做创建/修改分离，原生 HTML/MULTI 暂缓？
2. 代码视图默认自动切到“代码”还是保持“预览”，只在有变化时提示？
3. 图片是否需要视觉理解/OCR，还是第一版只作为素材 URL？
4. 附件保留多久，是否允许用户删除已发送附件？
5. ticket 方案是否可接受（需要 Redis）；还是坚持无状态 POST SSE？

## 10. 建议的下一步

先只做 **P0 + P1**：这两块不引入新存储、不改消息协议，能直接改善当前生成体验，也最容易验证。P2 涉及上传、表结构和请求协议，建议在 P0/P1 稳定后单独开一个迭代，避免一次改动同时触碰生成主链路和文件存储。
