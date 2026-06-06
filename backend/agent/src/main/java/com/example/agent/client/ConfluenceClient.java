package com.example.agent.client;

import com.example.agent.dto.confluence.ConfluencePageDto;
import com.example.agent.dto.confluence.ConfluenceSearchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface ConfluenceClient {

    @GetExchange("/wiki/rest/api/content/search")
    ConfluenceSearchResponse search(
            @RequestParam("cql") String cql,
            @RequestParam("expand") String expand,
            @RequestParam("limit") int limit
    );

    @GetExchange("/wiki/rest/api/content")
    ConfluenceSearchResponse getAllPages(
            @RequestParam("type") String type,
            @RequestParam("limit") int limit
    );

    @GetExchange("/wiki/rest/api/content/{id}")
    ConfluencePageDto getPage(
            @PathVariable("id") String id,
            @RequestParam("expand") String expand
    );
}
