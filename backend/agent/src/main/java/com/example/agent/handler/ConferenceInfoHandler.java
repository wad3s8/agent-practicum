package com.example.agent.handler;

import com.example.agent.client.ConfluenceClient;
import com.example.agent.dto.confluence.ConfluencePageDto;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceInfoHandler {

    private final ChatClient chatClient;
    private final ConfluenceClient confluenceClient;

    private static final int MAX_PAGES = 5;
    private static final int MAX_PAGE_CHARS = 3000;

    private static final String CQL_PROMPT = """
            Преобразуй вопрос пользователя в CQL-запрос для поиска в Confluence.
            Ответь ТОЛЬКО CQL-строкой без кавычек и без markdown.
            Используй формат: text ~ "ключевые слова" AND type = page
            Пример: вопрос "политика отпусков" -> text ~ "политика отпусков" AND type = page
            """;

    private static final String ANSWER_PROMPT = """
            Ты — ассистент по корпоративной базе знаний Confluence.
            На основе найденных страниц из Confluence ответь на вопрос пользователя.
            Если информация найдена — ответь чётко и по делу, сославшись на название страницы.
            Если информации недостаточно — так и скажи.
            Отвечай на русском языке. Используй Markdown.
            """;

    public String handle(String userText, List<Message> history) {
        String cql = generateCql(userText);
        log.debug("Confluence CQL: {}", cql);

        List<ConfluencePageDto> pages = searchConfluence(cql);
        if (pages == null) {
            return "Ошибка при обращении к Confluence. Попробуйте позже.";
        }
        if (pages.isEmpty()) {
            return "В Confluence не найдено страниц по вашему запросу. Попробуйте переформулировать вопрос.";
        }

        return answerWithContext(userText, pages, history);
    }

    private String generateCql(String userText) {
        try {
            return chatClient.prompt()
                    .system(CQL_PROMPT)
                    .user(userText)
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("CQL generation failed: {}", e.getMessage());
            return "text ~ \"" + userText + "\" AND type = page";
        }
    }

    private List<ConfluencePageDto> searchConfluence(String cql) {
        try {
            var response = confluenceClient.search(cql, "body.storage,space", MAX_PAGES);
            return response.results() != null ? response.results() : List.of();
        } catch (Exception e) {
            log.error("Confluence search failed for CQL '{}': {}", cql, e.getMessage());
            return null;
        }
    }

    private String answerWithContext(String userText, List<ConfluencePageDto> pages, List<Message> history) {
        String pagesText = buildPagesContext(pages);

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ANSWER_PROMPT));
        history.forEach(msg -> messages.add(
                msg.getSender() == SenderType.USER
                        ? new UserMessage(msg.getText())
                        : new AssistantMessage(msg.getText())
        ));
        messages.add(new UserMessage("Вопрос: " + userText + "\n\nСтраницы из Confluence:\n" + pagesText));

        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }

    private String buildPagesContext(List<ConfluencePageDto> pages) {
        StringBuilder sb = new StringBuilder();
        for (ConfluencePageDto page : pages) {
            sb.append("### ").append(page.title());
            if (page.space() != null) {
                sb.append(" (").append(page.space().name()).append(")");
            }
            sb.append("\n");

            if (page.body() != null && page.body().storage() != null) {
                String text = stripHtml(page.body().storage().value());
                if (text.length() > MAX_PAGE_CHARS) {
                    text = text.substring(0, MAX_PAGE_CHARS) + "...";
                }
                sb.append(text);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
