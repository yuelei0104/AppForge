package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.controller.*;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.workspace.vue.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 工程工具真实落盘，LangChain4j 原生工具循环使用确定性模型，工作区使用 H2 事务。 */
class VueProjectTest {
    @TempDir Path temp;

    static Map<String, String> source() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("package.json", VueProjectFiles.PACKAGE);
        files.put("vite.config.js", VueProjectFiles.CONFIG);
        files.put(
                "index.html",
                "<!doctype html><html lang='zh-CN'><head><meta charset='UTF-8'><meta"
                        + " name='viewport' content='width=device-width,"
                        + " initial-scale=1.0'><title>城市阅读馆</title></head><body><div"
                        + " id='app'></div><script type='module'"
                        + " src='/src/main.js'></script></body></html>");
        files.put(
                "src/main.js",
                "import {createApp} from 'vue';import App from './App.vue';import router from"
                        + " './router';import"
                        + " './styles/main.css';createApp(App).use(router).mount('#app')");
        files.put(
                "src/App.vue",
                "<script setup>import {RouterView} from 'vue-router';import AppNav from"
                    + " '@/components/AppNav.vue'</script><template><AppNav/><main><RouterView/></main><footer>城市阅读馆"
                    + " · 在阅读中遇见更大的世界<br>开放时间：周二至周日 09:00—20:00</footer></template>");
        files.put(
                "src/components/AppNav.vue",
                "<script setup>import {RouterLink} from"
                    + " 'vue-router'</script><template><header><RouterLink class='brand'"
                    + " to='/'>城市阅读馆</RouterLink><nav aria-label='主导航'><RouterLink"
                    + " to='/'>首页</RouterLink><RouterLink to='/books'>发现好书</RouterLink><RouterLink"
                    + " to='/about'>到馆指南</RouterLink></nav></header></template>");
        files.put(
                "src/router/index.js",
                "import {createRouter,createWebHashHistory} from 'vue-router';import Home from"
                    + " '@/pages/Home.vue';import Books from '@/pages/Books.vue';import About from"
                    + " '@/pages/About.vue';export default"
                    + " createRouter({history:createWebHashHistory(),routes:[{path:'/',component:Home},{path:'/books',component:Books},{path:'/about',component:About}]})");
        files.put(
                "src/pages/Home.vue",
                "<script setup>import {RouterLink} from 'vue-router';const"
                    + " events=[{title:'周末共读',text:'每周六下午，一起读完一本书，分享不同的生活经验。'},{title:'城市记忆',text:'从老街巷到新地标，用文字记录身边的变化。'},{title:'亲子故事会',text:'为六至十岁的小读者准备一段充满想象的旅程。'}]</script><template><section"
                    + " class='hero'><p"
                    + " class='eyebrow'>给生活留一页空白</p><h1>在城市里，找到你的阅读角落</h1><p>精选书单、线下共读与安静的自习空间，陪你探索每一个值得停留的故事。</p><RouterLink"
                    + " class='button' to='/books'>发现本月好书</RouterLink><img"
                    + " src='https://picsum.photos/seed/reading-room/1000/420'"
                    + " alt='阅读空间'/></section><section><h2>把阅读变成日常</h2><div class='grid'><article"
                    + " v-for='event in events'"
                    + " :key='event.title'><h3>{{event.title}}</h3><p>{{event.text}}</p></article></div></section><section"
                    + " class='callout'><h2>一本好书，一段新的相遇</h2><p>图书借阅免费开放，首次到馆可在服务台办理读者卡。让我们从今天开始，为阅读留出时间。</p><RouterLink"
                    + " to='/about'>查看到馆指南 →</RouterLink></section></template>");
        files.put(
                "src/pages/Books.vue",
                "<script setup>import {computed,ref} from 'vue';const query=ref('');const"
                    + " books=[{title:'乡土中国',author:'费孝通',category:'社会观察',text:'从日常生活理解中国基层社会的结构与关系。'},{title:'小王子',author:'圣埃克苏佩里',category:'文学故事',text:'关于友谊、责任与成长，写给所有曾经是孩子的大人。'},{title:'万物简史',author:'比尔·布莱森',category:'科学探索',text:'用充满好奇的视角，探索宇宙、地球与生命的故事。'}];const"
                    + " filtered=computed(()=>books.filter(book=>(book.title+book.author+book.category).includes(query.value.trim())))</script><template><section><p"
                    + " class='eyebrow'>每月精选</p><h1>发现好书</h1><label class='search'>搜索书名、作者或分类<input"
                    + " v-model='query' placeholder='例如：小王子'/></label><div class='grid'><article"
                    + " v-for='book in filtered'"
                    + " :key='book.title'><small>{{book.category}}</small><h2>{{book.title}}</h2><p>{{book.author}}</p><p>{{book.text}}</p></article></div><p"
                    + " v-if='!filtered.length'"
                    + " role='status'>没有找到相关书籍，试试其他关键词。</p></section></template>");
        files.put(
                "src/pages/About.vue",
                "<template><section><p class='eyebrow'>欢迎到来</p><h1>到馆指南</h1><div"
                    + " class='grid'><article><h2>开放时间</h2><p>周二至周日"
                    + " 09:00—20:00，周一闭馆整理。法定节假日安排请关注馆内公告。</p></article><article><h2>交通与地址</h2><p>安徽省合肥市城市文化中心二层。地铁文化广场站"
                    + " A 出口步行约五分钟。</p></article><article><h2>借阅须知</h2><p>凭有效证件办理读者卡，每次可借三本图书，借期三十天，可续借一次。</p></article></div><h2>常见问题</h2><details><summary>需要提前预约吗？</summary><p>日常阅读无需预约；周末共读活动需在服务台登记，席位按报名顺序安排。</p></details><details><summary>可以携带电脑吗？</summary><p>可以。自习区提供电源和无线网络，请保持安静并使用耳机。</p></details></section></template>");
        files.put(
                "src/styles/main.css",
                "*{box-sizing:border-box}body{margin:0;background:#f8f6f1;color:#243834;font:16px/1.8"
                    + " system-ui,sans-serif}a{color:#285b4f;text-decoration:none}header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:22px"
                    + " max(5vw,20px);border-bottom:1px solid"
                    + " #dfe5de;background:#fff}.brand{font-size:22px;font-weight:800}nav{display:flex;gap:24px}nav"
                    + " .router-link-exact-active{border-bottom:2px solid"
                    + " #35685a}main{max-width:1120px;margin:auto;padding:0"
                    + " 24px}section{padding:48px"
                    + " 0}h1{font-size:clamp(30px,4.5vw,54px);line-height:1.3;max-width:840px}h2{line-height:1.4}.eyebrow{color:#527665;letter-spacing:3px}.hero"
                    + " img{width:100%;height:300px;object-fit:cover;border-radius:22px;margin-top:36px}.button{display:inline-block;padding:12px"
                    + " 24px;background:#285b4f;color:white;border-radius:10px;margin-top:12px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px;margin-top:24px}article{background:#fff;border:1px"
                    + " solid"
                    + " #e0e5dd;border-radius:16px;padding:24px}.callout{background:#edf0e4;padding:32px;border-radius:20px;margin:10px"
                    + " 0 50px}footer{text-align:center;padding:36px;background:#e8ede7;color:#52645b}.search{display:grid;gap:8px;max-width:540px;margin:28px"
                    + " 0}input{font:inherit;border:1px solid"
                    + " #b8c9bf;border-radius:10px;padding:12px;background:white}details{border-bottom:1px"
                    + " solid #dbe3da;padding:18px"
                    + " 0}summary{cursor:pointer}a:focus-visible,input:focus-visible,summary:focus-visible{outline:3px"
                    + " solid"
                    + " #ba8a38;outline-offset:4px}@media(max-width:700px){header{align-items:flex-start;flex-direction:column;gap:10px}nav{gap:20px}.grid{grid-template-columns:1fr}main{padding:0"
                    + " 18px}section{padding:30px 0}.hero img{height:220px}}");
        return files;
    }

    static Map<String, String> runtime() {
        return Map.of(
                "dist/index.html",
                "<!doctype html><html><head><link rel='stylesheet'"
                        + " href='./assets/main.css'></head><body><div id='app'></div><script"
                        + " type='module' src='./assets/main.js'></script></body></html>",
                "dist/assets/main.js",
                "document.querySelector('#app').textContent='构建后的城市阅读馆'",
                "dist/assets/main.css",
                "body{color:green}");
    }

    ProjectTask filled(List<GenerationProgress> progress) {
        var task = new ProjectTask(Map.of(), progress::add);
        source().forEach(task::write);
        return task;
    }

    static ToolExecutionRequest call(String name, Map<String, String> args) {
        return ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .arguments(JSONUtil.toJsonStr(args))
                .build();
    }

    static class ScriptModel implements StreamingChatModel {
        int calls;
        final List<Integer> budgets = new CopyOnWriteArrayList<>();
        boolean fail, budgetExceeded, editing;

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().build();
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            budgets.add(request.maxOutputTokens());
            int step = calls++;
            AiMessage message;
            if (step == 0) message = AiMessage.from(call("readDirectory", Map.of()));
            else if (editing && step == 1)
                message =
                        AiMessage.from(
                                source().keySet().stream()
                                        .map(path -> call("readFile", Map.of("path", path)))
                                        .toList());
            else if (step == (editing ? 2 : 1))
                message =
                        AiMessage.from(
                                source().entrySet().stream()
                                        .map(
                                                e ->
                                                        call(
                                                                "writeFile",
                                                                Map.of(
                                                                        "path",
                                                                        e.getKey(),
                                                                        "content",
                                                                        e.getValue())))
                                        .toList());
            else message = AiMessage.from("不应展示的模型说明和源码");
            handler.onPartialResponse("不应展示的 npm install 使用指导");
            handler.onCompleteResponse(
                    ChatResponse.builder()
                            .aiMessage(message)
                            .finishReason(
                                    fail
                                            ? FinishReason.LENGTH
                                            : message.hasToolExecutionRequests()
                                                    ? FinishReason.TOOL_EXECUTION
                                                    : FinishReason.STOP)
                            .tokenUsage(new TokenUsage(10, budgetExceeded ? 20000 : 100))
                            .build());
        }
    }

    VueProjectRunner runner(StreamingChatModel model, VueProjectBuilder builder) {
        var runner = new VueProjectRunner();
        var facade =
                new AiCodeGeneratorFacade() {
                    @Override
                    public String contextForProject(String req, long id, long before) {
                        return req;
                    }
                };
        ReflectionTestUtils.setField(runner, "factory", new AiCodeGeneratorServiceFactory());
        ReflectionTestUtils.setField(runner, "facade", facade);
        ReflectionTestUtils.setField(runner, "streamingChatModel", model);
        ReflectionTestUtils.setField(runner, "builder", builder);
        return runner;
    }

    VueProjectBuilder successfulBuilder() {
        return new VueProjectBuilder() {
            @Override
            public Map<String, String> build(ProjectTask task) {
                task.seal();
                return runtime();
            }
        };
    }

    AppWorkspaceTest fixture() {
        var f = new AppWorkspaceTest();
        f.root = temp;
        f.setup();
        return f;
    }

    @Test
    void pathsRejectTraversalAbsolutePathsConfigurationAndWindowsDevices() {
        for (String path :
                List.of(
                        "../secret",
                        "/etc/passwd",
                        "C:/secret",
                        "src/../App.vue",
                        "src/.env",
                        ".npmrc",
                        "postcss.config.js",
                        "src/NUL.js",
                        "src/components/CON.vue",
                        "node_modules/x.js",
                        "dist/index.html",
                        "src/a\\b.js"))
            assertThrows(BusinessException.class, () -> VueProjectFiles.sourcePath(path), path);
        assertEquals("src/pages/Home.vue", VueProjectFiles.sourcePath("src/pages/Home.vue"));
    }

    @Test
    void writesUseCanonicalManifestAndDoNotExposeCommandExecution() {
        try (var task = filled(new CopyOnWriteArrayList<>())) {
            task.directory();
            task.read("package.json");
            task.write(
                    "package.json",
                    "{\"scripts\":{\"postinstall\":\"bad-command\"},\"dependencies\":{\"pinia\":\"latest\"}}");
            assertEquals(VueProjectFiles.PACKAGE, task.read("package.json"));
            assertFalse(task.read("package.json").contains("postinstall"));
            assertEquals(11, task.seal().size());
        }
    }

    @Test
    void existingFilesRequireDirectoryAndReadBeforeModification() {
        try (var task = new ProjectTask(source(), e -> {})) {
            assertThrows(BusinessException.class, () -> task.write("src/App.vue", "changed"));
            task.directory();
            assertThrows(BusinessException.class, () -> task.delete("src/App.vue"));
            task.read("src/App.vue");
            task.modify("src/App.vue", "城市阅读馆", "社区阅读馆");
            assertTrue(task.read("src/App.vue").contains("社区阅读馆"));
            assertThrows(BusinessException.class, () -> task.modify("src/App.vue", "不存在", "新内容"));
        }
    }

    @Test
    void fileCountAndSourceBudgetAndMissingRequiredFilesAreEnforced() {
        try (var task = new ProjectTask(Map.of(), e -> {})) {
            for (int i = 0; i < 29; i++) task.write("src/a" + i + ".js", "export default 1");
            assertThrows(BusinessException.class, () -> task.write("src/overflow.js", "x"));
            assertThrows(BusinessException.class, task::seal);
        }
        try (var task = filled(new ArrayList<>())) {
            assertThrows(
                    BusinessException.class, () -> task.write("src/large.js", "x".repeat(100001)));
        }
    }

    @Test
    void cancelledTasksCannotWriteOrReadAndAppsRemainIsolated() {
        try (var a = new ProjectTask(Map.of(), e -> {});
                var b = new ProjectTask(Map.of(), e -> {})) {
            a.write("src/private.js", "private app");
            assertThrows(BusinessException.class, () -> b.read("src/private.js"));
            a.cancel();
            assertThrows(
                    AppGenerationService.GenerationStoppedException.class,
                    () -> a.write("src/late.js", "late"));
            assertThrows(
                    AppGenerationService.GenerationStoppedException.class,
                    () -> a.read("src/private.js"));
        }
    }

    @Test
    void nativeToolLoopWritesEveryFileAndOnlyStreamsBasicProgress() {
        var events = new CopyOnWriteArrayList<GenerationProgress>();
        var model = new ScriptModel();
        var runner = runner(model, successfulBuilder());
        try (var task = new ProjectTask(Map.of(), events::add)) {
            String text =
                    String.join(
                            "",
                            runner.stream(1, 1, "创建阅读网站", task)
                                    .collectList()
                                    .block(Duration.ofSeconds(10)));
            assertTrue(text.startsWith("我会先"));
            assertTrue(text.endsWith("网站已生成完成。"));
            assertFalse(text.contains("npm"));
            assertFalse(text.contains("源码"));
            assertEquals(11, VueProjectFiles.sources(task.result()).size());
            assertTrue(
                    events.stream()
                            .anyMatch(
                                    e ->
                                            e.operation().equals("write_file")
                                                    && e.path().equals("src/App.vue")
                                                    && e.state().equals("SUCCEEDED")));
            assertEquals(3, model.calls);
            assertTrue(model.budgets.stream().allMatch(n -> n > 0 && n <= 8192));
            assertEquals(
                    "read_directory",
                    JSONUtil.parseObj(JSONUtil.toJsonStr(events.getFirst().toMap()))
                            .getStr("operation"));
            for (var e : events) assertFalse(JSONUtil.toJsonStr(e.toMap()).contains("<template>"));
        }
    }

    @Test
    void incompleteOrOverBudgetModelResponseDoesNotWriteFiles() {
        for (boolean budget : List.of(false, true)) {
            var model = new ScriptModel();
            model.fail = !budget;
            model.budgetExceeded = budget;
            var runner = runner(model, successfulBuilder());
            try (var task = new ProjectTask(Map.of(), e -> {})) {
                assertThrows(
                        RuntimeException.class,
                        () -> runner.stream(1, 1, "生成", task).blockLast(Duration.ofSeconds(5)));
                assertThrows(BusinessException.class, task::result);
            }
        }
    }

    @Test
    void generationCommitsSourcesAndBuiltAssetsTogetherAndRollbackPreservesArtifact() {
        var f = fixture();
        f.jdbc.update(
                "UPDATE app SET"
                    + " codeGenType='vue_project',generationStatus='GENERATING',generationToken='one'"
                    + " WHERE id=?",
                f.id);
        Map<String, String> files = new LinkedHashMap<>(source());
        files.putAll(runtime());
        f.workspace.complete(f.id, "one", files, "生成阅读馆");
        assertEquals(1, f.workspace.requireApp(f.id).getCurrentVersion());
        assertTrue(Files.exists(f.store.versionDir(f.id, 1).resolve("src/App.vue")));
        assertTrue(Files.exists(f.store.versionDir(f.id, 1).resolve("dist/assets/main.js")));
        assertFalse(f.workspace.versionFiles(f.id, 1, f.owner).containsKey("dist/index.html"));
        assertThrows(BusinessException.class, () -> f.workspace.versionFiles(f.id, 1, f.other));
        f.workspace.deploy(f.id, f.owner);
        f.workspace.rollback(f.id, 1, f.owner);
        assertEquals(2, f.workspace.requireApp(f.id).getCurrentVersion());
        assertEquals(1, f.workspace.requireApp(f.id).getDeployedVersion());
        assertEquals(
                runtime().get("dist/index.html"),
                f.workspace.files(f.id, 2).get("dist/index.html"));
    }

    @Test
    void failedBuildAndStopKeepPreviousVersionAndFiles() throws Exception {
        for (boolean stop : List.of(false, true)) {
            var f = fixture();
            f.jdbc.update(
                    "UPDATE app SET"
                        + " codeGenType='vue_project',generationStatus='GENERATING',generationToken='base'"
                        + " WHERE id=?",
                    f.id);
            var baseline = new LinkedHashMap<>(source());
            baseline.putAll(runtime());
            f.workspace.complete(f.id, "base", baseline, "初版");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var builder =
                    new VueProjectBuilder() {
                        @Override
                        public Map<String, String> build(ProjectTask task) {
                            task.seal();
                            entered.countDown();
                            if (stop)
                                try {
                                    release.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            task.check();
                            throw new BusinessException(ErrorCode.OPERATION_ERROR, "构建失败");
                        }
                    };
            var gen = f.generation(reactor.core.publisher.Flux.never());
            var model = new ScriptModel();
            model.editing = true;
            ReflectionTestUtils.setField(gen, "projectRunner", runner(model, builder));
            var ended = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();
            var subscription =
                    gen.generate(f.workspace.requireApp(f.id), "修改", () -> {}, () -> 1L, e -> {})
                            .subscribe(
                                    v -> {},
                                    e -> {
                                        error.set(e);
                                        ended.countDown();
                                    },
                                    ended::countDown);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            if (stop) gen.stop(f.id, f.owner);
            release.countDown();
            assertTrue(ended.await(5, TimeUnit.SECONDS));
            assertNotNull(error.get());
            assertEquals(1, f.workspace.requireApp(f.id).getCurrentVersion());
            assertEquals(baseline, f.workspace.files(f.id, 1));
            assertEquals(
                    stop ? "STOPPED" : "FAILED",
                    f.workspace.requireApp(f.id).getGenerationStatus());
            subscription.dispose();
        }
    }

    @Test
    void compiledAssetsRespectVisibilityAndPrivatePreviewInlinesModules() {
        var f = fixture();
        f.jdbc.update(
                "UPDATE app SET"
                    + " codeGenType='vue_project',generationStatus='GENERATING',generationToken='one'"
                    + " WHERE id=?",
                f.id);
        var files = new LinkedHashMap<>(source());
        files.putAll(runtime());
        f.workspace.complete(f.id, "one", files, "网站");
        var user = new AtomicReference<>(f.owner);
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenAnswer(i -> user.get());
        var controller = new StaticResourceController();
        ReflectionTestUtils.setField(controller, "workspace", f.workspace);
        ReflectionTestUtils.setField(controller, "userService", users);
        var req = new org.springframework.mock.web.MockHttpServletRequest();
        var response = controller.getPreviewIndex(f.id, req);
        assertEquals(200, response.getStatusCode().value());
        String html;
        try {
            html =
                    new String(
                            response.getBody().getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(html.contains("data:text/javascript"));
        assertFalse(html.contains("src=\"./assets/"));
        assertEquals(200, controller.previewAsset(f.id, "main.js", req).getStatusCode().value());
        assertEquals(
                404,
                controller.getPreviewResource(f.id, "package.json", req).getStatusCode().value());
        user.set(f.other);
        assertEquals(404, controller.previewAsset(f.id, "main.js", req).getStatusCode().value());
    }

    @Test
    void sourceDownloadContainsOnlyProjectSourceAndChecksOwner() throws Exception {
        var f = fixture();
        f.jdbc.update(
                "UPDATE app SET"
                    + " codeGenType='vue_project',generationStatus='GENERATING',generationToken='one'"
                    + " WHERE id=?",
                f.id);
        var files = new LinkedHashMap<>(source());
        files.putAll(runtime());
        f.workspace.complete(f.id, "one", files, "网站");
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenReturn(f.owner);
        var controller = new AppController();
        ReflectionTestUtils.setField(controller, "workspace", f.workspace);
        ReflectionTestUtils.setField(controller, "userService", users);
        var response =
                controller.downloadSource(
                        f.id, new org.springframework.mock.web.MockHttpServletRequest());
        Set<String> entries = new HashSet<>();
        try (var zip = new java.util.zip.ZipInputStream(response.getBody().getInputStream())) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) entries.add(e.getName());
        }
        assertEquals(source().keySet(), entries);
        assertFalse(entries.stream().anyMatch(e -> e.startsWith("dist/")));
        when(users.getLoginUser(any())).thenReturn(f.other);
        assertThrows(
                BusinessException.class,
                () ->
                        controller.downloadSource(
                                f.id, new org.springframework.mock.web.MockHttpServletRequest()));
    }

    @Test
    void buildUsesFixedIsolatedCommandsAndFailsWithoutDocker() throws Exception {
        var commands = new ArrayList<List<String>>();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        var builder =
                new VueProjectBuilder() {
                    @Override
                    protected Process start(List<String> command, Path log) {
                        commands.add(List.copyOf(command));
                        return process;
                    }

                    @Override
                    protected Map<String, String> collect(ProjectTask task) {
                        return runtime();
                    }
                };
        ReflectionTestUtils.setField(builder, "image", "node:20.19.0-bookworm-slim");
        try (var task = filled(new ArrayList<>())) {
            assertEquals(runtime(), builder.build(task));
            assertEquals(2, commands.size());
            for (var command : commands) {
                assertEquals(List.of("docker", "run"), command.subList(0, 2));
                assertTrue(
                        command.containsAll(
                                List.of(
                                        "--read-only",
                                        "--cap-drop",
                                        "ALL",
                                        "--security-opt",
                                        "no-new-privileges",
                                        "--memory",
                                        "512m")));
                assertTrue(
                        command.indexOf("--user") < command.indexOf("node:20.19.0-bookworm-slim"));
            }
            assertEquals("bridge", commands.get(0).get(commands.get(0).indexOf("--network") + 1));
            assertTrue(commands.get(0).contains("--ignore-scripts"));
            assertEquals("none", commands.get(1).get(commands.get(1).indexOf("--network") + 1));
            assertEquals(
                    List.of("npm", "run", "build"),
                    commands.get(1).subList(commands.get(1).size() - 3, commands.get(1).size()));
        }
        var missing =
                new VueProjectBuilder() {
                    @Override
                    protected Process start(List<String> command, Path log) throws IOException {
                        throw new IOException("no docker");
                    }
                };
        ReflectionTestUtils.setField(missing, "image", "node:20.19.0-bookworm-slim");
        try (var task = filled(new ArrayList<>())) {
            assertEquals(
                    "工程构建服务暂不可用",
                    assertThrows(BusinessException.class, () -> missing.build(task)).getMessage());
            assertThrows(BusinessException.class, task::result);
        }
    }

    @Test
    void collectorRejectsUnexpectedBuildFilesAndSymlinks() throws Exception {
        var builder =
                new VueProjectBuilder() {
                    Map<String, String> read(ProjectTask task) {
                        return collect(task);
                    }
                };
        try (var task = filled(new ArrayList<>())) {
            Path root = (Path) ReflectionTestUtils.getField(task, "root");
            Files.createDirectories(root.resolve("dist"));
            Files.writeString(root.resolve("dist/index.html"), "<h1>网站</h1>");
            Files.writeString(root.resolve("dist/secret.map"), "source");
            assertThrows(BusinessException.class, () -> builder.read(task));
            Files.delete(root.resolve("dist/secret.map"));
            assertEquals(1, builder.read(task).size());
            try {
                Files.createSymbolicLink(
                        root.resolve("dist/linked.txt"), root.resolve("package.json"));
            } catch (UnsupportedOperationException | IOException unavailable) {
                return;
            }
            assertThrows(BusinessException.class, () -> builder.read(task));
        }
    }

    @Test
    void nestedAssetHttpRoutesUseTheSameAccessChecks() throws Exception {
        var f = fixture();
        f.jdbc.update(
                "UPDATE app SET"
                    + " codeGenType='vue_project',generationStatus='GENERATING',generationToken='one'"
                    + " WHERE id=?",
                f.id);
        var files = new LinkedHashMap<>(source());
        files.putAll(runtime());
        f.workspace.complete(f.id, "one", files, "网站");
        f.workspace.deploy(f.id, f.owner);
        var user = new AtomicReference<>(f.owner);
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenAnswer(i -> user.get());
        var controller = new StaticResourceController();
        ReflectionTestUtils.setField(controller, "workspace", f.workspace);
        ReflectionTestUtils.setField(controller, "userService", users);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/static/" + f.id + "/assets/main.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(runtime().get("dist/assets/main.js")));
        mvc.perform(
                        get(
                                "/deploy/"
                                        + f.workspace.requireApp(f.id).getDeployKey()
                                        + "/assets/main.css"))
                .andExpect(status().isOk());
        user.set(f.other);
        mvc.perform(get("/static/" + f.id + "/assets/main.js")).andExpect(status().isNotFound());
        mvc.perform(get("/static/" + f.id + "/src/main.js")).andExpect(status().isNotFound());
        f.jdbc.update("UPDATE app SET visibility='PUBLIC' WHERE id=?", f.id);
        mvc.perform(get("/static/" + f.id + "/"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "data:text/javascript")));
    }

    @Test
    void progressIsSerializedAsAnActualNamedSseEvent() throws Exception {
        var users = mock(UserService.class);
        var owner = new com.yuelei.appforge.model.entity.User();
        owner.setId(100L);
        when(users.getLoginUser(any())).thenReturn(owner);
        var service = mock(com.yuelei.appforge.service.AppService.class);
        when(service.chatToGenCode(anyLong(), anyString(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            java.util.function.Consumer<GenerationProgress> progress =
                                    invocation.getArgument(3);
                            return reactor.core.publisher.Flux.defer(
                                    () -> {
                                        progress.accept(
                                                new GenerationProgress(
                                                        "file-1",
                                                        "write_file",
                                                        "src/App.vue",
                                                        "SUCCEEDED",
                                                        "已完成"));
                                        return reactor.core.publisher.Flux.just("网站已生成完成。");
                                    });
                        });
        var controller = new AppController();
        ReflectionTestUtils.setField(controller, "appService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var result =
                mvc.perform(get("/app/chat/gen/code").param("appId", "1").param("message", "创建网站"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String body =
                mvc.perform(asyncDispatch(result))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("event:progress"));
        assertTrue(body.contains("\"operation\":\"write_file\""));
        assertTrue(body.contains("\"path\":\"src/App.vue\""));
        int dataStart = body.indexOf("data:", body.indexOf("event:progress")) + 5;
        String payload = body.substring(dataStart).lines().findFirst().orElseThrow();
        assertEquals(
                Set.of("id", "operation", "path", "state", "message"),
                JSONUtil.parseObj(payload).keySet());
        assertTrue(body.contains("event:done"));
    }

    @Test
    void producesTrustedFixtureForNode18AndBrowserValidation() throws Exception {
        Path target = Path.of("target/vue-fixture");
        Files.createDirectories(target);
        for (var e : source().entrySet()) {
            Path p = target.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
        assertEquals(11, source().size());
        VueProjectFiles.validateSources(source());
    }
}
