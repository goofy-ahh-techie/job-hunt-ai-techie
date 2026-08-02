package com.jobhuntai.jobhunt_backend.jd.client;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdExtractionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Calls the intelligence-python service to extract structured intelligence from raw
 * JD text. Owns the Java → Python service boundary: every transport and protocol
 * failure is normalised into one of two domain exceptions, so callers never see
 * {@code RestClient} types.
 *
 * <ul>
 *   <li>HTTP 503 (LLM backend down) → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>HTTP 422 (parse/validation failure) → {@link JdExtractionFailedException}</li>
 *   <li>connection refused / timeout → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>any other error status → {@link IntelligenceServiceUnavailableException}</li>
 * </ul>
 */
@Component
public class JdIntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(JdIntelligenceClient.class);
    private static final String EXTRACT_PATH = "/extract/jd";

    private final RestClient intelligenceRestClient;

    public JdIntelligenceClient(RestClient intelligenceRestClient) {
        this.intelligenceRestClient = intelligenceRestClient;
    }

    public JdExtractionResult extract(String rawText) {
        log.debug("Requesting JD extraction: url={}, raw_text_length={}", EXTRACT_PATH, rawText.length());

        IntelligenceExtractionResponse response;
        try {
            response = intelligenceRestClient.post()
                    .uri(EXTRACT_PATH)
                    .body(Map.of("raw_text", rawText))
                    .retrieve()
                    .onStatus(code -> code.value() == 503, (request, res) -> {
                        throw new IntelligenceServiceUnavailableException("LLM service unavailable");
                    })
                    .onStatus(code -> code.value() == 422, (request, res) -> {
                        throw new JdExtractionFailedException("JD extraction failed");
                    })
                    .onStatus(code -> code.isError(), (request, res) -> {
                        // Defensive catch-all for any other non-2xx: treat as unavailable
                        // rather than leaking a raw RestClient error as a 500.
                        throw new IntelligenceServiceUnavailableException(
                                "Intelligence service returned HTTP " + res.getStatusCode().value());
                    })
                    .body(IntelligenceExtractionResponse.class);
        } catch (ResourceAccessException ex) {
            // Connection refused, connect timeout, or read timeout.
            log.warn("Intelligence service unreachable: {}", ex.getMessage());
            throw new IntelligenceServiceUnavailableException(
                    "Intelligence service is unreachable", ex);
        }

        log.debug("Intelligence service responded: status={}",
                response == null ? "null" : (response.success() ? "success" : "failure"));

        if (response == null || !response.success() || response.data() == null) {
            // 2xx with an unsuccessful envelope — shouldn't normally happen (failures
            // arrive as 4xx/5xx), but guard so a malformed success can't return null.
            throw new JdExtractionFailedException(
                    "Intelligence service returned an unsuccessful extraction");
        }
        return response.data();
    }
}
