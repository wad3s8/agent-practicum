package com.example.agent.handler;

import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenceInfoHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Captor ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> messagesCaptor;

    ConferenceInfoHandler handler;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new ConferenceInfoHandler(chatClient);
    }

    @Test
    void handle_withTranscript_returnsExtractedInfo() {
        when(callSpec.content()).thenReturn("## Ключевые договорённости\n- Сдать отчёт до 10.06");

        String result = handler.handle("Транскрипт: ... обсудили дедлайны ...", List.of());

        assertThat(result).contains("Ключевые договорённости");
    }

    @Test
    void handle_withHistory_includesContextInMessages() {
        when(callSpec.content()).thenReturn("Ответ по конфе");

        Message prev = message("Предыдущий вопрос", SenderType.USER);

        handler.handle("Что решили по бюджету?", List.of(prev));

        // system + 1 history + current = 3 messages
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(3);
    }

    @Test
    void handle_emptyHistory_includesSystemAndUserOnly() {
        when(callSpec.content()).thenReturn("Данные по конфе");

        handler.handle("Кто выступал?", List.of());

        // system + current = 2 messages
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(2);
    }

    @Test
    void handle_aiSystemMessageIsInHistory_addedAsAssistantMessage() {
        when(callSpec.content()).thenReturn("Ответ");

        Message userMsg = message("Вопрос", SenderType.USER);
        Message aiMsg = message("Ответ ИИ", SenderType.SYSTEM);

        handler.handle("Ещё вопрос", List.of(userMsg, aiMsg));

        verify(spec).messages(messagesCaptor.capture());
        List<org.springframework.ai.chat.messages.Message> msgs = messagesCaptor.getValue();
        // system prompt + USER + ASSISTANT + current user = 4
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(2)).isInstanceOf(org.springframework.ai.chat.messages.AssistantMessage.class);
    }

    private Message message(String text, SenderType sender) {
        Message m = new Message();
        m.setText(text);
        m.setSender(sender);
        return m;
    }
}
