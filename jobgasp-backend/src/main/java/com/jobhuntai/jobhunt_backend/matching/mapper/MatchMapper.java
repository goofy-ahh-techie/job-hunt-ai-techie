package com.jobhuntai.jobhunt_backend.matching.mapper;

import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchResponse;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchSummaryResponse;
import com.jobhuntai.jobhunt_backend.matching.dto.SubScoreDetail;
import com.jobhuntai.jobhunt_backend.matching.scoring.OrchestratedScores;
import com.jobhuntai.jobhunt_backend.matching.scoring.SubScoreResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single home for match entity → DTO conversion.
 *
 * <p>Two paths to a {@link MatchResponse}, because the two callers have different
 * information. A freshly calculated match still holds its {@link OrchestratedScores}
 * in memory, so every dimension can report its own matched/missing lists and
 * sentence. A match read back from the database cannot: only the three keyword
 * dimensions have their lists persisted (they are the actionable gaps), and the
 * per-dimension prose survives inside {@code score_explanation}. The reader
 * reconstructs what it can and leaves the rest empty rather than inventing it.
 *
 * <p>The JSON list codec is reused from {@code JdMapper} rather than duplicated —
 * the "list fields are JSON text" decision belongs in one place.
 */
public final class MatchMapper {

    private MatchMapper() {
    }

    // --- fresh calculation: full fidelity from the in-memory scores ---

    public static MatchResponse toMatchResponse(MatchResult result, OrchestratedScores scores) {
        return new MatchResponse(
                result.getId(),
                result.getUserId(),
                result.getResumeId(),
                result.getResumeVersionId(),
                result.getJobDescriptionId(),
                result.getOverallScore(),
                toDetail(scores.mustHave()),
                toDetail(scores.requiredSkills()),
                toDetail(scores.responsibilities()),
                toDetail(scores.experience()),
                toDetail(scores.qualifications()),
                toDetail(scores.preferredSkills()),
                result.getScoreExplanation(),
                result.getStatus(),
                result.getLastCalculatedAt(),
                result.getCreatedAt()
        );
    }

    // --- stored row: reconstructed from the persisted columns ---

    /**
     * Build a response from a persisted match alone (the GET path).
     *
     * <p>Dimensions whose evidence is not stored come back with empty lists and their
     * sentence recovered from the stored explanation — the full narrative is always
     * available on {@code scoreExplanation} regardless.
     */
    public static MatchResponse toMatchResponse(MatchResult result) {
        String explanation = result.getScoreExplanation();
        return new MatchResponse(
                result.getId(),
                result.getUserId(),
                result.getResumeId(),
                result.getResumeVersionId(),
                result.getJobDescriptionId(),
                result.getOverallScore(),
                detail(result.getMustHaveScore(),
                        JdMapper.deserializeList(result.getMustHaveMatched()),
                        JdMapper.deserializeList(result.getMustHaveMissing()),
                        lineFor(explanation, "Must-have coverage")),
                detail(result.getRequiredSkillsScore(),
                        JdMapper.deserializeList(result.getSkillsMatched()),
                        JdMapper.deserializeList(result.getSkillsMissing()),
                        lineFor(explanation, "Required skills")),
                detail(result.getResponsibilitiesScore(), List.of(), List.of(),
                        lineFor(explanation, "Responsibilities")),
                detail(result.getExperienceScore(), List.of(), List.of(),
                        lineFor(explanation, "Experience")),
                detail(result.getQualificationsScore(), List.of(), List.of(),
                        lineFor(explanation, "Qualifications")),
                detail(result.getPreferredSkillsScore(),
                        JdMapper.deserializeList(result.getPreferredMatched()), List.of(),
                        lineFor(explanation, "Preferred skills")),
                explanation,
                result.getStatus(),
                result.getLastCalculatedAt(),
                result.getCreatedAt()
        );
    }

    public static MatchSummaryResponse toMatchSummaryResponse(MatchResult result) {
        return new MatchSummaryResponse(
                result.getId(),
                result.getResumeId(),
                result.getJobDescriptionId(),
                result.getOverallScore(),
                result.getStatus(),
                result.getLastCalculatedAt()
        );
    }

    public static List<MatchSummaryResponse> toMatchSummaryResponseList(List<MatchResult> results) {
        return results.stream().map(MatchMapper::toMatchSummaryResponse).toList();
    }

    // --- helpers ---

    private static SubScoreDetail toDetail(SubScoreResult result) {
        return new SubScoreDetail(
                result.rawScore(), result.matched(), result.missing(), result.explanation());
    }

    private static SubScoreDetail detail(BigDecimal score, List<String> matched,
                                         List<String> missing, String explanation) {
        return new SubScoreDetail(
                score == null ? 0.0 : score.doubleValue(), matched, missing, explanation);
    }

    /**
     * Recover one dimension's sentence from the stored explanation block.
     *
     * <p>Matching on the label is safe because {@code ScoreExplanationBuilder} wrote
     * the text and uses these exact labels; a miss (an explanation from an older
     * format, or a null column) yields an empty string rather than a failed read.
     */
    private static String lineFor(String explanation, String label) {
        if (explanation == null || explanation.isBlank()) {
            return "";
        }
        String marker = label + ": ";
        for (String line : explanation.split("\\R")) {
            int index = line.indexOf(marker);
            if (index < 0) {
                continue;
            }
            String remainder = line.substring(index + marker.length());
            // "<score> — <explanation>": keep the prose, drop the number the caller
            // already has as a field.
            int separator = remainder.indexOf(" — ");
            return separator < 0 ? remainder.trim() : remainder.substring(separator + 3).trim();
        }
        return "";
    }
}
