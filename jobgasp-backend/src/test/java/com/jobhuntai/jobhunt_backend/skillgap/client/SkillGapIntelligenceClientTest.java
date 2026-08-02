package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.jobhuntai.jobhunt_backend.common.exception.GapAnalysisFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
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

class SkillGapIntelligenceClientTest {

    private static final String URL = "http://localhost:8000";
    private static final String ANALYZE_URL = URL + "/gaps/analyze";
    private static final String STANDALONE_URL = URL + "/gaps/analyze-standalone";

    private MockRestServiceServer server;
    private SkillGapIntelligenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SkillGapIntelligenceClient(builder.build());
    }

    private static GapAnalysisClientRequest request() {
        return new GapAnalysisClientRequest(
                "Senior Backend Engineer",
                "Fintech Global",
                List.of("Terraform"),
                List.of("Production Kubernetes"),
                List.of("GraphQL"),
                "Java and Spring Boot services on AWS.",
                74.5);
    }

    @Test
    void analyzeGap_success_returnsMappedResult() {
        String body = """
                {
                  "success": true,
                  "data": {
                    "gap_summary": "Orchestration is the main gap.",
                    "gaps": [
                      {
                        "skill": "Production Kubernetes",
                        "priority": "CRITICAL",
                        "reason": "Named as a must-have for this role.",
                        "learning_recommendation": "Learn pod scheduling and Helm charts.",
                        "estimated_weeks": 6
                      },
                      {
                        "skill": "GraphQL",
                        "priority": "MEDIUM",
                        "reason": "Preferred for this role.",
                        "learning_recommendation": "Add a GraphQL layer to your REST API.",
                        "estimated_weeks": 1
                      }
                    ],
                    "quick_wins": ["GraphQL"],
                    "deal_breakers": ["Production Kubernetes"]
                  },
                  "error": null
                }
                """;
        server.expect(requestTo(ANALYZE_URL))
                .andExpect(method(POST))
                // The request body is snake_case on the wire, matching the Python schema.
                .andExpect(jsonPath("$.job_title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.company_name").value("Fintech Global"))
                .andExpect(jsonPath("$.missing_must_haves[0]").value("Production Kubernetes"))
                .andExpect(jsonPath("$.overall_score").value(74.5))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GapAnalysisClientResult result = client.analyzeGap(request());

        assertThat(result.gapSummary()).isEqualTo("Orchestration is the main gap.");
        assertThat(result.gaps()).hasSize(2);
        GapItemResult critical = result.gaps().getFirst();
        assertThat(critical.skill()).isEqualTo("Production Kubernetes");
        assertThat(critical.priority()).isEqualTo("CRITICAL");
        assertThat(critical.learningRecommendation()).contains("Helm");
        assertThat(critical.estimatedWeeks()).isEqualTo(6);
        assertThat(result.quickWins()).containsExactly("GraphQL");
        assertThat(result.dealBreakers()).containsExactly("Production Kubernetes");
        server.verify();
    }

    @Test
    void analyzeGap_http503_throwsIntelligenceUnavailable() {
        server.expect(requestTo(ANALYZE_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"success\":false,\"data\":null,\"error\":\"LLM service unavailable\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyzeGap(request()))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void analyzeGap_http422_throwsGapAnalysisFailed() {
        server.expect(requestTo(ANALYZE_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"success\":false,\"data\":null,\"error\":\"Gap analysis failed validation\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyzeGap(request()))
                .isInstanceOf(GapAnalysisFailedException.class);
        server.verify();
    }

    @Test
    void analyzeGap_timeout_throwsIntelligenceUnavailable() {
        // A low-level I/O failure surfaces as ResourceAccessException in RestClient.
        server.expect(requestTo(ANALYZE_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.analyzeGap(request()))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void analyzeGap_successEnvelopeWithNullData_throwsGapAnalysisFailed() {
        // A 2xx that carries no data would otherwise return null into the service.
        server.expect(requestTo(ANALYZE_URL))
                .andRespond(withSuccess("{\"success\":true,\"data\":null,\"error\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyzeGap(request()))
                .isInstanceOf(GapAnalysisFailedException.class);
        server.verify();
    }

    @Test
    void analyzeGap_otherErrorStatus_throwsIntelligenceUnavailable() {
        server.expect(requestTo(ANALYZE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.analyzeGap(request()))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void analyzeStandalone_success_returnsMappedResult() {
        String body = """
                {
                  "success": true,
                  "data": {
                    "strong_domains": ["backend API development", "cloud infrastructure"],
                    "weak_domains": ["data engineering"],
                    "recommended_additions": ["Kafka Streams", "Spark"],
                    "general_assessment": "A solid backend engineer."
                  },
                  "error": null
                }
                """;
        server.expect(requestTo(STANDALONE_URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.resume_text").value("Java engineer resume text"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        StandaloneGapClientResult result = client.analyzeStandalone("Java engineer resume text");

        assertThat(result.strongDomains())
                .containsExactly("backend API development", "cloud infrastructure");
        assertThat(result.weakDomains()).containsExactly("data engineering");
        assertThat(result.recommendedAdditions()).containsExactly("Kafka Streams", "Spark");
        assertThat(result.generalAssessment()).isEqualTo("A solid backend engineer.");
        server.verify();
    }

    @Test
    void analyzeStandalone_http503_throwsIntelligenceUnavailable() {
        server.expect(requestTo(STANDALONE_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"success\":false,\"data\":null,\"error\":\"LLM service unavailable\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyzeStandalone("resume"))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }
}
