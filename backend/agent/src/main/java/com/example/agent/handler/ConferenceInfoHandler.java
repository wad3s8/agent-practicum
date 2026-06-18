package com.example.agent.handler;

import com.example.agent.client.ConfluenceClient;
import com.example.agent.dto.confluence.ConfluencePageDto;
import com.example.agent.dto.confluence.ConfluenceSearchResponse;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceInfoHandler {

    private final ChatClient chatClient;
    private final ConfluenceClient confluenceClient;

    private static final int MAX_PAGE_CHARS = 4000;

    private static final String SELECT_PROMPT = """
            Вот список страниц из Confluence (номер. ID | Заголовок):
            %s

            Вопрос пользователя: "%s"

            Выбери ID страницы, которая наиболее вероятно содержит ответ на вопрос.
            Ответь ТОЛЬКО числом ID страницы. Если ни одна не подходит — ответь: null
            """;

    private static final String ANSWER_PROMPT = """
            Ты — ассистент по корпоративной базе знаний Confluence.
            На основе содержимого страницы ответь на вопрос пользователя чётко и по делу.
            Если информации недостаточно — так и скажи.
            Отвечай на русском языке. Используй Markdown.
            """;

    public String handle(String userText, List<Message> history) {
        List<ConfluencePageDto> allPages = fetchAllPages();
        if (allPages == null) {
            return "Ошибка при обращении к Confluence. Попробуйте позже.";
        }
        if (allPages.isEmpty()) {
            return "В Confluence нет доступных страниц.";
        }

        String selectedId = selectBestPage(userText, allPages);
        if (selectedId == null || selectedId.equalsIgnoreCase("null")) {
            return "Не удалось найти подходящую страницу в Confluence по вашему вопросу.";
        }

        ConfluencePageDto page = fetchPageContent(selectedId);
        if (page == null) {
            return "Ошибка при загрузке страницы из Confluence.";
        }

        log.debug("Selected Confluence page: {} (id={})", page.title(), selectedId);
        return answerFromPage(userText, page, history);
    }

    private List<ConfluencePageDto> fetchAllPages() {
        try {
            ConfluenceSearchResponse response = confluenceClient.getAllPages("page", 50);
            List<ConfluencePageDto> pages = response.results() != null ? response.results() : List.of();
            log.info("Confluence pages fetched ({}): {}", pages.size(),
                    pages.stream().map(p -> p.id() + " | " + p.title()).toList());
            return pages;
        } catch (Exception e) {
            log.error("Failed to fetch Confluence pages: {}", e.getMessage());
            return null;
        }
    }

    private String selectBestPage(String userText, List<ConfluencePageDto> pages) {
        String pageList = IntStream.range(0, pages.size())
                .mapToObj(i -> (i + 1) + ". " + pages.get(i).id() + " | " + pages.get(i).title())
                .collect(Collectors.joining("\n"));

        try {
            return chatClient.prompt()
                    .user(String.format(SELECT_PROMPT, pageList, userText))
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("Page selection failed: {}", e.getMessage());
            return null;
        }
    }

    private ConfluencePageDto fetchPageContent(String pageId) {
        try {
            return confluenceClient.getPage(pageId, "body.storage,space");
        } catch (Exception e) {
            log.error("Failed to fetch Confluence page {}: {}", pageId, e.getMessage());
            return null;
        }
    }

    private String answerFromPage(String userText, ConfluencePageDto page, List<Message> history) {
        String pageContent = extractText(page);

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ANSWER_PROMPT));
        history.forEach(msg -> messages.add(
                msg.getSender() == SenderType.USER
                        ? new UserMessage(msg.getText())
                        : new AssistantMessage(msg.getText())
        ));
        messages.add(new UserMessage(
                "Страница: «" + page.title() + "»\n\n" + pageContent + "\n\nВопрос: " + userText
        ));

        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }

    private String extractText(ConfluencePageDto page) {
        if (page.body() == null || page.body().storage() == null) return "";
        String html = page.body().storage().value();
        if (html == null) return "";
        String text = html
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return text.length() > MAX_PAGE_CHARS ? text.substring(0, MAX_PAGE_CHARS) + "..." : text;
    }
}
