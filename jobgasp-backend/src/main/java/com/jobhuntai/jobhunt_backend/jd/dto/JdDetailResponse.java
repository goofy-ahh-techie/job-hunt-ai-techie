package com.jobhuntai.jobhunt_backend.jd.dto;

/**
 * Combined view: a job description plus its extracted intelligence. {@code intelligence}
 * is null when extraction has not produced a row (e.g. the intelligence service was
 * unavailable and the JD is FAILED).
 */
public record JdDetailResponse(
        JdResponse jd,
        JdIntelligenceResponse intelligence
) {
}
