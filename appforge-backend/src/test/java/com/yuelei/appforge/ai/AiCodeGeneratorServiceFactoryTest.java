package com.yuelei.appforge.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.yuelei.appforge.service.ChatHistoryService;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

class AiCodeGeneratorServiceFactoryTest {
    AiCodeGeneratorServiceFactory factory;
    ChatHistoryService history;
    Cache<Long, ?> cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        factory = new AiCodeGeneratorServiceFactory();
        history = mock(ChatHistoryService.class);
        var store = mock(RedisChatMemoryStore.class);
        var messages = new ConcurrentHashMap<Object, List<ChatMessage>>();
        when(store.getMessages(any()))
                .thenAnswer(i -> messages.getOrDefault(i.getArgument(0), List.of()));
        doAnswer(
                        i -> {
                            messages.put(i.getArgument(0), List.copyOf(i.getArgument(1)));
                            return null;
                        })
                .when(store)
                .updateMessages(any(), anyList());
        doAnswer(
                        i -> {
                            messages.remove(i.getArgument(0));
                            return null;
                        })
                .when(store)
                .deleteMessages(any());
        var model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder()
                                .aiMessage(AiMessage.from("{\"name\":\"测试应用\"}"))
                                .build());
        ReflectionTestUtils.setField(factory, "chatModel", model);
        ReflectionTestUtils.setField(factory, "streamingChatModel", mock(StreamingChatModel.class));
        ReflectionTestUtils.setField(factory, "redisChatMemoryStore", store);
        ReflectionTestUtils.setField(factory, "chatHistoryService", history);
        cache = (Cache<Long, ?>) ReflectionTestUtils.getField(factory, "serviceCache");
    }

    @Test
    void serviceUsesTheSameMemoryReturnedForItsApp() {
        var service = factory.getAiCodeGeneratorService(1);
        var memory = factory.getChatMemory(1);
        assertSame(service, factory.getAiCodeGeneratorService(1));
        assertSame(memory, factory.getChatMemory(1));
        service.generateAppName("应用一的需求");
        assertTrue(
                memory.messages().stream()
                        .anyMatch(
                                m ->
                                        m instanceof UserMessage u
                                                && u.singleText().equals("应用一的需求")));
        assertFalse(
                factory.getChatMemory(2).messages().stream()
                        .anyMatch(m -> m instanceof UserMessage));
    }

    @Test
    void delayedEvictionCannotRemoveTheReplacementMemory() {
        MessageWindowChatMemory previous = factory.getChatMemory(7);
        for (int i = 0; i < 50; i++) {
            cache.invalidate(7L);
            var current = factory.getChatMemory(7);
            cache.cleanUp();
            ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);
            assertNotNull(current);
            assertNotSame(previous, current);
            assertSame(current, factory.getChatMemory(7));
            previous = current;
        }
    }

    @Test
    void concurrentLookupsAndInvalidationsNeverReturnNullMemory() throws Exception {
        var pool = Executors.newFixedThreadPool(5);
        var start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    for (int i = 0; i < 50; i++) {
                                        assertNotNull(factory.getChatMemory(9));
                                        assertNotNull(factory.getAiCodeGeneratorService(9));
                                    }
                                    return null;
                                }));
            jobs.add(
                    pool.submit(
                            () -> {
                                start.await();
                                for (int i = 0; i < 100; i++) {
                                    cache.invalidate(9L);
                                    cache.cleanUp();
                                }
                                return null;
                            }));
            start.countDown();
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refreshAlwaysReceivesTheCurrentNonNullMemory() {
        factory.getChatMemory(11);
        cache.invalidate(11L);
        factory.refreshChatMemory(11);
        verify(history)
                .loadChatHistoryToMemory(
                        eq(11L), same(factory.getChatMemory(11)), eq(20), eq(false));
        assertNotNull(factory.aiCodeGeneratorService());
        assertNotNull(factory.getChatMemory(0));
    }
}
