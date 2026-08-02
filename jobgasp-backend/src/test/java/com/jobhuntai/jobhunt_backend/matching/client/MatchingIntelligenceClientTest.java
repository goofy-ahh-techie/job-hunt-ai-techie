package com.jobhuntai.jobhunt_backend.matching.client;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.SemanticMatchingFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MatchingIntelligenceClientTest {

    private static final String URL = "http://localhost:8000";
    private static final String SIMILARITY_URL = URL + "/match/semantic-similarity";

    private MockRestServiceServer server;
    private MatchingIntelligenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MatchingIntelligenceClient(builder.build());
    }

    private void call() {
        client.semanticSimilarity(List.of("Kubernetes"), List.of("Ran container orchestration"), 0.65);
    }

    @Test
    void success_returnsMappedResult() {
        String body = """
                {
                  "results": [
                    {
                      "phrase": "Kubernetes",
                      "matched": true,
                      "best_score": 0.81,
                      "best_match_excerpt": "Ran container orchestration"
                    },
                    {
                      "phrase": "Terraform",
                      "matched": false,
                      "best_score": 0.22,
                      "best_match_excerpt": ""
                    }
                  ],
                  "match_count": 1,
                  "match_percentage": 50.0
                }
                """;
        server.expect(requestTo(SIMILARITY_URL))
                .andExpect(method(POST))
                // The request body is snake_case on the wire, matching the Python schema.
                .andExpect(jsonPath("$.source_phrases[0]").value("Kubernetes"))
                .andExpect(jsonPath("$.target_texts[0]").value("Ran container orchestration"))
                .andExpect(jsonPath("$.threshold").value(0.65))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SemanticSimilarityResult result = client.semanticSimilarity(
                List.of("Kubernetes"), List.of("Ran container orchestration"), 0.65);

        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.matchPercentage()).isEqualTo(50.0);
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().getFirst().phrase()).isEqualTo("Kubernetes");
        assertThat(result.results().getFirst().matched()).isTrue();
        assertThat(result.results().getFirst().bestScore()).isEqualTo(0.81);
        assertThat(result.results().getFirst().bestMatchExcerpt())
                .isEqualTo("Ran container orchestration");
        assertThat(result.matchedPhrases()).containsExactly("Kubernetes");
        assertThat(result.unmatchedPhrases()).containsExactly("Terraform");
        server.verify();
    }

    @Test
    void http503_throwsIntelligenceUnavailable() {
        server.expect(requestTo(SIMILARITY_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"detail\":\"LLM service unavailable\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call)
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void http422_throwsSemanticMatchingFailed() {
        server.expect(requestTo(SIMILARITY_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"success\":false,\"data\":null,\"error\":\"Invalid request\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call)
                .isInstanceOf(SemanticMatchingFailedException.class);
        server.verify();
    }

    @Test
    void timeout_throwsIntelligenceUnavailable() {
        // A low-level I/O failure surfaces as ResourceAccessException in RestClient,
        // which the client maps to "unavailable" so the scorers can degrade.
        server.expect(requestTo(SIMILARITY_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(this::call)
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void otherErrorStatus_throwsIntelligenceUnavailable() {
        server.expect(requestTo(SIMILARITY_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(this::call)
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }
}
