package com.jobhuntai.jobhunt_backend.jd.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import com.jobhuntai.jobhunt_backend.jd.dto.JdDetailResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdIntelligenceResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdResponse;

import java.util.List;

/**
 * Single home for JD entity → DTO conversion, and for the JSON (de)serialisation of
 * the list-valued intelligence fields.
 *
 * <p>The list fields ({@code responsibilities}, {@code required_skills}, …) are stored
 * as JSON-serialised TEXT — a documented Phase 3 tradeoff. This class owns both
 * directions of that codec: {@link #serializeList} for the service writing an entity,
 * and the private reader for building responses. Kept here (not in the entity, service,
 * or controller) so the JSON concern lives in exactly one place.
 */
public final class JdMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private JdMapper() {
    }

    // --- JSON list codec (list <-> TEXT column) ---

    /**
     * Serialise a {@code List<String>} to its JSON-array text form for storage. A null
     * list is stored as an empty JSON array so the column is never null and reads back
     * as an empty list.
     */
    public static String serializeList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            // String serialisation of a List<String> does not realistically fail; treat
            // any freak failure as an empty array rather than corrupting the write.
            return "[]";
        }
    }

    /** Deserialise a JSON-array TEXT column back to a {@code List<String>}. */
    private static List<String> deserializeList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(json, STRING_LIST);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            // A malformed value should not blow up a read; surface an empty list.
            return List.of();
        }
    }

    // --- entity -> DTO ---

    public static JdResponse toJdResponse(JobDescription jd) {
        return new JdResponse(
                jd.getId(),
                jd.getUserId(),
                jd.getTitle(),
                jd.getSourceType(),
                jd.getStatus(),
                jd.getCreatedAt()
        );
    }

    public static JdIntelligenceResponse toJdIntelligenceResponse(JdIntelligence intel) {
        return new JdIntelligenceResponse(
                intel.getId(),
                intel.getJobDescriptionId(),
                intel.getJobTitle(),
                intel.getCompanyName(),
                intel.getCompanyDescription(),
                intel.getLocation(),
                intel.getEmploymentType(),
                intel.getExperienceYearsMin(),
                intel.getExperienceYearsMax(),
                deserializeList(intel.getResponsibilities()),
                deserializeList(intel.getRequiredSkills()),
                deserializeList(intel.getPreferredSkills()),
                deserializeList(intel.getQualifications()),
                deserializeList(intel.getMustHave()),
                deserializeList(intel.getNiceToHave()),
                deserializeList(intel.getBenefits()),
                intel.getRawSummary(),
                intel.getExtractionStatus(),
                intel.getExtractionError()
        );
    }

    public static JdDetailResponse toJdDetailResponse(JobDescription jd, JdIntelligence intel) {
        return new JdDetailResponse(
                toJdResponse(jd),
                intel == null ? null : toJdIntelligenceResponse(intel)
        );
    }

    public static List<JdResponse> toJdResponseList(List<JobDescription> jds) {
        return jds.stream().map(JdMapper::toJdResponse).toList();
    }
}
