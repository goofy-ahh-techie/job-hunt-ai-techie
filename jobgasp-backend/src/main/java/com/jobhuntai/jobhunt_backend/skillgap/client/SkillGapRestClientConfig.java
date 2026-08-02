package com.jobhuntai.jobhunt_backend.skillgap.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} the {@link SkillGapIntelligenceClient} uses.
 *
 * <p>The third such bean, and the third distinct read timeout, because the three
 * workloads genuinely differ: JD extraction is one completion over a job ad (130s),
 * semantic matching embeds every resume passage (180s), and gap analysis is one
 * completion over a short gap list (120s). All three share the base URL — there is
 * one Python service — and every client qualifies its injection point by name so no
 * bean can be resolved by accident.
 */
@Configuration
public class SkillGapRestClientConfig {

    @Bean
    public RestClient skillGapRestClient(
            @Value("${intelligence.service.url:http://localhost:8000}") String baseUrl,
            @Value("${intelligence.service.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${intelligence.service.gap-read-timeout-seconds:120}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
