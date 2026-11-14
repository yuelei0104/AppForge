package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yuelei.appforge.ai.AiCodeGenTypeRoutingService;
import com.yuelei.appforge.ai.AiCodeGenTypeRoutingServiceFactory;
import com.yuelei.appforge.ai.guardrail.*;
import com.yuelei.appforge.ai.model.message.AiResponseMessage;
import com.yuelei.appforge.core.handler.StreamHandlerExecutor;
import com.yuelei.appforge.model.dto.app.AppAddRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.impl.AppServiceImpl;
import com.yuelei.appforge.workspace.AppGenerationService;
import com.yuelei.appforge.workspace.GenerationProgress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class GuardrailAppServiceTest {
    static class Fixture {
        final App app = new App();
        final User user = new User();
        final AtomicReference<App> saved = new AtomicReference<>();
        final AppServiceImpl service =
                new AppServiceImpl() {
                    @Override
                    public App getById(java.io.Serializable id) {
                        return app;
                    }

                    @Override
                    public boolean save(App value) {
                        value.setId(99L);
                        saved.set(value);
                        return true;
                    }
                };
        final AppGenerationService generation = mock(AppGenerationService.class);
        final ChatHistoryService history = mock(ChatHistoryService.class);
        final AttachmentService attachments = mock(AttachmentService.class);
        final AiCodeGenTypeRoutingServiceFactory routing =
                mock(AiCodeGenTypeRoutingServiceFactory.class);

        Fixture(PromptReviewer reviewer) {
            app.setId(20L);
            app.setUserId(10L);
            app.setCodeGenType("html");
            user.setId(10L);
            var guard =
                    new PromptSafetyInputGuardrail(
                            new GuardrailProperties(), reviewer, new GuardrailAudit());
            ReflectionTestUtils.setField(service, "promptSafetyInputGuardrail", guard);
            ReflectionTestUtils.setField(service, "generation", generation);
            ReflectionTestUtils.setField(service, "chatHistoryService", history);
            ReflectionTestUtils.setField(service, "attachmentService", attachments);
            ReflectionTestUtils.setField(
                    service, "streamHandlerExecutor", new StreamHandlerExecutor());
            ReflectionTestUtils.setField(service, "aiCodeGenTypeRoutingServiceFactory", routing);
            when(attachments.requireOwned(anyList(), anyLong(), any())).thenReturn(List.of());
            when(attachments.buildPromptBlock(anyList())).thenReturn("<document>浅色布局</document>");
            when(history.appendChatMessage(anyLong(), anyString(), anyString(), anyLong()))
                    .thenReturn(1L);
            when(history.addChatMessage(anyLong(), anyString(), anyString(), anyLong()))
                    .thenReturn(true);
        }
    }

    private static PromptReviewer.Review allow() {
        return new PromptReviewer.Review(PromptReviewer.Action.ALLOW, List.of(), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"html", "multi_file", "vue_project"})
    void allGenerationModesReceiveOnlyReviewedMessageAttachmentsAndElement(String type) {
        String key = "sk-" + "unit1234".repeat(4);
        Fixture f =
                new Fixture(
                        (message, context) -> {
                            assertFalse(message.contains(key));
                            assertFalse(context.contains(key));
                            assertTrue(context.contains("<element_context>"));
                            assertTrue(context.contains("<document>浅色布局</document>"));
                            return allow();
                        });
        f.app.setCodeGenType(type);
        var sent = new AtomicReference<String>();
        when(f.generation.generate(any(), anyString(), any(Runnable.class), any(), any(), any()))
                .thenAnswer(
                        call ->
                                Flux.defer(
                                        () -> {
                                            sent.set(call.getArgument(1));
                                            ((Runnable) call.getArgument(2)).run();
                                            return Flux.just(
                                                    type.equals("vue_project")
                                                            ? cn.hutool.json.JSONUtil.toJsonStr(
                                                                    new AiResponseMessage("网站已完成"))
                                                            : "网站已完成");
                                        }));
        var progress = new ArrayList<GenerationProgress>();
        f.service
                .chatToGenCode(
                        20L,
                        "创建博客，示例凭据 " + key,
                        f.user,
                        List.of(),
                        "h1 标题，示例凭据 " + key,
                        progress::add,
                        event -> {})
                .blockLast(Duration.ofSeconds(5));
        assertNotNull(sent.get());
        assertFalse(sent.get().contains(key));
        assertEquals("guardrail", progress.getFirst().operation());
        verify(f.history)
                .appendChatMessage(
                        eq(20L),
                        argThat(text -> text.contains("创建博客") && !text.contains(key)),
                        eq("user"),
                        eq(10L));
        verify(f.generation)
                .afterConversationSaved(eq(20L), argThat(text -> !text.contains(key)), anyString());
    }

    @Test
    void blockedChatDoesNotClaimGenerationOrWriteHistory() {
        Fixture f =
                new Fixture(
                        (message, context) ->
                                new PromptReviewer.Review(
                                        PromptReviewer.Action.BLOCK,
                                        List.of(PromptReviewer.Rule.UNSAFE_CONTENT),
                                        List.of()));
        assertThrows(
                PromptGuardrailException.class,
                () ->
                        f.service
                                .chatToGenCode(20L, "隐晦有害请求", f.user)
                                .blockLast(Duration.ofSeconds(5)));
        verifyNoInteractions(f.generation, f.history);
    }

    @Test
    void createAppRoutesAndStoresTheSanitizedPromptRatherThanTheOriginalCredential() {
        Fixture f = new Fixture((message, context) -> allow());
        var route = mock(AiCodeGenTypeRoutingService.class);
        when(route.routeCodeGenType(anyString())).thenReturn(CodeGenTypeEnum.HTML);
        when(f.routing.createAiCodeGenTypeRoutingService()).thenReturn(route);
        String key = "sk-" + "unit5678".repeat(4);
        AppAddRequest body = new AppAddRequest();
        body.setInitPrompt("创建摄影博客，示例凭据 " + key);
        assertEquals(99L, f.service.createApp(body, f.user));
        assertFalse(f.saved.get().getInitPrompt().contains(key));
        assertTrue(f.saved.get().getInitPrompt().contains("[已移除敏感信息]"));
        verify(route).routeCodeGenType(f.saved.get().getInitPrompt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "html", "multi_file", "vue_project"})
    void blockedCreationDoesNotCallRouterOrSaveAnApplication(String mode) {
        Fixture f =
                new Fixture(
                        (message, context) ->
                                new PromptReviewer.Review(
                                        PromptReviewer.Action.BLOCK,
                                        List.of(PromptReviewer.Rule.UNSAFE_CONTENT),
                                        List.of()));
        AppAddRequest body = new AppAddRequest();
        body.setInitPrompt("隐晦有害请求");
        body.setCodeGenType(mode);
        assertThrows(PromptGuardrailException.class, () -> f.service.createApp(body, f.user));
        assertNull(f.saved.get());
        verifyNoInteractions(f.routing);
    }


    @ParameterizedTest
    @ValueSource(strings = {"html", "multi_file", "vue_project"})
    void explicitModeStillSanitizesPromptAndInitializesWorkspaceWithoutRouting(String mode) {
        Fixture f = new Fixture((message, context) -> allow());
        String key = "sk-" + "unit9012".repeat(4);
        AppAddRequest body = new AppAddRequest();
        body.setAppName("模式验证");
        body.setInitPrompt("创建摄影博客，示例凭据 " + key);
        body.setCodeGenType(mode);
        assertEquals(99L, f.service.createApp(body, f.user));
        App stored = f.saved.get();
        assertEquals(mode, stored.getCodeGenType());
        assertFalse(stored.getInitPrompt().contains(key));
        assertTrue(stored.getInitPrompt().contains("[已移除敏感信息]"));
        assertEquals("IDLE", stored.getGenerationStatus());
        assertEquals(0, stored.getCurrentVersion());
        assertEquals("PRIVATE", stored.getVisibility());
        verifyNoInteractions(f.routing);
        assertTrue(body.getInitPrompt().contains(key), "创建入口不应修改调用者的请求对象");
    }

    @Test
    void unsupportedExplicitModeIsRejectedRatherThanAutomaticallyReplaced() {
        Fixture f = new Fixture((message, context) -> allow());
        AppAddRequest body = new AppAddRequest();
        body.setAppName("模式验证");
        body.setInitPrompt("创建摄影博客");
        body.setCodeGenType("unsupported");
        var error =
                assertThrows(
                        com.yuelei.appforge.exception.BusinessException.class,
                        () -> f.service.createApp(body, f.user));
        assertEquals(
                com.yuelei.appforge.exception.ErrorCode.PARAMS_ERROR.getCode(), error.getCode());
        assertNull(f.saved.get());
        verifyNoInteractions(f.routing);
    }

    @Test
    void disconnectDuringReviewDoesNotStartGenerationAfterReviewerReturns() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        Fixture f =
                new Fixture(
                        (message, context) -> {
                            entered.countDown();
                            // Deliberately return even after interruption, as an external provider
                            // might do.
                            try {
                                release.await(3, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            return allow();
                        });
        var subscription = f.service.chatToGenCode(20L, "创建博客", f.user).subscribe();
        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
        subscription.dispose();
        release.countDown();
        verify(f.generation, after(300).never())
                .generate(any(), anyString(), any(Runnable.class), any(), any(), any());
        verifyNoInteractions(f.history);
    }
}
