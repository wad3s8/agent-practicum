package com.example.agent.dto.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSearchResponse(List<ConfluencePageDto> results, int size) {}
