# 应用封面任务设计

本文为设计基线，现已按现有工作区架构实现。启用、迁移、限制和验证方式见 [封面启用与验证](08-app-cover.md)。

状态：待实现的技术设计。本文提交不启用截图队列，不执行数据库迁移，不修改现有权限或部署行为。

基于主分支 `f95ebc605ae83a293e6b40f6ca09d707d8e475c2` 核对，设计日期为 2026-11-10。

## 目标和第一版范围

在已有 HTML、MULTI_FILE、VUE_PROJECT 生成、版本快照、部署和 COS 上传基础上，增加可靠的后台封面任务。用户生成或部署网站时不等待截图；截图失败保留上一次可用封面，没有可用图片时显示现有渐变占位。

第一版使用现有 MySQL、Spring 调度器、一个有界截图执行器和 COS，不增加 Kafka、RabbitMQ 或独立截图服务。数据库是任务事实来源，内存队列只是调度加速。整个应用目前仍按单后端实例运行；截图任务的领取令牌为后续扩展预留保护，不能据此宣称整个平台已经支持多实例。

验收范围包括生成后截图、部署补图、手动重试、任务去重、失败补偿、权限保护、旧任务隔离、前端回退和资源清理。

## 已有行为及需要替换的位置

| 代码位置 | 当前行为 | 设计中的调整 |
| --- | --- | --- |
| `service/impl/AppServiceImpl.deployApp` | 部署后拼接 URL，调用虚拟线程截图，返回 deployKey | 部署返回约定保持不变；删除直接启动截图线程的路径，改为幂等创建任务 |
| `workspace/AppWorkspaceService.append/complete/rollback` | 完整产物保存为不可变版本，回退也创建新版本 | 在保存版本的同一事务中记录封面任务；提交后才允许执行 |
| `workspace/AppWorkspaceService.deploy` | 将 deployedVersion 指向已保存版本 | 同一事务检查该版本任务，必要时补建；不重复构建 Vue 工程 |
| `utils/WebScreenshotUtils` | 静态 WebDriver，synchronized 串行访问；已有 HTTP 探测及错误页识别 | 浏览器改为任务内创建、使用、关闭，移除静态共享实例；探测和错误页识别继续保留并补充验证 |
| `service/impl/ScreenshotServiceImpl` | 截图、压缩、上传和清理临时文件 | 拆出截图结果与上传结果，返回 objectKey、尺寸、字节数等结构化数据 |
| `components/AppCard.vue` | 无封面或图片加载失败时显示渐变占位 | 复用现有回退，增加任务状态提示，无需额外下载默认图片 |
| `workspace/AppWorkspaceService.remove/cleanFiles` | 持久化文件清理任务，删除失败后定时重试 | 同时取消封面任务并持久化 COS 清理意图，不在数据库事务内调用 COS |

当前主分支已允许凭 deployKey 访问部署页面，私有预览仍鉴权。本文不把部署分享规则改动混入封面扩展。截图走专门的指定版本渲染入口，不依赖分享链接，也不需要给截图浏览器登录管理员账号。

## 封面代表哪个版本

- 应用卡片代表最新成功保存的 `currentVersion`；线上作品仍由 `deployedVersion` 决定，与现有版本行为一致。
- 每个任务固定 appId、versionNo、封面修订号和截图规格。执行时不能读取“当时最新”的版本或动态部署指向。
- v1 截图期间产生 v2，v1 可以停止；即使完成，也不能回写 v2 的卡片封面。
- 回退后创建的新版本按普通新版本处理，原来的线上部署保持不变。
- 已有自定义封面优先。自动截图不得覆盖用户手工设置的链接；用户可显式切换到“自动截图”。
- 当前封面可以在新图准备期间继续展示，但 `coverVersion < currentVersion` 时标记为旧版本，不能声称是新版本截图。

## 数据设计

以下是候选表结构，评审后才生成一次性迁移脚本；不要把本文 SQL 当成已发布的迁移执行。

### app 增量字段

现有 `cover` 字段兼容保留；自动封面额外保存对象键，API 响应中的 cover 根据模式生成，不把永久公开 COS URL 当作私有封面存储方式。

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| coverMode | varchar(16) | AUTO / MANUAL；新建无自定义链接时为 AUTO |
| coverStatus | varchar(16) | NONE / PENDING / RUNNING / RETRY_WAIT / SUCCEEDED / FAILED |
| coverVersion | int | 当前已绑定的自动封面所属版本，默认 0 |
| coverRevision | bigint | 每次目标改变、强制重截或手动修改封面时递增，用于条件更新 |
| coverObjectKey | varchar(512), nullable | 自动封面 COS 对象键，不含签名和凭证 |
| coverTaskId | bigint, nullable | 当前期望完成的任务 |

迁移时已有非空 cover 按 MANUAL 保留，避免误删或自动覆盖历史链接。已有空封面设为 AUTO，成功版本由限速补偿扫描补图。已有错误页封面需要用户显式切换到 AUTO 或重新生成，不根据任意 URL 自动猜测它是否为系统图片。

### app_cover_task

```sql
CREATE TABLE app_cover_task (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  appId bigint NOT NULL,
  versionNo int NOT NULL,
  revision bigint NOT NULL,
  profile varchar(32) NOT NULL DEFAULT 'desktop-v1',
  requestId varchar(64) NULL COMMENT '手动请求幂等键；自动触发为空',
  triggerType varchar(16) NOT NULL COMMENT 'GENERATE/DEPLOY/RESTORE/MANUAL/REPAIR',
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  attempts int NOT NULL DEFAULT 0,
  nextRunTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  executionToken varchar(64) NULL,
  workerId varchar(128) NULL,
  leaseUntil datetime(3) NULL,
  renderTokenHash varchar(64) NULL,
  renderTokenExpiresAt datetime(3) NULL,
  objectKey varchar(512) NULL,
  errorCode varchar(64) NOT NULL DEFAULT '',
  errorMessage varchar(512) NOT NULL DEFAULT '',
  createTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updateTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cover_request(appId,versionNo,revision,profile),
  UNIQUE KEY uk_cover_manual(appId,requestId),
  KEY idx_cover_due(status,nextRunTime,id),
  KEY idx_cover_lease(status,leaseUntil),
  KEY idx_cover_app(appId,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

任务状态另包含 CANCELLED。调度采用至少一次执行，依靠令牌和条件更新实现至多一次有效绑定，不承诺恰好一次执行浏览器或 COS 请求。

### app_cover_cleanup

持久化记录 id、bucket、objectKey、notBefore、attempts、nextRunTime、lastError、createTime，并建立 `(bucket,objectKey)` 唯一键和到期扫描索引。

上传前先记录候选对象的延迟清理意图，再执行上传，避免上传成功后进程退出留下完全不可追踪的对象。绑定成功后移除候选清理意图；换图、删除应用或任务过期时追加清理记录。删除前始终检查当前应用和有效执行任务是否还引用该对象，不能仅凭记录存在就删除。

对象键示例为 `app-covers/{appId}/v{versionNo}/{revision}/{executionToken}.jpg`。每次执行使用独立键，防止租约过期后旧执行覆盖新执行上传的对象。系统只管理此前记录过的本桶对象，不从用户自定义 URL 解析并删除文件。

## 任务创建与执行

### 创建规则

1. 版本保存、回退或部署事务内先锁 app 行，确认应用未删除且存在完整版本。
2. MANUAL 模式自动触发直接跳过。AUTO 模式存在同版本、同规格的成功封面或有效任务时直接复用，不递增 revision。
3. 需要新任务时递增 coverRevision，插入任务并写入 coverTaskId、coverStatus；保留旧 coverObjectKey 和 coverVersion。
4. 提交事务后尝试唤醒调度。通知丢失由定时扫描恢复，不依赖内存事件保证持久性。
5. 截图执行、浏览器初始化和 COS 调用均不在 app 数据库事务内进行。

补偿建任务也遵守这条路径，不能只凭扫描到空 cover 就直接启动线程。没有版本、生成中无可用版本、软删除和 MANUAL 应用不补建。每批有数量上限，扫描带游标，不能每轮从头扫描同一批应用。

### 领取、隔离和回写

内存队列只存任务 ID，初始一个线程、等待容量 20。调度只提交空闲容量内的任务，本地集合去除重复 ID；拒绝入队不丢弃数据库任务，也不让 HTTP 请求线程执行截图。

真正开始工作时才原子领取：仅到期的 PENDING/RETRY_WAIT 任务可转为 RUNNING，更新 attempts、executionToken、workerId、leaseUntil。领取行数不是 1 时跳过。不要在入队时就启动租约，否则长队列会使尚未执行的任务过期。

所有任务更新、租约续期、错误处理和完成处理都附带 `id + executionToken + RUNNING` 条件。续期使用独立的轻量调度器，不能排在阻塞的截图线程后面。租约过期恢复时先撤销旧令牌；旧执行即使继续运行也没有回写权。

上传完成后的短事务锁定 app 和 task，重新校验未删除、AUTO、currentVersion、coverRevision、coverTaskId、executionToken 和租约，条件全部匹配才绑定新对象并标为成功。失败或过期结果记入清理队列，绝不覆盖用户新设置的封面。

应用被手动改为 MANUAL、再次强制截图、产生新版本或删除时，旧任务取消或失去回写资格。领取和完成都要校验，不只在任务创建时校验。

### 重试与截止时间

| 类型 | 策略 |
| --- | --- |
| 网络波动、COS 5xx、可恢复的浏览器进程错误 | 总计最多执行 3 次，间隔 30 秒、120 秒并加少量抖动 |
| HTTP 非 200、登录页、错误页、渲染标记不匹配 | 不发布图片；区分授权失效和内容错误，前者重建凭证后有限重试 |
| InvalidAccessKeyId、签名配置错误、无上传权限 | 本任务直接失败；暂停自动上传调度，修正配置后由管理员恢复，避免批量重试风暴 |
| 应用删除、版本失效、手工封面优先 | CANCELLED，不重试 |

每次尝试预算初值 90 秒；页面加载最多 30 秒，渲染检查 10 秒，上传 15 秒，其他时间用于浏览器启动、压缩和清理。租约 120 秒，每 15 秒续期。所有数值是待压测的配置初值，不是性能承诺。

不能只对 CompletableFuture 设置超时：Future 超时不代表 Selenium 和浏览器进程已经停止。监督器必须终止该任务拥有的浏览器/Driver 进程树并回收临时目录，确认隔离后才执行下一任务；不能执行按进程名杀掉用户所有 Chrome 的命令。

## 截图渲染入口与访问规则

新增仅服务截图的候选入口：`GET /api/internal/cover/render/{taskId}?token=...`。

- Worker 开始执行时创建至少 256 位随机 token，仅存 SHA-256 摘要；token 绑定 taskId、appId、versionNo 和本次 executionToken，初始有效期 120 秒，结束立即失效。
- 入口校验 token、有效租约、应用未删除、任务仍为当前有效目标，再从数据库读取固定版本；不接受客户端提交任意 URL、文件路径或源代码。
- 普通无凭证请求返回 404；凭证不能用于平台 API、源码下载、其他应用或其他版本。入口必须同时限定可达网络范围，不能把 internal 路径名视作授权。
- Vue 只渲染 dist 运行产物，原生网站和 Vue 都复用现有渲染逻辑内嵌本地资源，避免凭证传播到资产请求；不会再次执行构建。
- 响应带 sandbox、no-store、Referrer-Policy: no-referrer。应用外部资源不能收到截图 token，访问日志和错误日志也不能输出完整带 token URL。
- 继续使用当前部署分享规则；私有预览仍由 AppPolicy 校验，不为截图增加管理员会话或放宽其他接口权限。

Worker 只访问固定配置的内部地址，禁止 API 调用者传任意目标，禁止重定向到其他主机。浏览器渲染生成代码时还需要网络隔离：仅允许渲染入口及受控的公开图片/字体请求，阻断平台管理接口、云元数据和其他内网地址。实施时通过浏览器容器网络/代理落实，不能仅靠 HTTP 预探测实现。

HTTP 探测和 Chrome 必须使用同一任务凭证、同一版本。只接受预期 HTML 和任务响应标记，页面 readyState complete 本身不代表访问成功。到达目标文档后检查版本标记和 Vue 根节点内容，再有限等待字体及图片；不存在不可恢复错误时，个别外部图片超时可截已有内容，不能为一张图片无限等待。

## 图片、COS 和封面读取

- 默认桌面首屏 1600×900，输出 JPEG，沿用卡片 16:9，不默认截图整个长页面。
- 压缩质量初值 0.75，目标文件不超过 500 KiB；实际大小、视觉效果验证后再调整，不能直接声称优于现有参数。
- COS 上传成功、文件校验通过后才更新 app；日志记录 taskId、appId、version、阶段和耗时，不记录密钥。
- 自动封面对象采用私有访问，AppVO.cover 返回平台封面读取地址；后端按 AppPolicy.canRead 校验后流式读取 COS，不对外返回永久公开 URL。
- 封面读取建议 `GET /api/app/cover/image?appId=...&revision=...`；revision 只用于缓存更新，不授予访问权。私有响应 no-store，公开也采用短缓存或 no-store，避免转私有后继续从 CDN 分发。
- 私有应用封面只向本人和管理员返回，公开作品可按现有可见性公开读取。部署链接可分享不等于私有项目封面需要出现在公开列表。
- 如果后来改用 COS 短期签名 URL，必须明确到期前仍可访问的边界；第一版采用平台鉴权读取，方便立即撤销。

当前 InvalidAccessKeyId 需要正确配置实际运行进程的 COS_SECRET_ID 和 COS_SECRET_KEY 并重启。队列和重试无法修复错误密钥。COS 桶策略、真实上传和读图需在本机联调确认；设计文档不代表已完成云配置。

## API 与前端契约

所有下列接口是拟新增接口，沿用 BaseResponse；appId、taskId、revision 在 JSON 中返回字符串，避免 JavaScript 大整数精度问题。

| 接口 | 权限 | 返回/行为 |
| --- | --- | --- |
| `GET /api/app/cover/status?appId=...` | 本人或管理员 | 当前目标版本、已绑定版本、状态、是否可重试、脱敏错误和建议轮询间隔 |
| `POST /api/app/cover/regenerate` | 本人或管理员 | 幂等提交自动截图任务；不接收 URL，返回 taskId、status |
| `POST /api/app/cover/mode` | 本人或管理员 | 显式选择 AUTO/MANUAL；MANUAL 复用现有链接校验，切换时撤销旧任务资格 |
| `GET /api/app/cover/image` | 按 AppPolicy.canRead | 图片二进制；无权、已删除或无图片时按统一策略返回 404 |

强制重截请求包含 appId、expectedVersion、requestId。服务端检查目标版本仍匹配；相同 requestId 和参数重试返回原结果，同键不同参数拒绝，幂等记录在业务事务中保存到任务表。自动触发的 requestId 为 NULL，不占用手动幂等键。保证手动请求幂等窗口至少 24 小时，终态记录保留 30 天。每个应用手动触发间隔初值 30 秒，每用户每分钟最多 5 次；拒绝返回清晰提示，不启动任务。MANUAL 模式下必须先显式切换为 AUTO，不能由截图按钮悄悄覆盖手工链接。

AppVO 和前端 AppInfo 增加 coverMode、coverStatus、coverVersion、coverTargetVersion、coverRevision。列表批量取得这些字段，禁止为每张卡片单独查询数据库。详细错误只在本人/管理员状态接口返回。

| 前端状态 | 展示 |
| --- | --- |
| NONE，无旧图 | 现有渐变占位 |
| PENDING/RUNNING/RETRY_WAIT，无旧图 | 占位和“封面生成中” |
| 新任务进行中，有旧图 | 保留旧图，提示“封面更新中” |
| FAILED | 保留旧图或占位，在详情提供失败原因及重试 |
| SUCCEEDED | 替换到新 revision 对应图片，停止轮询 |
| 图片本身加载失败 | 触发现有 img onerror 回退，不在 onerror 中自动发起截图 |

详情中展示封面模式和“重新生成封面”按钮。任务存在时每 3 秒刷新状态；页面隐藏时暂停，离开页面销毁定时器，最长轮询 2 分钟，之后显示“后台处理中，可刷新查看”。首页仅在有待完成封面时刷新当前列表页，避免每张卡片各起定时器。完成后更新 cover URL/revision，使已有 coverFailed 标记按现有 watch 重置。

## 清理和服务关闭

- 每任务一个临时目录，截图或上传失败也在 finally 中清理；目录命名使用完整 UUID。只允许操作配置根目录下的普通目录，拒绝软链接逃逸。
- 每日 `0 0 2 * * *`、`zone = Asia/Shanghai` 清理超过 24 小时且不属于有效任务的残留目录；不是每天无条件清空整个 screenshots 目录。
- 删除应用的事务中取消待执行任务、撤销执行凭证并写入对象清理记录，提交后再做磁盘/COS 操作。Worker 完成时再次检查删除状态。
- COS 删除任务每分钟限量处理；不存在视为成功，暂时错误重试。旧图片保留 24 小时后清理，但仍被应用/有效任务引用时必须跳过。
- 任务数据保留 30 天；只能清理终态记录，且候选对象、COS 清理意图已处理完毕。活跃任务及其令牌不可被记录清理误删。
- 服务关闭先停止领取，允许当前任务在有限时间内收尾，再关闭其浏览器与执行器。异常退出由租约恢复，不把一次停机永久标成截图失败。

## 类职责和接入顺序

| 拟新增/调整类 | 职责 |
| --- | --- |
| `cover/AppCoverTaskService` | 事务内创建任务、去重、模式切换和手动重截 |
| `cover/AppCoverTaskRepository` | JdbcTemplate 持久化、原子领取、租约、令牌和条件回写 |
| `cover/AppCoverWorker` | 一次任务的截图、上传、发布或失败分类 |
| `cover/ScreenshotManager` | 有界执行器、任务专属浏览器生命周期及截止时间监督 |
| `cover/CoverRenderService` | 指定版本产物渲染和短期凭证 |
| `cover/AppCoverScheduler` | 到期调度、租约恢复、补图、文件及 COS 清理 |
| `controller/AppCoverController` | 权限与参数检查、状态/重截/图片读取 |
| `config/CoverProperties` | 开关、内部地址、队列、超时、重试和清理参数 |
| `manager/CosManager` | 对象上传、鉴权读取和受控对象删除 |

生成、回退和部署都从 workspace 的事务路径创建任务，避免 facade、handler、service 各触发一次。JsonMessageStreamHandler 继续只处理流内容，不增加截图或构建副作用。补偿调度只调用幂等任务入口。

有界调度器和文件清理器受 Spring 生命周期管理，不依赖普通静态工具类上的 @PreDestroy 生效。

## 分步交付与验收

1. 修复运行配置并完成基础截图验证：正确 COS 密钥、真实页面可访问、错误页拒绝、超时终止；不承诺本阶段已经有持久化队列。
2. 后端任务核心：候选迁移评审后生成迁移和初始化 SQL，接入单线程调度、租约、重试、固定版本渲染及条件绑定。
3. 接入生成/回退/部署与 API，补齐前端状态、模式选择、鉴权读图和手动重截。
4. 完成补偿、重启恢复、删除/换图清理、孤儿对象及临时目录清理，并增加 CI 回归。

| 验收场景 | 必须满足的结果 |
| --- | --- |
| 并发提交多个应用 | 单实例同一时刻只有一个截图任务执行，不出现 A 的封面绑定到 B |
| 连续点击同版本部署 | 复用任务或已有封面，不重复上传 |
| 已生成但未部署 | 固定成功版本能生成封面，不强迫用户先公开或部署 |
| 私有应用 | 无凭证渲染和无权限读图被拒绝；合法任务可读取指定版本 |
| 任务中途生成 v2 或修改手工封面 | v1/旧 revision 结果无法覆盖新目标 |
| 任务中途删除应用 | 后续结果无法回写，产生对象最终被清理 |
| HTTP 404/403、错误文档、渲染异常 | 不上传错误页、不把失败当作成功 |
| COS 密钥错误或权限错误 | 明确失败并保留原图，不无限重试 |
| COS 短暂 5xx、服务重启 | 有限重试/租约恢复，终态和图片绑定一致 |
| Future 超时但浏览器仍忙 | 该任务进程被受控终止，不释放旧执行的回写权 |
| 队列满 | 接口不执行截图，数据库任务仍在，后续能被调度 |
| 上传成功后、绑定前崩溃 | 候选对象可追踪，后续成功绑定或被清理 |
| 首页图片加载失败 | 正常回退，不触发截图请求风暴 |

自动测试使用 H2/临时目录、受控 HTTP 页面和假的 COS 存储；另用 MySQL 验证原子领取、唯一键、事务和时间精度，用真实 Chrome 验证渲染与并发隔离。真实 COS 联调只使用运行环境凭证，测试不得输出密钥或误操作用户其他对象。未实际执行的验证不能写为已通过。

## 实施参考

- [JDK 21 Executors：newSingleThreadExecutor 使用无界队列](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newSingleThreadExecutor())。
- [Selenium ThreadGuard：WebDriver 的线程归属](https://www.selenium.dev/documentation/webdriver/support_features/thread_guard/)。
- [Spring Framework 6.2：执行器、队列容量与调度](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)。
- [现有工作台版本与权限约定](03-app-workspace.md)。
- [现有 Vue 工程构建与运行产物约定](06-vue-project-generation.md)。
