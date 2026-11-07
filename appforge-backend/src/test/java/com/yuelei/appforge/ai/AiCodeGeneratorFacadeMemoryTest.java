package com.yuelei.appforge.ai;

import com.yuelei.appforge.core.AiCodeGeneratorFacade;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.workspace.CancellableCodeStream;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCodeGeneratorFacadeMemoryTest {

    @Test
    void remembersConversationPerApp() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        AiCodeGeneratorServiceFactory factory = mock(AiCodeGeneratorServiceFactory.class);
        when(factory.getChatMemory(1L)).thenReturn(memory);

        CancellableCodeStream stream = mock(CancellableCodeStream.class);
        when(stream.stream(anyString(), eq(CodeGenTypeEnum.MULTI_FILE)))
                .thenReturn(Flux.just("AI 回复"));

        AiCodeGeneratorFacade facade = new AiCodeGeneratorFacade();
        ReflectionTestUtils.setField(facade, "aiCodeGeneratorServiceFactory", factory);
        ReflectionTestUtils.setField(facade, "cancellableCodeStream", stream);

        facade.generateCodeStream("当前请求一", "用户原话一", CodeGenTypeEnum.MULTI_FILE, 1L)
                .blockLast();
        Assertions.assertEquals(2, memory.messages().size());
        Assertions.assertEquals("用户原话一", ((UserMessage) memory.messages().get(0)).singleText());
        Assertions.assertEquals("AI 回复", ((AiMessage) memory.messages().get(1)).text());

        facade.generateCodeStream("当前请求二", "用户原话二", CodeGenTypeEnum.MULTI_FILE, 1L)
                .blockLast();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(stream, times(2)).stream(prompt.capture(), eq(CodeGenTypeEnum.MULTI_FILE));
        Assertions.assertTrue(prompt.getAllValues().get(1).contains("用户原话一"));
        Assertions.assertTrue(prompt.getAllValues().get(1).contains("AI 回复"));
        Assertions.assertEquals(4, memory.messages().size());
    }
}
