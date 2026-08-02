package com.jobhuntai.jobhunt_backend.jd.client;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdExtractionFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class JdIntelligenceClientTest {

    private static final String URL = "http://localhost:8000";
    private static final String EXTRACT_URL = URL + "/extract/jd";

    private MockRestServiceServer server;
    private JdIntelligenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JdIntelligenceClient(builder.build());
    }

    @Test
    void extract_success_returnsMappedResult() {
        String body = """
                {
                  "success": true,
                  "data": {
                    "job_title": "Senior Java Engineer",
                    "company_name": "Acme",
                    "company_description": null,
                    "location": "Remote",
                    "employment_type": "FULL_TIME",
                    "experience_years_min": 5,
                    "experience_years_max": 9,
                    "responsibilities": ["Build APIs", "Mentor"],
                    "required_skills": ["Java", "Spring Boot"],
                    "preferred_skills": ["Kafka"],
                    "qualifications": ["BSc"],
                    "must_have": ["5+ years Java"],
                    "nice_to_have": ["OSS"],
                    "benefits": ["Health"],
                    "raw_summary": "A backend role."
                  },
                  "error": null
                }
                """;
        server.expect(requestTo(EXTRACT_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        JdExtractionResult result = client.extract("some job description text over fifty chars long");

        assertThat(result.jobTitle()).isEqualTo("Senior Java Engineer");
        assertThat(result.employmentType()).isEqualTo("FULL_TIME");
        assertThat(result.experienceYearsMin()).isEqualTo(5);
        assertThat(result.experienceYearsMax()).isEqualTo(9);
        assertThat(result.requiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.mustHave()).containsExactly("5+ years Java");
        assertThat(result.responsibilities()).containsExactly("Build APIs", "Mentor");
        server.verify();
    }

    @Test
    void extract_http503_throwsIntelligenceUnavailable() {
        server.expect(requestTo(EXTRACT_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"success\":false,\"data\":null,\"error\":\"LLM service unavailable\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract("some job description text over fifty chars long"))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void extract_http422_throwsJdExtractionFailed() {
        server.expect(requestTo(EXTRACT_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"success\":false,\"data\":null,\"error\":\"Failed to parse LLM response\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract("some job description text over fifty chars long"))
                .isInstanceOf(JdExtractionFailedException.class);
        server.verify();
    }

    @Test
    void extract_connectionTimeout_throwsIntelligenceUnavailable() {
        // A low-level I/O failure surfaces as ResourceAccessException in RestClient,
        // which the client maps to "unavailable".
        server.expect(requestTo(EXTRACT_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.extract("some job description text over fifty chars long"))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);
        server.verify();
    }
}
