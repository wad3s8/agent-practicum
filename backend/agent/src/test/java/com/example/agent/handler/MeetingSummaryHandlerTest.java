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
class MeetingSummaryHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Captor ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> messagesCaptor;

    MeetingSummaryHandler handler;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new MeetingSummaryHandler(chatClient);
    }

    @Test
    void handle_withEmptyHistory_returnsAiSummary() {
        when(callSpec.content()).thenReturn("## Встреча\n...");

        String result = handler.handle("Встреча 01.06 с командой", List.of());

        assertThat(result).isEqualTo("## Встреча\n...");
        verify(spec).messages(any(List.class));
    }

    @Test
    void handle_withHistory_passesAllMessagesToAi() {
        when(callSpec.content()).thenReturn("Саммари готово");

        Message userMsg = message("Заметки встречи", SenderType.USER);
        Message aiMsg = message("Ранний ответ", SenderType.SYSTEM);

        String result = handler.handle("Ещё заметки", List.of(userMsg, aiMsg));

        assertThat(result).isEqualTo("Саммари готово");
        // system prompt + 2 history + current = 4 messages
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(4);
    }

    @Test
    void handle_emptyHistory_includesSystemAndCurrentOnly() {
        when(callSpec.content()).thenReturn("Готово");

        handler.handle("Заметки", List.of());

        // system prompt + current user = 2 messages
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(2);
    }

    private Message message(String text, SenderType sender) {
        Message m = new Message();
        m.setText(text);
        m.setSender(sender);
        return m;
    }
}
