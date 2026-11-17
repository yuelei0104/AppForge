package com.yuelei.appforge.config;

import com.mybatisflex.core.paginate.Page;
import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.model.vo.AppVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RedisCacheManagerConfigTest {

    @Test
    void cacheSerializerKeepsResponseAndPageTypes() {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(RedisCacheManagerConfig.cacheObjectMapper());

        Page<AppVO> page = new Page<>(1, 10);
        page.setRecords(List.of(new AppVO()));
        BaseResponse<Page<AppVO>> response = new BaseResponse<>(0, page, "");

        Object restored = serializer.deserialize(serializer.serialize(response));

        BaseResponse<?> restoredResponse = assertInstanceOf(BaseResponse.class, restored);
        Page<?> restoredPage = assertInstanceOf(Page.class, restoredResponse.getData());
        assertEquals(1, restoredPage.getRecords().size());
        assertInstanceOf(AppVO.class, restoredPage.getRecords().get(0));
    }
}
