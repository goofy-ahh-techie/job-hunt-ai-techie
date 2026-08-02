package com.jobhuntai.jobhunt_backend.matching.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} the {@link MatchingIntelligenceClient} uses.
 *
 * <p>A second bean rather than a reuse of {@code intelligenceRestClient}: the base URL
 * is shared (one Python service) but the read timeout is not. A JD extraction is one
 * LLM completion; a semantic-similarity call embeds every resume chunk plus every
 * unmatched phrase, so its worst case is a multiple of the extraction's and the 130s
 * extraction ceiling would cut off a legitimate match on a long resume.
 *
 * <p>Both beans are named, and both clients qualify their injection point, so adding
 * this one cannot silently make the Phase 3 wiring ambiguous.
 */
@Configuration
public class MatchingRestClientConfig {

    @Bean
    public RestClient matchingRestClient(
            @Value("${intelligence.service.url:http://localhost:8000}") String baseUrl,
            @Value("${intelligence.service.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${intelligence.service.match-read-timeout-seconds:180}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
