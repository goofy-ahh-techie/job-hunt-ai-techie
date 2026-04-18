package com.jobhuntai.jobhunt_backend.common.client;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PythonIntelligenceClient {

    private final WebClient intelligenceWebClient;

    public Map<String, Object> ping() {
        return intelligenceWebClient.get().uri("/ping")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                }).block();
    }

}
