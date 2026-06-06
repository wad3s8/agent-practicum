package com.example.agent.client;

import com.example.agent.dto.jira.JiraAssigneeRequest;
import com.example.agent.dto.jira.JiraProjectDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import com.example.agent.dto.jira.JiraUserDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

@HttpExchange
public interface JiraClient {

    @PostExchange("/rest/api/3/search/jql")
    JiraSearchResponse search(@RequestBody JiraSearchRequest request);

    @GetExchange("/rest/api/3/project")
    List<JiraProjectDto> getProjects();

    @GetExchange("/rest/api/3/user/search")
    List<JiraUserDto> searchUsers(@RequestParam("query") String query);

    @PutExchange("/rest/api/3/issue/{issueKey}/assignee")
    void assignIssue(@PathVariable("issueKey") String issueKey, @RequestBody JiraAssigneeRequest request);
}
