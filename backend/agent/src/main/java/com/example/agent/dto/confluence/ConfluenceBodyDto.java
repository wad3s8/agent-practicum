package com.example.agent.dto.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceBodyDto(ConfluenceStorageDto storage) {}
