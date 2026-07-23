package com.jobhuntai.jobhunt_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient intelligenceWebClient(@Value("${app.intelligence.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl).build();
    }

}
