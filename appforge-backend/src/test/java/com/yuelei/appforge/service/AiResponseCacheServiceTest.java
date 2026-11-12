package com.yuelei.appforge.service;

import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiResponseCacheServiceTest {

    @Test
    void normalizeRemovesWhitespaceAndPunctuation() {
        assertEquals("你好世界", AiResponseCacheService.normalize(" 你好，世界！ "));
        assertEquals("生成一个登录页面", AiResponseCacheService.normalize("生成一个 登录页面。"));
    }

    @Test
    void similarityMatchesNearIdenticalPromptsAndRejectsDifferentOnes() {
        double identical =
                AiResponseCacheService.similarity(
                        AiResponseCacheService.normalize("生成一个登录页面"),
                        AiResponseCacheService.normalize("生成一个登录页面"));
        assertEquals(1D, identical);

        double near =
                AiResponseCacheService.similarity(
                        AiResponseCacheService.normalize("生成一个登录页面"),
                        AiResponseCacheService.normalize("请生成一个登录页面"));
        assertTrue(near > 0.75D, "near prompt similarity=" + near);

        double different =
                AiResponseCacheService.similarity(
                        AiResponseCacheService.normalize("生成一个登录页面"),
                        AiResponseCacheService.normalize("生成一个摄影作品展示网站"));
        assertTrue(different < 0.5D, "different prompt similarity=" + different);
    }

    @Test
    void shortTextsOnlyMatchExactly() {
        assertEquals(1D, AiResponseCacheService.similarity("你好", "你好"));
        assertEquals(0D, AiResponseCacheService.similarity("你好", "您好"));
        assertEquals(0D, AiResponseCacheService.similarity("登录", "登录页面"));
    }

    @Test
    void exactValuesRoundTripThroughRedisPayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> lists = mock(ListOperations.class);
        Map<String, String> store = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(values.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(
                        invocation -> {
                            store.put(invocation.getArgument(0), invocation.getArgument(1));
                            return null;
                        })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        when(lists.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        AiResponseCacheService service = new AiResponseCacheService();
        ReflectionTestUtils.setField(service, "redis", redis);

        service.putRoute("生成一个登录页面", CodeGenTypeEnum.HTML);
        assertEquals(CodeGenTypeEnum.HTML, service.findRoute("生成一个登录页面"));

        Map<String, String> files = Map.of("index.html", "<h1>登录</h1>");
        service.putGeneration("html", "生成一个登录页面", files);
        Map<String, String> cached = service.findGeneration("html", "生成一个登录页面");
        assertNotNull(cached);
        assertEquals(files, cached);
        assertEquals(files, service.findGeneration("html", " 生成一个登录页面！ "));
        assertNull(service.findGeneration("vue_project", "生成一个登录页面"));
    }
}
