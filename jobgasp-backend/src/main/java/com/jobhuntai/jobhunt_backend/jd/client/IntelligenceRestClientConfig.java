package com.jobhuntai.jobhunt_backend.jd.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} the {@link JdIntelligenceClient} uses to reach the
 * intelligence-python service.
 *
 * <p>Two distinct timeouts, deliberately: the <em>connect</em> timeout is short (the
 * service is either up or it is not), while the <em>read</em> timeout is long because
 * a single JD extraction runs a local LLM that takes tens of seconds — measured at
 * 30–66s for llama3.2:3b on CPU. A read timeout at the connect value would turn every
 * real extraction into a false "service unavailable".
 */
@Configuration
public class IntelligenceRestClientConfig {

    @Bean
    public RestClient intelligenceRestClient(
            @Value("${intelligence.service.url:http://localhost:8000}") String baseUrl,
            @Value("${intelligence.service.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${intelligence.service.read-timeout-seconds:130}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
