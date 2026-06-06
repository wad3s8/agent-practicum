package com.example.agent.client;

import com.example.agent.dto.confluence.ConfluenceSearchResponse;
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
}
