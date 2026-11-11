package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yuelei.appforge.controller.*;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.dto.app.AppQueryRequest;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import reactor.core.publisher.Flux;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 真实 H2 事务、版本文件与 HTTP SSE；不连接用户数据库或调用模型。 */
class AppWorkspaceTest {
    @TempDir Path root;
    JdbcTemplate jdbc;
    AppFileStore store;
    AppWorkspaceService workspace;
    User owner, other, admin;
    long id = 475952860203356160L;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE chat_memory_summary(appId bigint primary key)");
        jdbc.execute(
                "CREATE TABLE app(id bigint primary key,appName varchar(256),cover"
                    + " varchar(512),initPrompt text,codeGenType varchar(64),deployKey"
                    + " varchar(64),deployedTime timestamp,priority int default 0,userId"
                    + " bigint,editTime timestamp,createTime timestamp default"
                    + " current_timestamp,updateTime timestamp default current_timestamp,isDelete"
                    + " int default 0,description varchar(1000) default '',tags varchar(256)"
                    + " default '',visibility varchar(16) default 'PRIVATE',generationStatus"
                    + " varchar(16) default 'IDLE',generationError varchar(512) default"
                    + " '',generationToken varchar(36) default '',currentVersion int default"
                    + " 0,deployedVersion int default 0,deploymentStatus varchar(16) default"
                    + " 'OFFLINE',featureStatus varchar(16) default 'NONE',featureReason"
                    + " varchar(256) default '')");
        jdbc.execute(
                "CREATE TABLE app_version(id bigint auto_increment primary key,appId"
                    + " bigint,versionNo int,kind varchar(16),sourceVersion int,summary"
                    + " varchar(256),codeFiles text,createTime timestamp,unique(appId,versionNo))");
        jdbc.execute(
                "CREATE TABLE app_file_cleanup(appId bigint primary key,codeGenType"
                        + " varchar(64),deployKey varchar(64),createTime timestamp default"
                        + " current_timestamp)");
        jdbc.update(
                "INSERT INTO app(id,appName,userId,codeGenType) VALUES(?,?,?,?)",
                id,
                "我的博客",
                100,
                "multi_file");
        store = new AppFileStore();
        ReflectionTestUtils.setField(store, "configuredRoot", root.toString());
        workspace = new AppWorkspaceService();
        ReflectionTestUtils.setField(workspace, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(workspace, "fileStore", store);
        ReflectionTestUtils.setField(
                workspace,
                "transactionTemplate",
                new TransactionTemplate(new DataSourceTransactionManager(ds)));
        owner = user(100, "user");
        other = user(200, "user");
        admin = user(300, "admin");
    }

    User user(long id, String role) {
        User u = new User();
        u.setId(id);
        u.setUserRole(role);
        return u;
    }

    int snapshot(String token, String html) {
        jdbc.update(
                "UPDATE app SET generationStatus='GENERATING',generationToken=? WHERE id=?",
                token,
                id);
        return workspace.complete(
                id,
                token,
                Map.of(
                        "index.html",
                        html,
                        "style.css",
                        "body{color:red}",
                        "script.js",
                        "console.log(1)"),
                "生成博客");
    }

    @Test
    void versionsAreImmutableAndRollbackDoesNotChangePublishedVersion() throws Exception {
        assertEquals(1, snapshot("a", "<h1>v1</h1>"));
        String key = workspace.deploy(id, owner);
        assertEquals(2, snapshot("b", "<h1>v2</h1>"));
        assertEquals(3, workspace.rollback(id, 1, owner));
        assertEquals("<h1>v1</h1>", workspace.files(id, 1).get("index.html"));
        assertEquals("<h1>v2</h1>", workspace.files(id, 2).get("index.html"));
        assertEquals("<h1>v1</h1>", workspace.files(id, 3).get("index.html"));
        assertEquals(1, workspace.requireApp(id).getDeployedVersion());
        assertEquals(
                "<h1>v1</h1>", Files.readString(store.versionDir(id, 3).resolve("index.html")));
        assertEquals(key, workspace.deploy(id, owner));
        assertEquals(3, workspace.requireApp(id).getDeployedVersion());
        assertEquals(3, workspace.versions(id, owner).size());
    }

    @Test
    void stopTokenPreventsLateSaveAndOtherUsersCannotReadHistory() {
        snapshot("a", "<h1>old</h1>");
        jdbc.update("UPDATE app SET generationStatus='STOPPED',generationToken='' WHERE id=?", id);
        assertThrows(
                BusinessException.class,
                () -> workspace.complete(id, "a", Map.of("index.html", "late"), "late"));
        assertEquals(1, workspace.requireApp(id).getCurrentVersion());
        assertThrows(BusinessException.class, () -> workspace.versions(id, other));
        assertThrows(BusinessException.class, () -> workspace.rollback(id, 1, other));
        assertThrows(BusinessException.class, () -> workspace.deploy(id, other));
        assertEquals(1, workspace.versions(id, admin).size());
    }

    @Test
    void invalidGeneratedFilesNeverAdvanceVersion() {
        assertThrows(BusinessException.class, () -> snapshot("a", ""));
        assertEquals(0, workspace.requireApp(id).getCurrentVersion());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_version", Integer.class));
    }

    @Test
    void legacyCodeIsImportedBeforeFirstEdit() throws Exception {
        Path dir = root.resolve("code_output/multi_file_" + id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.html"), "<h1>legacy</h1>");
        assertEquals(1, workspace.ensureBaseline(id));
        assertEquals(1, workspace.ensureBaseline(id));
        assertEquals("IMPORT", workspace.versions(id, owner).get(0).kind());
        assertEquals("<h1>legacy</h1>", workspace.files(id, 1).get("index.html"));
    }

    @Test
    void deploymentSharingPrivatePreviewOfflineAndSoftDeleteAreCheckedAtResourceEndpoint() {
        snapshot("a", "<h1>private</h1>");
        String key = workspace.deploy(id, owner);
        StaticResourceController controller = new StaticResourceController();
        ReflectionTestUtils.setField(controller, "workspace", workspace);
        AtomicReference<User> viewer = new AtomicReference<>();
        UserService users =
                (UserService)
                        Proxy.newProxyInstance(
                                UserService.class.getClassLoader(),
                                new Class[] {UserService.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("getLoginUser")) {
                                        if (viewer.get() == null)
                                            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
                                        return viewer.get();
                                    }
                                    return null;
                                });
        ReflectionTestUtils.setField(controller, "userService", users);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(200, controller.getDeployIndex(key, request).getStatusCode().value());
        viewer.set(other);
        assertEquals(404, controller.getPreviewIndex(id, request).getStatusCode().value());
        viewer.set(owner);
        assertEquals(200, controller.getDeployIndex(key, request).getStatusCode().value());
        jdbc.update("UPDATE app SET visibility='PUBLIC' WHERE id=?", id);
        viewer.set(null);
        assertEquals(200, controller.getDeployIndex(key, request).getStatusCode().value());
        assertEquals(
                "sandbox allow-scripts allow-forms",
                controller
                        .getDeployIndex(key, request)
                        .getHeaders()
                        .getFirst("Content-Security-Policy"));
        workspace.offline(id, owner);
        assertEquals(404, controller.getDeployIndex(key, request).getStatusCode().value());
        workspace.deploy(id, owner);
        jdbc.update("INSERT INTO chat_memory_summary(appId) VALUES(?)", id);
        workspace.delete(id, owner);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_memory_summary", Integer.class));
        assertEquals(404, controller.getDeployIndex(key, request).getStatusCode().value());
        assertEquals(404, controller.getPreviewIndex(id, request).getStatusCode().value());
        assertFalse(Files.exists(root.resolve("code_versions/" + id)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_version", Integer.class));
    }

    @Test
    void failedFileCleanupRemainsQueuedAndRetryFinishes() {
        snapshot("a", "<h1>x</h1>");
        AppFileStore broken =
                new AppFileStore() {
                    @Override
                    public void cleanup(long i, String t, String k) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR);
                    }
                };
        ReflectionTestUtils.setField(workspace, "fileStore", broken);
        assertTrue(workspace.delete(id, owner));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM app_file_cleanup", Integer.class));
        ReflectionTestUtils.setField(workspace, "fileStore", store);
        workspace.cleanFiles();
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM app_file_cleanup", Integer.class));
    }

    @Test
    void publicSearchFiltersPrivateAppsAndSupportsTagsDescriptionAndPinnedOrder() {
        jdbc.update(
                "UPDATE app SET"
                    + " visibility='PUBLIC',priority=99,description='轻量知识管理',tags='|知识库|工具|' WHERE"
                    + " id=?",
                id);
        jdbc.update(
                "INSERT INTO app(id,appName,userId,codeGenType,priority,visibility,tags)"
                    + " VALUES(2,'置顶工具',200,'html',999,'PUBLIC','|工具|'),(3,'私有工具',200,'html',999,'PRIVATE','|工具|')");
        AppCatalogSearch catalog = new AppCatalogSearch();
        ReflectionTestUtils.setField(catalog, "jdbcTemplate", jdbc);
        AppQueryRequest query = new AppQueryRequest();
        query.setPageNum(1);
        query.setPageSize(20);
        query.setTag("工具");
        assertEquals(
                List.of(2L, id),
                catalog.search(query, null, true, true).getRecords().stream()
                        .map(App::getId)
                        .toList());
        query.setSearchText("知识管理");
        assertEquals(1, catalog.search(query, null, true, false).getTotalRow());
        query.setSearchText("% OR 1=1");
        assertEquals(0, catalog.search(query, null, true, false).getTotalRow());
        query.setPageNum(51);
        assertThrows(BusinessException.class, () -> catalog.search(query, null, true, false));
    }

    @Test
    void featuredRequestRequiresPublicOwnershipAndIsReviewedByAdmin() {
        snapshot("a", "<h1>x</h1>");
        assertThrows(BusinessException.class, () -> workspace.feature(id, owner, null, null));
        jdbc.update("UPDATE app SET visibility='PUBLIC' WHERE id=?", id);
        assertThrows(BusinessException.class, () -> workspace.feature(id, other, null, null));
        workspace.feature(id, owner, null, null);
        assertEquals("PENDING", workspace.requireApp(id).getFeatureStatus());
        assertThrows(BusinessException.class, () -> workspace.feature(id, owner, true, ""));
        workspace.feature(id, admin, true, "通过");
        assertEquals(99, workspace.requireApp(id).getPriority());
        assertThrows(BusinessException.class, () -> workspace.feature(id, admin, true, "重复审核"));
    }

    AppGenerationService generation(Flux<String> source) {
        AiCodeGeneratorFacade facade =
                new AiCodeGeneratorFacade() {
                    @Override
                    public Flux<String> generateCodeStream(
                            String request,
                            String memoryMessage,
                            CodeGenTypeEnum type,
                            Long appId,
                            long currentUserMessageId) {
                        return source;
                    }

                    @Override
                    public void rememberSuccessfulConversation(
                            long appId, String user, String ai) {}
                };
        AppGenerationService gen = new AppGenerationService();
        ReflectionTestUtils.setField(gen, "workspace", workspace);
        ReflectionTestUtils.setField(gen, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(gen, "generator", facade);
        return gen;
    }

    @Test
    void completedStreamSavesExactlyOneVersionAndFailureKeepsPrevious() {
        var gen =
                generation(
                        Flux.just(
                                "```html\n"
                                        + "<h1>完成</h1>\n"
                                        + "```\n"
                                        + "```css\n"
                                        + "body{}\n"
                                        + "```\n"
                                        + "```js\n"
                                        + "console.log(1)\n"
                                        + "```"));
        gen.generate(workspace.requireApp(id), "生成").blockLast(Duration.ofSeconds(5));
        assertEquals(1, workspace.requireApp(id).getCurrentVersion());
        assertEquals("SUCCEEDED", workspace.requireApp(id).getGenerationStatus());
        generation(Flux.error(new java.net.SocketTimeoutException("connect")))
                .generate(workspace.requireApp(id), "修改")
                .onErrorComplete()
                .blockLast(Duration.ofSeconds(5));
        assertEquals("FAILED", workspace.requireApp(id).getGenerationStatus());
        assertEquals(1, workspace.requireApp(id).getCurrentVersion());
        assertTrue(workspace.requireApp(id).getGenerationError().contains("超时"));
    }

    @Test
    void stoppingCancelsUpstreamAndDoesNotSavePartialCode() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1), ended = new CountDownLatch(1);
        var gen =
                generation(
                        Flux.create(
                                sink -> {
                                    sink.onCancel(() -> cancelled.set(true));
                                    sink.next("```html\n<h1>半截");
                                    started.countDown();
                                }));
        var subscription =
                gen.generate(workspace.requireApp(id), "生成")
                        .subscribe(v -> {}, e -> ended.countDown(), ended::countDown);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(
                RuntimeException.class,
                () ->
                        gen.generate(workspace.requireApp(id), "重复")
                                .blockLast(Duration.ofSeconds(2)));
        gen.stop(id, owner);
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertTrue(cancelled.get());
        assertEquals(0, workspace.requireApp(id).getCurrentVersion());
        assertEquals("STOPPED", workspace.requireApp(id).getGenerationStatus());
        subscription.dispose();
    }

    com.yuelei.appforge.service.impl.AppServiceImpl historyAppService(
            AppGenerationService gen, List<String> history) {
        var service =
                new com.yuelei.appforge.service.impl.AppServiceImpl() {
                    @Override
                    public App getById(java.io.Serializable key) {
                        return workspace.requireApp(Long.parseLong(key.toString()));
                    }
                };
        ChatHistoryService recorder =
                (ChatHistoryService)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {ChatHistoryService.class},
                                (p, m, args) -> {
                                    if (m.getName().equals("addChatMessage")
                                            || m.getName().equals("appendChatMessage")) {
                                        history.add(args[2] + ":" + args[1]);
                                        return m.getName().equals("appendChatMessage")
                                                ? Long.valueOf(history.size())
                                                : Boolean.TRUE;
                                    }
                                    return null;
                                });
        ReflectionTestUtils.setField(service, "generation", gen);
        ReflectionTestUtils.setField(service, "chatHistoryService", recorder);
        ReflectionTestUtils.setField(
                service,
                "streamHandlerExecutor",
                new com.yuelei.appforge.core.handler.StreamHandlerExecutor());
        return service;
    }

    @Test
    void historyRecordingUsesWorkspaceGenerationAndSavesTheCompleteAiMessage() {
        var history = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var gen = generation(Flux.just("```html\n<h1>", "博客</h1>\n```"));
        var service = historyAppService(gen, history);
        var flux = service.chatToGenCode(id, "创建博客", owner);
        assertTrue(history.isEmpty());
        flux.blockLast(Duration.ofSeconds(5));
        assertEquals(List.of("user:创建博客", "ai:```html\n<h1>博客</h1>\n```"), history);
        assertEquals(1, workspace.requireApp(id).getCurrentVersion());
        assertEquals("SUCCEEDED", workspace.requireApp(id).getGenerationStatus());
    }

    @Test
    void duplicateGenerationDoesNotRecordRejectedUserMessagesAndStopKeepsHistory()
            throws Exception {
        var history = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var gen = generation(Flux.never());
        var service = historyAppService(gen, history);
        var ended = new CountDownLatch(1);
        var subscription =
                service.chatToGenCode(id, "开始", owner).subscribe(x -> {}, e -> ended.countDown());
        for (int i = 0; i < 100 && history.isEmpty(); i++) Thread.sleep(10);
        assertEquals(List.of("user:开始"), history);
        assertThrows(
                RuntimeException.class,
                () -> service.chatToGenCode(id, "重复", owner).blockLast(Duration.ofSeconds(3)));
        assertEquals(List.of("user:开始"), history);
        gen.stop(id, owner);
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("user:开始", "ai:生成已停止，未保存不完整代码"), history);
        assertEquals(0, workspace.requireApp(id).getCurrentVersion());
        subscription.dispose();
    }

    @Test
    void historyFailureMessageIsSanitizedAndDoesNotCreateAVersion() {
        var history = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var service =
                historyAppService(
                        generation(Flux.error(new RuntimeException("provider internal secret"))),
                        history);
        assertThrows(
                RuntimeException.class,
                () -> service.chatToGenCode(id, "失败请求", owner).blockLast(Duration.ofSeconds(3)));
        assertEquals(2, history.size());
        assertFalse(history.getLast().contains("secret"));
        assertEquals(0, workspace.requireApp(id).getCurrentVersion());
    }

    @Test
    void restartMarksInterruptedJobsAsFailedAndAllowsRetry() {
        jdbc.update(
                "UPDATE app SET generationStatus='GENERATING',generationToken='old' WHERE id=?",
                id);
        generation(Flux.empty()).recoverInterrupted();
        assertEquals("FAILED", workspace.requireApp(id).getGenerationStatus());
    }

    @Test
    void aiNameIsAppliedOnlyIfUserHasNotRenamed() throws Exception {
        var finished = new CountDownLatch(1);
        var jdbcSpy =
                new JdbcTemplate(jdbc.getDataSource()) {
                    @Override
                    public int update(String sql, Object... args) {
                        int result = super.update(sql, args);
                        finished.countDown();
                        return result;
                    }
                };
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ai =
                (com.yuelei.appforge.ai.AiCodeGeneratorService)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {com.yuelei.appforge.ai.AiCodeGeneratorService.class},
                                (p, m, a) -> {
                                    started.countDown();
                                    release.await(3, TimeUnit.SECONDS);
                                    return "{\"name\":\"AI 博客\"}";
                                });
        var naming = new AppNameService();
        ReflectionTestUtils.setField(naming, "ai", ai);
        ReflectionTestUtils.setField(naming, "jdbcTemplate", jdbcSpy);
        naming.suggest(id, "创建博客", "我的博客");
        assertTrue(started.await(3, TimeUnit.SECONDS));
        jdbc.update("UPDATE app SET appName='手动名称' WHERE id=?", id);
        release.countDown();
        assertTrue(finished.await(3, TimeUnit.SECONDS));
        assertEquals("手动名称", workspace.requireApp(id).getAppName());
    }

    @Test
    void metadataValidationRejectsUnsafeOrExcessiveTags() {
        assertEquals(
                List.of("博客", "Java"),
                AppPolicy.decodeTags(AppPolicy.encodeTags(List.of(" 博客 ", "Java", "博客"))));
        assertThrows(BusinessException.class, () -> AppPolicy.encodeTags(List.of("a|b")));
        assertThrows(
                BusinessException.class,
                () -> AppPolicy.encodeTags(List.of("1", "2", "3", "4", "5", "6")));
        assertThrows(BusinessException.class, () -> AppPolicy.visibility("friends"));
        assertEquals("个人博客网站", AppNameService.fallback("帮我创建个人博客网站，采用浅色设计"));
    }

    @Test
    void versionFileFailureKeepsTheOldSnapshot() {
        snapshot("a", "<h1>old</h1>");
        AppFileStore broken =
                new AppFileStore() {
                    @Override
                    public void writeVersion(long i, int v, Map<String, String> files) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "磁盘不可写");
                    }
                };
        ReflectionTestUtils.setField(broken, "configuredRoot", root.toString());
        ReflectionTestUtils.setField(workspace, "fileStore", broken);
        assertThrows(BusinessException.class, () -> snapshot("b", "<h1>new</h1>"));
        assertEquals(1, workspace.requireApp(id).getCurrentVersion());
        assertEquals(1, workspace.versions(id, owner).size());
    }

    @Test
    void privateHtmlEmbedsAssetsWithoutExposingSessionCookiesToSandbox() {
        String html =
                AppPageRenderer.inlinePrivateAssets(
                        Map.of(
                                "index.html",
                                "<link href='style.css?v=1' rel='stylesheet'><script"
                                        + " src='./script.js'></script><img"
                                        + " src='https://example.com/a.png'>",
                                "style.css",
                                "h1{color:red}",
                                "script.js",
                                "window.name='你好';"));
        assertTrue(html.contains("data:text/css;charset=utf-8;base64,"));
        assertTrue(html.contains("data:text/javascript;charset=utf-8;base64,"));
        assertTrue(html.contains("https://example.com/a.png"));
        assertFalse(html.contains("href=\"style.css"));
    }

    @Test
    void uncommittedFileSnapshotDoesNotPoisonNextGeneration() throws Exception {
        snapshot("a", "<h1>v1</h1>");
        Path orphan = store.versionDir(id, 2);
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("index.html"), "orphan");
        assertEquals(2, snapshot("b", "<h1>v2</h1>"));
        assertEquals("<h1>v1</h1>", workspace.files(id, 1).get("index.html"));
        assertEquals("<h1>v2</h1>", Files.readString(orphan.resolve("index.html")));
    }

    @Test
    void sseFailureReturnsNamedErrorEventInsteadOfJsonNegotiationFailure() throws Exception {
        AppController controller = new AppController();
        AppService apps =
                (AppService)
                        Proxy.newProxyInstance(
                                AppService.class.getClassLoader(),
                                new Class[] {AppService.class},
                                (p, m, a) ->
                                        Flux.error(new java.net.SocketTimeoutException("connect")));
        UserService users =
                (UserService)
                        Proxy.newProxyInstance(
                                UserService.class.getClassLoader(),
                                new Class[] {UserService.class},
                                (p, m, a) -> owner);
        ReflectionTestUtils.setField(controller, "appService", apps);
        ReflectionTestUtils.setField(controller, "userService", users);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MvcResult pending =
                mvc.perform(
                                get("/app/chat/gen/code")
                                        .param("appId", String.valueOf(id))
                                        .param("message", "生成")
                                        .accept("text/event-stream"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String text =
                mvc.perform(asyncDispatch(pending))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("event:generation-error"), text);
        assertFalse(text.contains("event:done"));
        assertTrue(text.contains("超时"), text);
    }
}
