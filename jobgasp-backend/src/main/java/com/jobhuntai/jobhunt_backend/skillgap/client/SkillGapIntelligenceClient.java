package com.jobhuntai.jobhunt_backend.skillgap.client;

import com.jobhuntai.jobhunt_backend.common.exception.GapAnalysisFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Calls the intelligence-python service for skill gap analysis, in both its
 * match-tied and standalone forms.
 *
 * <p>Owns this corner of the Java → Python boundary: every transport and protocol
 * failure is normalised into one of two domain exceptions, so nothing above ever
 * sees a {@code RestClient} type. Unlike the Phase 4 matching client, callers here do
 * <em>not</em> degrade around a failure — a gap analysis with no analysis is nothing
 * at all, so the exception propagates and the row records FAILED.
 *
 * <ul>
 *   <li>HTTP 503 (LLM runtime down) → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>HTTP 422 (parse/validation failure) → {@link GapAnalysisFailedException}</li>
 *   <li>connection refused / timeout → {@link IntelligenceServiceUnavailableException}</li>
 *   <li>any other error status → {@link IntelligenceServiceUnavailableException}</li>
 * </ul>
 */
@Component
public class SkillGapIntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(SkillGapIntelligenceClient.class);
    private static final String ANALYZE_PATH = "/gaps/analyze";
    private static final String ANALYZE_STANDALONE_PATH = "/gaps/analyze-standalone";

    private final RestClient skillGapRestClient;

    public SkillGapIntelligenceClient(@Qualifier("skillGapRestClient") RestClient skillGapRestClient) {
        this.skillGapRestClient = skillGapRestClient;
    }

    /** Rank and explain the gaps for one match. */
    public GapAnalysisClientResult analyzeGap(GapAnalysisClientRequest request) {
        log.debug("Requesting gap analysis: job_title={}, must_haves={}, skills={}, preferred={}",
                request.jobTitle(),
                request.missingMustHaves().size(),
                request.missingSkills().size(),
                request.preferredMissing().size());

        GapAnalysisResponseEnvelope response =
                post(ANALYZE_PATH, request, GapAnalysisResponseEnvelope.class);

        if (response == null || !response.success() || response.data() == null) {
            // 2xx with an unsuccessful envelope — failures normally arrive as
            // 4xx/5xx, but guard so a malformed success cannot return null.
            throw new GapAnalysisFailedException(
                    "Intelligence service returned an unsuccessful gap analysis");
        }

        log.debug("Gap analysis returned {} gap(s)", response.data().gaps().size());
        return response.data();
    }

    /** Assess a resume on its own, with no target role. */
    public StandaloneGapClientResult analyzeStandalone(String resumeText) {
        log.debug("Requesting standalone gap analysis: resume_text_length={}",
                resumeText == null ? 0 : resumeText.length());

        StandaloneGapResponseEnvelope response = post(
                ANALYZE_STANDALONE_PATH,
                Map.of("resume_text", resumeText == null ? "" : resumeText),
                StandaloneGapResponseEnvelope.class);

        if (response == null || !response.success() || response.data() == null) {
            throw new GapAnalysisFailedException(
                    "Intelligence service returned an unsuccessful standalone gap analysis");
        }
        return response.data();
    }

    /** Shared POST + status mapping for both endpoints. */
    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return skillGapRestClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .onStatus(code -> code.value() == 503, (request, response) -> {
                        throw new IntelligenceServiceUnavailableException("LLM service unavailable");
                    })
                    .onStatus(code -> code.value() == 422, (request, response) -> {
                        throw new GapAnalysisFailedException("Gap analysis failed");
                    })
                    .onStatus(code -> code.isError(), (request, response) -> {
                        // Defensive catch-all: treat any other non-2xx as unavailable
                        // rather than leaking a raw RestClient error as a 500.
                        throw new IntelligenceServiceUnavailableException(
                                "Intelligence service returned HTTP "
                                        + response.getStatusCode().value());
                    })
                    .body(responseType);
        } catch (ResourceAccessException ex) {
            // Connection refused, connect timeout, or read timeout.
            log.warn("Intelligence service unreachable for gap analysis: {}", ex.getMessage());
            throw new IntelligenceServiceUnavailableException(
                    "Intelligence service is unreachable", ex);
        }
    }

    /** The {@code {success, data, error}} envelope around a gap analysis. */
    record GapAnalysisResponseEnvelope(
            boolean success,
            GapAnalysisClientResult data,
            String error
    ) {
    }

    /** The same envelope around a standalone assessment. */
    record StandaloneGapResponseEnvelope(
            boolean success,
            StandaloneGapClientResult data,
            String error
    ) {
    }
}
