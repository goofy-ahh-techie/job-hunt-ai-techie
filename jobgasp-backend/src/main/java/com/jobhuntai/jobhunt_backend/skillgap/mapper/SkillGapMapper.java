package com.jobhuntai.jobhunt_backend.skillgap.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhuntai.jobhunt_backend.skillgap.client.StandaloneGapClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapItem;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapPriority;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import com.jobhuntai.jobhunt_backend.skillgap.dto.GapItemResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapSummaryResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.StandaloneGapResponse;

import java.util.List;
import java.util.UUID;

/**
 * Single home for skill gap entity → DTO conversion, and for the JSON codec of the
 * {@code gaps} / {@code quick_wins} / {@code deal_breakers} columns.
 *
 * <p>This mapper owns its own {@link ObjectMapper} rather than reusing {@code JdMapper}'s
 * string-list helpers for everything, because {@code gaps} is a JSON array of
 * <em>objects</em> — the first structured JSON column in the codebase. The flat string
 * lists still go through the same shape of helper, kept local so both directions of the
 * gap codec sit together.
 */
public final class SkillGapMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<GapItem>> GAP_ITEM_LIST = new TypeReference<>() {
    };

    private SkillGapMapper() {
    }

    // --- JSON codec ---

    /** Serialise a {@code List<String>} to its JSON-array text form for storage. */
    public static String serializeList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            // Serialising a List<String> does not realistically fail; treat any freak
            // failure as an empty array rather than corrupting the write.
            return "[]";
        }
    }

    /** Serialise the gap objects themselves. */
    public static String serializeGaps(List<GapItem> gaps) {
        try {
            return OBJECT_MAPPER.writeValueAsString(gaps == null ? List.of() : gaps);
        } catch (Exception ex) {
            return "[]";
        }
    }

    /** Deserialise a JSON-array TEXT column back to a {@code List<String>}. */
    public static List<String> deserializeList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(json, STRING_LIST);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            // A malformed value must not blow up a read; surface an empty list.
            return List.of();
        }
    }

    /** Deserialise the {@code gaps} column back to {@link GapItem} objects. */
    public static List<GapItem> deserializeGaps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<GapItem> parsed = OBJECT_MAPPER.readValue(json, GAP_ITEM_LIST);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            return List.of();
        }
    }

    // --- entity -> DTO ---

    public static SkillGapResponse toSkillGapResponse(SkillGap gap) {
        return new SkillGapResponse(
                gap.getId(),
                gap.getMatchResultId(),
                gap.getResumeId(),
                gap.getJobDescriptionId(),
                gap.getGapSummary(),
                toGapItemResponses(deserializeGaps(gap.getGaps())),
                deserializeList(gap.getQuickWins()),
                deserializeList(gap.getDealBreakers()),
                gap.getOverallScoreContext(),
                gap.getStatus() == null ? null : gap.getStatus().name(),
                gap.getLastAnalyzedAt(),
                gap.getCreatedAt()
        );
    }

    public static SkillGapSummaryResponse toSkillGapSummaryResponse(SkillGap gap) {
        List<GapItem> items = deserializeGaps(gap.getGaps());
        return new SkillGapSummaryResponse(
                gap.getId(),
                gap.getMatchResultId(),
                gap.getJobDescriptionId(),
                countByPriority(items, GapPriority.CRITICAL),
                countByPriority(items, GapPriority.HIGH),
                deserializeList(gap.getDealBreakers()),
                gap.getOverallScoreContext(),
                gap.getLastAnalyzedAt()
        );
    }

    public static List<SkillGapSummaryResponse> toSkillGapSummaryResponseList(List<SkillGap> gaps) {
        return gaps.stream().map(SkillGapMapper::toSkillGapSummaryResponse).toList();
    }

    public static StandaloneGapResponse toStandaloneGapResponse(
            UUID resumeId, StandaloneGapClientResult result) {
        return new StandaloneGapResponse(
                resumeId,
                result.strongDomains(),
                result.weakDomains(),
                result.recommendedAdditions(),
                result.generalAssessment()
        );
    }

    // --- helpers ---

    private static List<GapItemResponse> toGapItemResponses(List<GapItem> items) {
        return items.stream()
                .map(item -> new GapItemResponse(
                        item.skill(),
                        item.priority() == null ? null : item.priority().name(),
                        item.reason(),
                        item.learningRecommendation(),
                        item.estimatedWeeks()))
                .toList();
    }

    private static int countByPriority(List<GapItem> items, GapPriority priority) {
        return (int) items.stream().filter(item -> item.priority() == priority).count();
    }
}
