package com.example.agent.handler;

import com.example.agent.client.ConfluenceClient;
import com.example.agent.dto.confluence.ConfluenceBodyDto;
import com.example.agent.dto.confluence.ConfluencePageDto;
import com.example.agent.dto.confluence.ConfluenceSearchResponse;
import com.example.agent.dto.confluence.ConfluenceSpaceDto;
import com.example.agent.dto.confluence.ConfluenceStorageDto;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConferenceInfoHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ConfluenceClient confluenceClient;
    @Captor ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> messagesCaptor;

    ConferenceInfoHandler handler;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new ConferenceInfoHandler(chatClient, confluenceClient);
    }

    @Test
    void handle_pageSelected_returnsAnswer() {
        // getAllPages returns list of pages (titles only)
        when(confluenceClient.getAllPages("page", 50))
                .thenReturn(new ConfluenceSearchResponse(List.of(stubPage("111", "Политика отпусков", null)), 1));
        // LLM selects page id
        when(callSpec.content())
                .thenReturn("111")                          // page selection
                .thenReturn("Отпуск составляет 28 дней."); // answer
        // getPage returns full content
        when(confluenceClient.getPage("111", "body.storage,space"))
                .thenReturn(stubPage("111", "Политика отпусков", "28 дней оплачиваемого отпуска"));

        String result = handler.handle("Сколько дней отпуска?", List.of());

        assertThat(result).isEqualTo("Отпуск составляет 28 дней.");
        verify(confluenceClient).getPage("111", "body.storage,space");
    }

    @Test
    void handle_noPagesInConfluence_returnsEmptyMessage() {
        when(confluenceClient.getAllPages("page", 50))
                .thenReturn(new ConfluenceSearchResponse(List.of(), 0));

        String result = handler.handle("Вопрос", List.of());

        assertThat(result).containsIgnoringCase("нет доступных страниц");
    }

    @Test
    void handle_llmSelectsNull_returnsNotFoundMessage() {
        when(confluenceClient.getAllPages("page", 50))
                .thenReturn(new ConfluenceSearchResponse(List.of(stubPage("1", "Случайная страница", null)), 1));
        when(callSpec.content()).thenReturn("null");

        String result = handler.handle("Вопрос без ответа", List.of());

        assertThat(result).containsIgnoringCase("не удалось найти");
    }

    @Test
    void handle_confluenceThrows_returnsErrorMessage() {
        when(confluenceClient.getAllPages(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Confluence недоступен"));

        String result = handler.handle("Вопрос", List.of());

        assertThat(result).containsIgnoringCase("ошибка");
    }

    @Test
    void handle_withHistory_includesItInAnswerCall() {
        when(confluenceClient.getAllPages("page", 50))
                .thenReturn(new ConfluenceSearchResponse(List.of(stubPage("42", "Настройка окружения", null)), 1));
        when(callSpec.content())
                .thenReturn("42")
                .thenReturn("Нужна Java 21.");
        when(confluenceClient.getPage("42", "body.storage,space"))
                .thenReturn(stubPage("42", "Настройка окружения", "Установи Java 21 и Docker."));

        Message prev = message("Предыдущий вопрос", SenderType.USER);
        handler.handle("Как настроить?", List.of(prev));

        // system + 1 history + current = 3 messages
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(3);
    }

    private ConfluencePageDto stubPage(String id, String title, String content) {
        ConfluenceBodyDto body = content != null
                ? new ConfluenceBodyDto(new ConfluenceStorageDto("<p>" + content + "</p>"))
                : null;
        return new ConfluencePageDto(id, title, body, new ConfluenceSpaceDto("TEST", "Test Space"));
    }

    private Message message(String text, SenderType sender) {
        Message m = new Message();
        m.setText(text);
        m.setSender(sender);
        return m;
    }
}
