package com.yuelei.appforge.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.utils.SpringContextUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证路由服务在并发调用时会为每次请求获取新的 prototype ChatModel。
 *
 * <p>不依赖真实 DeepSeek 网络：用 mock ChatModel 返回固定枚举 JSON，重点覆盖
 * {@link AiCodeGenTypeRoutingServiceFactory} 的并发创建路径。</p>
 */
@Slf4j
class AiConcurrentTest {

    @AfterEach
    void clearContext() {
        ReflectionTestUtils.setField(SpringContextUtil.class, "applicationContext", null);
    }

    @Test
    void testConcurrentRoutingCalls() throws InterruptedException {
        String[] prompts = {
            "做一个简单的HTML页面", "做一个多页面网站项目", "做一个Vue管理系统"
        };

        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(context.getBean("routingChatModelPrototype", ChatModel.class))
                .thenAnswer(invocation -> chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder()
                                .aiMessage(AiMessage.from("{\"value\":\"HTML\"}"))
                                .build());
        ReflectionTestUtils.setField(SpringContextUtil.class, "applicationContext", context);

        AiCodeGenTypeRoutingServiceFactory factory = new AiCodeGenTypeRoutingServiceFactory();
        AtomicInteger failures = new AtomicInteger();
        Thread[] threads = new Thread[prompts.length];

        for (int i = 0; i < prompts.length; i++) {
            final int index = i + 1;
            final String prompt = prompts[i];
            threads[i] =
                    Thread.ofVirtual()
                            .start(
                                    () -> {
                                        try {
                                            AiCodeGenTypeRoutingService service =
                                                    factory.createAiCodeGenTypeRoutingService();
                                            CodeGenTypeEnum result =
                                                    service.routeCodeGenType(prompt);
                                            log.info(
                                                    "线程 {}: {} -> {}",
                                                    index,
                                                    prompt,
                                                    result.getValue());
                                        } catch (Throwable e) {
                                            failures.incrementAndGet();
                                            log.error("线程 {} 并发路由失败", index, e);
                                        }
                                    });
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(0, failures.get(), "并发路由调用不应有失败");
        verify(context, times(prompts.length))
                .getBean("routingChatModelPrototype", ChatModel.class);
    }
}
