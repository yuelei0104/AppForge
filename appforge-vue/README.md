# AppForge 前端

Vue 3 + TypeScript + Vite + Ant Design Vue。提供应用创建、流式对话、生成预览、部署和管理页面。

## 本地运行

```sh
npm install
npm run dev
```

默认后端为 `http://localhost:8123/api`，前端通常运行于 `http://localhost:5173`。

## 环境配置

将 `.env.example` 复制为 `.env.local`，按实际部署情况调整以下公开配置。修改配置后需要重启开发服务器，生产环境需要重新构建。

| 变量                   | 用途                     | 未配置时                    |
| ---------------------- | ------------------------ | --------------------------- |
| `VITE_API_BASE_URL`    | Axios 和 SSE 的 API 前缀 | `http://localhost:8123/api` |
| `VITE_DEPLOY_DOMAIN`   | 已发布作品的 URL 前缀    | `${API_BASE_URL}/deploy`    |
| `VITE_STATIC_BASE_URL` | 生成预览的 URL 前缀      | `${API_BASE_URL}/static`    |

当前后端路由为 `/api/deploy/{deployKey}/` 和 `/api/static/{appId}/`。部署和预览使用不同的目录与接口；预览地址不包含代码生成类型前缀。若使用独立发布域名，可以将 `VITE_DEPLOY_DOMAIN` 配置为 `https://apps.example.com`，并配置反向代理将 `/{deployKey}/` 转发到后端发布路由；前端配置本身不会创建反向代理。

首页卡片、编辑页和部署成功弹窗统一使用 `src/utils/appUrls.ts` 生成链接。部署弹窗从接口返回值提取部署标识，再应用配置的发布域名。

`VITE_*` 会进入前端构建产物，请勿配置模型 API 密钥或其他私密信息。`.env.local` 已被 Git 忽略。

## 展示组件

- `AppCard.vue`：我的作品和精选案例共用，展示封面、创建者、应用标题及查看入口。
- `UserInfo.vue`：用户头像、昵称及可选的应用标题。
- `AppDetailModal.vue`：应用信息与有权限用户的修改、删除操作。
- `DeploySuccessModal.vue`：成功状态、复制部署地址和访问入口。
- `MarkdownContent.vue`：AI 流式 Markdown，支持 HTML、CSS、JavaScript 等代码高亮；原始 HTML 不会执行，未知语言回退为转义后的代码文本。

## 检查与构建

```sh
npm run type-check
npm run build
```
