package com.example.agent.client;

import com.example.agent.dto.jira.JiraProjectDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange
public interface JiraClient {

    @PostExchange("/rest/api/3/search/jql")
    JiraSearchResponse search(@RequestBody JiraSearchRequest request);

    @GetExchange("/rest/api/3/project")
    List<JiraProjectDto> getProjects();
}
