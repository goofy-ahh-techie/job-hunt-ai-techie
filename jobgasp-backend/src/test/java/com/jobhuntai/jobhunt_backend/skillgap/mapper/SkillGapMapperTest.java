package com.jobhuntai.jobhunt_backend.skillgap.mapper;

import com.jobhuntai.jobhunt_backend.skillgap.client.StandaloneGapClientResult;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapItem;
import com.jobhuntai.jobhunt_backend.skillgap.domain.GapPriority;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGapStatus;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapSummaryResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.StandaloneGapResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGapMapperTest {

    private static final List<GapItem> GAPS = List.of(
            new GapItem("Production Kubernetes", GapPriority.CRITICAL,
                    "Named as a must-have.", "Learn pod scheduling and Helm charts.", 6),
            new GapItem("Experience owning services end to end", GapPriority.CRITICAL,
                    "Must-have.", "Take on-call ownership of one service.", 12),
            new GapItem("Terraform", GapPriority.HIGH,
                    "Required skill.", "Codify your AWS setup as modules.", 3),
            new GapItem("GraphQL", GapPriority.MEDIUM,
                    "Preferred.", "Add a GraphQL layer to your REST API.", 1),
            new GapItem("Technical writing", GapPriority.LOW,
                    "Inferred.", "Write design docs.", null));

    private static SkillGap gapEntity() {
        return SkillGap.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .matchResultId(UUID.randomUUID())
                .resumeId(UUID.randomUUID())
                .jobDescriptionId(UUID.randomUUID())
                .gapSummary("Strong backend profile; orchestration is the gap.")
                .gaps(SkillGapMapper.serializeGaps(GAPS))
                .quickWins(SkillGapMapper.serializeList(List.of("GraphQL")))
                .dealBreakers(SkillGapMapper.serializeList(List.of("Production Kubernetes")))
                .overallScoreContext(new BigDecimal("74.50"))
                .status(SkillGapStatus.COMPLETED)
                .lastAnalyzedAt(Instant.now())
                .build();
    }

    @Test
    void toSkillGapResponse_deserializesGapObjectsAndStringLists() {
        SkillGapResponse response = SkillGapMapper.toSkillGapResponse(gapEntity());

        assertThat(response.gaps()).hasSize(5);
        // The gap objects survive the JSON round trip intact — this column holds
        // objects, not the flat string arrays of earlier phases.
        assertThat(response.gaps().getFirst().skill()).isEqualTo("Production Kubernetes");
        assertThat(response.gaps().getFirst().priority()).isEqualTo("CRITICAL");
        assertThat(response.gaps().getFirst().learningRecommendation()).contains("Helm");
        assertThat(response.gaps().getFirst().estimatedWeeks()).isEqualTo(6);
        // A null estimate stays null rather than becoming zero.
        assertThat(response.gaps().getLast().estimatedWeeks()).isNull();

        assertThat(response.quickWins()).containsExactly("GraphQL");
        assertThat(response.dealBreakers()).containsExactly("Production Kubernetes");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.overallScoreContext()).isEqualByComparingTo("74.50");
    }

    @Test
    void toSkillGapSummaryResponse_countsCriticalAndHighOnly() {
        SkillGapSummaryResponse summary = SkillGapMapper.toSkillGapSummaryResponse(gapEntity());

        assertThat(summary.criticalGapCount()).isEqualTo(2);
        assertThat(summary.highGapCount()).isEqualTo(1);
        // MEDIUM and LOW are deliberately not counted — a triage view is about what
        // blocks the application, not everything that could be improved.
        assertThat(summary.dealBreakers()).containsExactly("Production Kubernetes");
        assertThat(summary.overallScoreContext()).isEqualByComparingTo("74.50");
    }

    @Test
    void nullJsonColumnsYieldEmptyListsRatherThanThrowing() {
        SkillGap sparse = SkillGap.builder()
                .id(UUID.randomUUID())
                .matchResultId(UUID.randomUUID())
                .status(SkillGapStatus.FAILED)
                .build();

        SkillGapResponse response = SkillGapMapper.toSkillGapResponse(sparse);
        SkillGapSummaryResponse summary = SkillGapMapper.toSkillGapSummaryResponse(sparse);

        assertThat(response.gaps()).isEmpty();
        assertThat(response.quickWins()).isEmpty();
        assertThat(response.dealBreakers()).isEmpty();
        assertThat(summary.criticalGapCount()).isZero();
        assertThat(summary.highGapCount()).isZero();
    }

    @Test
    void malformedJsonDegradesToEmptyRatherThanThrowing() {
        // A read must never fail on a bad column value — the rest of the row is
        // still useful and the alternative is a 500 on a GET.
        SkillGap corrupt = SkillGap.builder()
                .id(UUID.randomUUID())
                .matchResultId(UUID.randomUUID())
                .gaps("{not json}")
                .quickWins("also not json")
                .status(SkillGapStatus.COMPLETED)
                .build();

        SkillGapResponse response = SkillGapMapper.toSkillGapResponse(corrupt);

        assertThat(response.gaps()).isEmpty();
        assertThat(response.quickWins()).isEmpty();
    }

    @Test
    void toStandaloneGapResponse_carriesThroughAllFourFields() {
        UUID resumeId = UUID.randomUUID();
        StandaloneGapClientResult result = new StandaloneGapClientResult(
                List.of("backend API development"),
                List.of("data engineering"),
                List.of("Kafka Streams", "Spark"),
                "A solid backend engineer.");

        StandaloneGapResponse response = SkillGapMapper.toStandaloneGapResponse(resumeId, result);

        assertThat(response.resumeId()).isEqualTo(resumeId);
        assertThat(response.strongDomains()).containsExactly("backend API development");
        assertThat(response.weakDomains()).containsExactly("data engineering");
        assertThat(response.recommendedAdditions()).containsExactly("Kafka Streams", "Spark");
        assertThat(response.generalAssessment()).isEqualTo("A solid backend engineer.");
    }

    @Test
    void gapItemsRoundTripThroughSerialization() {
        String json = SkillGapMapper.serializeGaps(GAPS);
        List<GapItem> parsed = SkillGapMapper.deserializeGaps(json);

        assertThat(parsed).hasSize(5);
        assertThat(parsed.getFirst().priority()).isEqualTo(GapPriority.CRITICAL);
        assertThat(parsed.get(3).priority()).isEqualTo(GapPriority.MEDIUM);
    }
}
