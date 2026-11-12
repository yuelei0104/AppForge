package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.enums.ChatHistoryMessageTypeEnum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ChatIntentServiceTest {
    @Mock private ChatHistoryService chatHistoryService;

    private ChatIntentService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ChatIntentService(chatHistoryService);
        user = new User();
        user.setId(42L);
    }

    @Test
    void recognizesPureGreetingsAndCapabilityQuestions() {
        assertTrue(service.isSmallTalk("你好", List.of(), null));
        assertTrue(service.isSmallTalk("\uFEFF你好", List.of(), null));
        assertTrue(service.isSmallTalk("你好！", List.of(), null));
        assertTrue(service.isSmallTalk("在吗", List.of(), null));
        assertTrue(service.isSmallTalk("谢谢", List.of(), null));
        assertTrue(service.isSmallTalk("你能做什么", List.of(), null));
        assertTrue(service.isSmallTalk("你好，有什么帮助嘛", List.of(), null));
    }

    @Test
    void doesNotTreatConcreteChangeRequestsAsSmallTalk() {
        assertFalse(service.isSmallTalk("你好，帮我做一个报名页面", List.of(), null));
        assertFalse(service.isSmallTalk("把按钮颜色改成蓝色", List.of(), null));
        assertFalse(service.isSmallTalk("生成一个登录页", List.of(), null));
        assertFalse(service.isSmallTalk("你好", List.of(1L), null));
        assertFalse(service.isSmallTalk("你好", List.of(), "<button>按钮</button>"));
    }

    @Test
    void replyRecordsUserAndAssistantMessages() {
        when(chatHistoryService.appendChatMessage(
                        anyLong(),
                        anyString(),
                        eq(ChatHistoryMessageTypeEnum.USER.getValue()),
                        eq(42L)))
                .thenReturn(100L);
        when(chatHistoryService.addChatMessage(
                        anyLong(),
                        anyString(),
                        eq(ChatHistoryMessageTypeEnum.AI.getValue()),
                        eq(42L)))
                .thenReturn(true);

        List<String> chunks = service.reply(7L, "你好", user).collectList().block();

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).startsWith("你好！"));
        verify(chatHistoryService)
                .appendChatMessage(
                        7L, "你好", ChatHistoryMessageTypeEnum.USER.getValue(), 42L);
        verify(chatHistoryService)
                .addChatMessage(
                        7L,
                        chunks.get(0),
                        ChatHistoryMessageTypeEnum.AI.getValue(),
                        42L);
    }
}
