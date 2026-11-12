# AI 成本与流量优化

本文说明 AI 响应缓存、路由缓存和 SSE/API 瘦身的实现约束。改造不新增数据库表，依赖现有 Redis。

## AI 响应缓存

缓存服务位于 `appforge-backend/src/main/java/com/yuelei/appforge/service/AiResponseCacheService.java`。

### 智能路由缓存

- 应用创建前先按初始 prompt 查缓存，命中后不再调用路由模型。
- key：`appforge:ai:route:v1:<sha256(normalize(prompt))>`。
- 索引：`appforge:ai:route:index`，保留最近 200 条候选。
- 有效期：7 天。
- 相似度阈值：0.88。路由只决定 HTML / Vue / 多文件类型，允许一定模糊匹配。

### 生成结果缓存

- 仅缓存“从零创建、且消息等于应用初始 prompt”的完整结果；修改模式、带附件、带选中元素的请求不会命中。
- key：`appforge:ai:result:v1:<codeGenType>:<sha256(normalize(prompt))>`。
- 索引：`appforge:ai:result:index:<codeGenType>`，每个类型最近 200 条。
- 有效期：24 小时；单条结果上限 2 MB，超过则不缓存。
- 相似度阈值：0.97。源码会直接成为应用版本，因此只接受几乎一致的 prompt。
- 命中时直接生成一个新版本并返回短提示，不再请求模型；前端通过版本文件接口加载代码。

### 降级

Redis 读写异常只记录 debug 日志，继续调用真实模型；缓存写入失败不能影响已经完成的生成。

## SSE 瘦身

`progress` 和 `file` 事件改用短字段，前端同时兼容旧字段：

| 事件 | 旧字段 | 新字段 |
| --- | --- | --- |
| progress | `id/operation/path/state/message` | `i/o/p/s/m` |
| file | `path/operation/content/truncated` | `p/o/c/t` |
| done | `version` | `v` |
| generation-error | `code/message` | `c/m` |

`file` 的 `o` 使用 `C/U/D`，`c` 仅在内容存在时发送，`t` 仅在截断时发送。

## API 瘦身与压缩

- `AppVO` 使用 `@JsonInclude(NON_NULL)`，省略空字段。
- `server.compression` 开启 gzip，最小 1024 字节，覆盖 JSON/HTML/CSS/JS 等静态和接口响应。
- **SSE 不使用 gzip**：`text/event-stream` 未加入压缩 MIME，避免代理或容器缓冲破坏逐字流式效果。

## 验证

```powershell
cd appforge-backend
mvn -q "-Dtest=AiResponseCacheServiceTest,AppWorkspaceTest" test
```

前端：

```powershell
cd appforge-vue
npm run type-check
npm run lint
npm run build-only
```

`AiResponseCacheServiceTest` 覆盖 normalize、相似度、Redis 精确键往返；`AppWorkspaceTest.createGenerationReusesCachedFilesWithoutCallingModel` 覆盖缓存命中不调用模型。

## 已知边界

- 缓存按 prompt 文本共享，不区分用户；只对无附件、无选中元素的创建请求生效。
- 相似度使用字符二元组 Jaccard，中文短句效果较好，但不做语义理解。
- 路由缓存 7 天，结果缓存 24 小时；需求变更后旧缓存可能仍被近似命中，可通过修改 key 版本前缀主动失效。
- 当前未对 SSE 正文分块做二次压缩，流量收益主要来自短字段和普通 API 的 gzip。
