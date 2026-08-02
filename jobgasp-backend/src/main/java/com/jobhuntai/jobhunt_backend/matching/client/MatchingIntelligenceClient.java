package com.jobhuntai.jobhunt_backend.matching.client;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.SemanticMatchingFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the intelligence-python service for embedding-based semantic similarity.
 *
 * <p>A separate client from {@code JdIntelligenceClient} rather than another method on
 * it: different endpoint, different timeout profile, and a different failure contract.
 * Extraction failing is fatal to a JD ingest; semantic matching failing is not fatal to
 * a match — the scorers catch the exceptions below and degrade to keyword-only. Keeping
 * the clients apart keeps that asymmetry legible.
 *
 * <ul>
 *   <li>HTTP 503 (embedding runtime down) → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>HTTP 422 (request rejected) → {@link SemanticMatchingFailedException}</li>
 *   <li>connection refused / timeout → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>any other error status → {@link IntelligenceServiceUnavailableException}</li>
 * </ul>
 */
@Component
public class MatchingIntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(MatchingIntelligenceClient.class);
    private static final String SIMILARITY_PATH = "/match/semantic-similarity";

    private final RestClient matchingRestClient;

    public MatchingIntelligenceClient(@Qualifier("matchingRestClient") RestClient matchingRestClient) {
        this.matchingRestClient = matchingRestClient;
    }

    /**
     * Ask which of {@code sourcePhrases} are semantically present in {@code targetTexts}.
     *
     * @param threshold minimum cosine similarity for a phrase to count as present;
     *                  callers tune it per dimension (keyword-like skills sit higher
     *                  than prose responsibilities).
     */
    public SemanticSimilarityResult semanticSimilarity(List<String> sourcePhrases,
                                                       List<String> targetTexts,
                                                       double threshold) {
        log.debug("Requesting semantic similarity: phrases={}, targets={}, threshold={}",
                sourcePhrases.size(), targetTexts.size(), threshold);

        // LinkedHashMap, not Map.of: the payload is a fixed-shape request body and a
        // stable field order keeps request logs diffable between runs.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_phrases", sourcePhrases);
        body.put("target_texts", targetTexts);
        body.put("threshold", threshold);

        SemanticSimilarityResult result;
        try {
            result = matchingRestClient.post()
                    .uri(SIMILARITY_PATH)
                    .body(body)
                    .retrieve()
                    .onStatus(code -> code.value() == 503, (request, response) -> {
                        throw new IntelligenceServiceUnavailableException(
                                "Semantic matching service unavailable");
                    })
                    .onStatus(code -> code.value() == 422, (request, response) -> {
                        throw new SemanticMatchingFailedException(
                                "Semantic matching request was rejected");
                    })
                    .onStatus(code -> code.isError(), (request, response) -> {
                        // Defensive catch-all: treat any other non-2xx as unavailable
                        // rather than leaking a raw RestClient error as a 500.
                        throw new IntelligenceServiceUnavailableException(
                                "Intelligence service returned HTTP "
                                        + response.getStatusCode().value());
                    })
                    .body(SemanticSimilarityResult.class);
        } catch (ResourceAccessException ex) {
            // Connection refused, connect timeout, or read timeout.
            log.warn("Intelligence service unreachable for semantic matching: {}", ex.getMessage());
            throw new IntelligenceServiceUnavailableException(
                    "Intelligence service is unreachable", ex);
        }

        if (result == null) {
            // 2xx with an empty body — nothing downstream can read it, and returning
            // null would push a NullPointerException into a scorer.
            throw new SemanticMatchingFailedException(
                    "Intelligence service returned an empty semantic-similarity response");
        }

        log.debug("Semantic similarity responded: matched={}/{} ({}%)",
                result.matchCount(), result.results().size(), result.matchPercentage());
        return result;
    }
}
