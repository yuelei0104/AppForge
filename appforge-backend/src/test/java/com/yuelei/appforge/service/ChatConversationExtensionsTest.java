package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.AiChatSummaryService;
import com.yuelei.appforge.config.ChatMemoryProperties;
import com.yuelei.appforge.controller.ChatHistoryController;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.service.impl.ChatMemoryServiceImpl;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 真实历史查询与 H2 持久化，模型由确定性桩替代。 */
class ChatConversationExtensionsTest {
    ChatHistoryIntegrationTest fixture;
    JdbcTemplate jdbc;
    ChatMemoryServiceImpl memory;
    ChatMemoryProperties properties;
    AtomicReference<AiChatSummaryService> model;
    long appId;

    @BeforeEach
    void setup() {
        fixture = new ChatHistoryIntegrationTest();
        fixture.setup();
        jdbc = fixture.jdbc;
        appId = fixture.appId;
        jdbc.execute("CREATE TABLE app(id bigint primary key,isDelete int default 0)");
        jdbc.update("INSERT INTO app(id) VALUES(?)", appId);
        jdbc.execute(
                "CREATE TABLE chat_memory_summary(appId bigint primary key,coveredThroughId bigint"
                    + " default 0,coveredMessages bigint default 0,summary longtext,revision bigint"
                    + " default 0,updatedTime timestamp)");
        properties = new ChatMemoryProperties();
        memory = new ChatMemoryServiceImpl();
        model = new AtomicReference<>(input -> "{\"summary\":\"保留博客目标和蓝色主题\"}");
        ReflectionTestUtils.setField(memory, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(memory, "properties", properties);
        ReflectionTestUtils.setField(memory, "chatHistoryService", fixture.service);
        ReflectionTestUtils.setField(
                memory, "summaryAi", (AiChatSummaryService) input -> model.get().summarize(input));
    }

    void history(int count) {
        for (int i = 0; i < count; i++) {
            fixture.insert(fixture.firstId + i, appId, fixture.time, 0);
            jdbc.update(
                    "UPDATE chat_history SET messageType=?,message=? WHERE id=?",
                    i % 2 == 0 ? "user" : "ai",
                    i % 2 == 0 ? "用户要求 " + i : "说明 " + i + "\n```html\n<h1>旧代码</h1>\n```",
                    fixture.firstId + i);
        }
    }

    void done() {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    while (memory.getStatus(appId, fixture.owner).isRunning()) Thread.sleep(10);
                });
    }

    @Test
    void statisticsAndExportReadAllPagesInStableOrderAndExcludeDeletedRows() throws Exception {
        history(55);
        jdbc.update("UPDATE chat_history SET isDelete=1 WHERE id=?", fixture.firstId + 54);
        fixture.insert(fixture.firstId + 100, appId + 1, fixture.time, 0);
        var stats = fixture.service.statistics(appId, fixture.owner);
        assertEquals(27, stats.getRoundCount());
        assertEquals(27, stats.getAiMessageCount());
        assertEquals(54, stats.getMessageCount());
        var out = new StringWriter();
        fixture.service.exportMarkdown(appId, fixture.admin, out);
        assertTrue(out.toString().contains("## 54. AI"));
        assertEquals(54, out.toString().split("(?m)^## ").length - 1);
        assertTrue(out.toString().indexOf("用户要求 0") < out.toString().indexOf("用户要求 52"));
        assertFalse(out.toString().contains("用户要求 54"));
        assertTrue(out.toString().contains("<h1>旧代码</h1>"));
    }

    @Test
    void emptyExportIsValidAndExtensionsShareHistoryAccessChecks() throws Exception {
        var out = new StringWriter();
        fixture.service.exportMarkdown(appId, fixture.owner, out);
        assertTrue(out.toString().contains("暂无对话记录"));
        assertEquals(0, fixture.service.statistics(appId, fixture.owner).getRoundCount());
        assertThrows(
                BusinessException.class,
                () -> fixture.service.exportMarkdown(appId, fixture.other, new StringWriter()));
        assertThrows(
                BusinessException.class, () -> fixture.service.statistics(appId, fixture.other));
        assertThrows(BusinessException.class, () -> memory.getStatus(appId, fixture.other));
        assertThrows(BusinessException.class, () -> memory.compress(appId, fixture.other));
    }

    @Test
    void summaryKeepsFullHistoryAndRecentWindowWithoutDuplicatingCurrentRequest() {
        history(30);
        var input = new AtomicReference<String>();
        model.set(
                text -> {
                    input.set(text);
                    return "{\"summary\":\"博客目标和蓝色主题\"}";
                });
        memory.compress(appId, fixture.owner);
        done();
        var state = memory.getStatus(appId, fixture.owner);
        assertEquals(22, state.getCoveredMessages());
        assertNull(state.getLastError());
        assertEquals(30, fixture.service.statistics(appId, fixture.owner).getMessageCount());
        assertEquals(22, JSONUtil.parseObj(input.get()).getJSONArray("messages").size());
        assertFalse(input.get().contains("<h1>旧代码</h1>"));
        String prompt = memory.buildPrompt(appId, fixture.firstId + 28, "本轮新增任务");
        assertTrue(prompt.contains("博客目标和蓝色主题"));
        assertTrue(prompt.contains("用户要求 26"));
        assertFalse(prompt.contains("用户要求 28"));
        assertFalse(prompt.contains("说明 29"));
        assertNull(memory.buildPrompt(appId + 1, 0, "其他应用"));
    }

    @Test
    void automaticSummaryRespectsThresholdAndDisabledConfiguration() {
        history(18);
        memory.afterConversationSaved(appId);
        assertFalse(memory.getStatus(appId, fixture.owner).isRunning());
        fixture.insert(fixture.firstId + 18, appId, fixture.time, 0);
        fixture.insert(fixture.firstId + 19, appId, fixture.time, 0);
        memory.afterConversationSaved(appId);
        done();
        assertEquals(12, memory.getStatus(appId, fixture.owner).getCoveredMessages());
        properties.setEnabled(false);
        assertNull(memory.buildPrompt(appId, 0, "请求"));
        assertThrows(BusinessException.class, () -> memory.compress(appId, fixture.owner));
    }

    @Test
    void concurrentManualRequestsRunOneTaskAndInvalidationRejectsItsLateResult() throws Exception {
        history(30);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        model.set(
                input -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return "{\"summary\":\"过时摘要\"}";
                });
        memory.compress(appId, fixture.owner);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            memory.compress(appId, fixture.admin);
            assertEquals(1, calls.get());
            jdbc.update("UPDATE chat_history SET isDelete=1 WHERE id=?", fixture.firstId);
            memory.invalidate(appId);
        } finally {
            release.countDown();
        }
        done();
        assertTrue(memory.getStatus(appId, fixture.owner).getSummary().isBlank());
        assertFalse(memory.buildPrompt(appId, 0, "请求").contains("用户要求 0"));
        assertEquals(29, fixture.service.statistics(appId, fixture.owner).getMessageCount());
    }

    @Test
    void failedSummaryPreservesPreviousSummaryAndHistoryAndCanBeRetried() {
        history(30);
        memory.compress(appId, fixture.owner);
        done();
        var before = memory.getStatus(appId, fixture.owner);
        for (int i = 30; i < 34; i++) fixture.insert(fixture.firstId + i, appId, fixture.time, 0);
        model.set(input -> "not json");
        memory.compress(appId, fixture.owner);
        done();
        var failed = memory.getStatus(appId, fixture.owner);
        assertEquals(before.getSummary(), failed.getSummary());
        assertEquals(before.getCoveredMessages(), failed.getCoveredMessages());
        assertNotNull(failed.getLastError());
        assertEquals(34, fixture.service.statistics(appId, fixture.owner).getMessageCount());
        model.set(input -> "{\"summary\":\"重试成功\"}");
        memory.compress(appId, fixture.owner);
        done();
        assertEquals("重试成功", memory.getStatus(appId, fixture.owner).getSummary());
        assertNull(memory.getStatus(appId, fixture.owner).getLastError());
    }

    @Test
    void generationBusyAndInsufficientHistoryCannotStartManualSummary() {
        assertThrows(BusinessException.class, () -> memory.compress(appId, fixture.owner));
        history(30);
        fixture.app.setGenerationStatus("GENERATING");
        assertThrows(BusinessException.class, () -> memory.compress(appId, fixture.owner));
        assertFalse(memory.getStatus(appId, fixture.owner).isRunning());
    }

    @Test
    void deletingAppDuringModelCallPreventsSummaryRecreation() throws Exception {
        history(30);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        model.set(
                input -> {
                    entered.countDown();
                    await(release);
                    return "{\"summary\":\"迟到摘要\"}";
                });
        memory.compress(appId, fixture.owner);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            jdbc.update("UPDATE app SET isDelete=1 WHERE id=?", appId);
            jdbc.update("DELETE FROM chat_memory_summary WHERE appId=?", appId);
        } finally {
            release.countDown();
        }
        done();
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_memory_summary", Integer.class));
        memory.invalidate(appId);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_memory_summary", Integer.class));
    }

    @Test
    void exportHttpReturnsUtf8AttachmentAndStatsReusesAccessChecks() throws Exception {
        history(4);
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenReturn(fixture.owner);
        var controller = new ChatHistoryController();
        ReflectionTestUtils.setField(controller, "chatHistoryService", fixture.service);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "chatMemoryService", memory);
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(get("/chatHistory/app/" + appId + "/stats"))
                .andExpect(jsonPath("$.data.roundCount").value(2));
        var pending =
                mvc.perform(get("/chatHistory/app/" + appId + "/export"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(pending))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "attachment; filename=app-" + appId + "-chat.md"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertTrue(
                pending.getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                        .contains("用户要求 0"));
        when(users.getLoginUser(any())).thenReturn(fixture.other);
        mvc.perform(get("/chatHistory/app/" + appId + "/export"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
    }

    @Test
    void serializedHistoryBudgetBoundsSummaryRequestsAndSubsequentContext() {
        history(30);
        properties.setMaxInputCharacters(10000);
        properties.setMaxSummaryCharacters(1000);
        jdbc.update(
                "UPDATE chat_history SET messageType='user',message=? WHERE appId=?",
                "\"".repeat(2000),
                appId);
        var sizes = new CopyOnWriteArrayList<Integer>();
        model.set(
                input -> {
                    sizes.add(input.length());
                    return "{\"summary\":\"保留已确认的需求\"}";
                });
        memory.compress(appId, fixture.owner);
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    while (memory.getStatus(appId, fixture.owner).getCoveredMessages() < 12
                            || memory.getStatus(appId, fixture.owner).isRunning()) Thread.sleep(10);
                });
        assertTrue(sizes.size() > 1);
        assertTrue(sizes.stream().allMatch(size -> size <= 10000));
        String prompt = memory.buildPrompt(appId, 0, "当前请求");
        String json = prompt.substring(prompt.indexOf('{'), prompt.indexOf("\n\n用户本轮要求"));
        assertTrue(json.length() <= 10000);
        assertEquals(30, fixture.service.statistics(appId, fixture.owner).getMessageCount());
    }

    @Test
    void timeoutPreservesSummaryAndDiscardsLateUninterruptibleResult() throws Exception {
        history(30);
        memory.compress(appId, fixture.owner);
        done();
        var previous = memory.getStatus(appId, fixture.owner);
        for (int i = 30; i < 34; i++) fixture.insert(fixture.firstId + i, appId, fixture.time, 0);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        model.set(
                input -> {
                    entered.countDown();
                    while (release.getCount() > 0)
                        try {
                            release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    returned.countDown();
                    return "{\"summary\":\"超时后的迟到结果\"}";
                });
        memory.compress(appId, fixture.owner);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(30),
                    () -> {
                        while (memory.getStatus(appId, fixture.owner).isRunning()) Thread.sleep(20);
                    });
            assertNotNull(memory.getStatus(appId, fixture.owner).getLastError());
        } finally {
            release.countDown();
        }
        assertTrue(returned.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(previous.getSummary(), memory.getStatus(appId, fixture.owner).getSummary());
        assertEquals(
                previous.getCoveredMessages(),
                memory.getStatus(appId, fixture.owner).getCoveredMessages());
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("test latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
