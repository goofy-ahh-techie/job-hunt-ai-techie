package com.jobhuntai.jobhunt_backend.jd.client;

/**
 * The intelligence-python {@code JdExtractionResponse} envelope: {@code success},
 * the extracted {@code data} (null on failure), and an {@code error} string. The
 * client unwraps this and returns {@link JdExtractionResult} on success.
 */
record IntelligenceExtractionResponse(
        boolean success,
        JdExtractionResult data,
        String error
) {
}
