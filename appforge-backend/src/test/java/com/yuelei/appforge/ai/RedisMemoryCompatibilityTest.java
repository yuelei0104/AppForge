package com.yuelei.appforge.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;

class RedisMemoryCompatibilityTest {
    @Test
    void realRedisMemoryImplementationUpdatesMessagesWithTtlUsingResolvedJedisApi() {
        // Exercise the library implementation that failed in production; mock only Redis I/O.
        var client = mock(UnifiedJedis.class);
        var pipeline = mock(AbstractPipeline.class);
        @SuppressWarnings("unchecked")
        Response<String> response = mock(Response.class);
        when(client.pipelined()).thenReturn(pipeline);
        when(pipeline.jsonSet(anyString(), anyString())).thenReturn(response);
        when(response.get()).thenReturn("OK");

        var store =
                RedisChatMemoryStore.builder()
                        .host("localhost")
                        .port(6379)
                        .prefix("chat:")
                        .ttl(60L)
                        .build();
        try (var originalClient = (UnifiedJedis) ReflectionTestUtils.getField(store, "client")) {
            Object operations = ReflectionTestUtils.getField(store, "redisOperations");
            ReflectionTestUtils.setField(operations, "client", client);
            var messages = List.of(UserMessage.from("修改选中的标题"));
            String json = ChatMessageSerializer.messagesToJson(List.copyOf(messages));

            assertDoesNotThrow(() -> store.updateMessages(123L, List.copyOf(messages)));
            verify(client).pipelined();
            verify(pipeline).jsonSet("chat:123", json);
            verify(pipeline).expire("chat:123", 60L);
            verify(pipeline).sync();
            verify(pipeline).close();
        }
    }
}
