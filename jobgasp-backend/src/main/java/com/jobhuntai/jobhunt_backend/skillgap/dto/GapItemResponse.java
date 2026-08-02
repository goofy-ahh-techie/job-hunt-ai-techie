package com.jobhuntai.jobhunt_backend.skillgap.dto;

/**
 * One gap as the API reports it.
 *
 * <p>{@code priority} is a plain {@code String} rather than the enum: it is a stable
 * wire value for clients to sort and colour by, and keeping the enum out of the DTO
 * means renaming a constant cannot silently change the contract.
 */
public record GapItemResponse(
        String skill,
        String priority,
        String reason,
        String learningRecommendation,
        Integer estimatedWeeks
) {
}
