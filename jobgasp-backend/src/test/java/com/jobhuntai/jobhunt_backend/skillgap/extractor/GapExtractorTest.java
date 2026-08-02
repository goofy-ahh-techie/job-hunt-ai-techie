package com.jobhuntai.jobhunt_backend.skillgap.extractor;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GapExtractorTest {

    private final GapExtractor extractor = new GapExtractor();

    private static MatchResult.MatchResultBuilder match() {
        return MatchResult.builder().id(UUID.randomUUID());
    }

    private static JdIntelligence jd(List<String> preferredSkills) {
        return JdIntelligence.builder()
                .id(UUID.randomUUID())
                .jobDescriptionId(UUID.randomUUID())
                .preferredSkills(JdMapper.serializeList(preferredSkills))
                .build();
    }

    @Test
    void extractsAllThreeGapTypes() {
        MatchResult matchResult = match()
                .mustHaveMissing(JdMapper.serializeList(List.of("Production Kubernetes")))
                .skillsMissing(JdMapper.serializeList(List.of("Terraform")))
                .skillsMatched(JdMapper.serializeList(List.of("Java", "Kafka")))
                .preferredMatched(JdMapper.serializeList(List.of("Kafka")))
                .build();

        ExtractedGaps gaps = extractor.extract(matchResult, jd(List.of("Kafka", "GraphQL", "Redis")));

        assertThat(gaps.missingMustHaves()).containsExactly("Production Kubernetes");
        assertThat(gaps.missingSkills()).containsExactly("Terraform");
        // Kafka was matched, so only the two genuinely absent preferred skills remain.
        assertThat(gaps.preferredMissing()).containsExactly("GraphQL", "Redis");
        assertThat(gaps.total()).isEqualTo(4);
        assertThat(gaps.isEmpty()).isFalse();
    }

    @Test
    void preferredSkillsAreFilteredByMatchedSkillsCaseInsensitively() {
        MatchResult matchResult = match()
                .skillsMatched(JdMapper.serializeList(List.of("apache kafka", "REDIS")))
                .build();

        ExtractedGaps gaps = extractor.extract(matchResult, jd(List.of("Kafka", "Redis", "GraphQL")));

        // "Kafka" is contained in "apache kafka", and case must not matter — the two
        // lists come from different sources describing the same skill.
        assertThat(gaps.preferredMissing()).containsExactly("GraphQL");
    }

    @Test
    void perfectMatchYieldsEmptyGaps() {
        MatchResult matchResult = match()
                .mustHaveMissing(JdMapper.serializeList(List.of()))
                .skillsMissing(JdMapper.serializeList(List.of()))
                .skillsMatched(JdMapper.serializeList(List.of("Java", "Kafka")))
                .build();

        ExtractedGaps gaps = extractor.extract(matchResult, jd(List.of("Kafka")));

        assertThat(gaps.isEmpty()).isTrue();
        assertThat(gaps.total()).isZero();
    }

    @Test
    void nullJsonColumnsAreTreatedAsEmptyListsWithoutThrowing() {
        // A match row written before a column existed, or a FAILED analysis, leaves
        // these null. A gap extractor that NPEs on that would take the whole request
        // down for a recoverable condition.
        MatchResult matchResult = match().build();
        JdIntelligence intelligence = JdIntelligence.builder()
                .id(UUID.randomUUID()).jobDescriptionId(UUID.randomUUID()).build();

        ExtractedGaps gaps = extractor.extract(matchResult, intelligence);

        assertThat(gaps.missingMustHaves()).isEmpty();
        assertThat(gaps.missingSkills()).isEmpty();
        assertThat(gaps.preferredMissing()).isEmpty();
        assertThat(gaps.isEmpty()).isTrue();
    }

    @Test
    void malformedJsonDegradesToEmptyRatherThanThrowing() {
        MatchResult matchResult = match().skillsMissing("not json at all").build();

        ExtractedGaps gaps = extractor.extract(matchResult, jd(List.of()));

        assertThat(gaps.missingSkills()).isEmpty();
    }

    @Test
    void jdWithNoPreferredSkillsYieldsNoPreferredGaps() {
        MatchResult matchResult = match()
                .skillsMissing(JdMapper.serializeList(List.of("Terraform")))
                .build();

        ExtractedGaps gaps = extractor.extract(matchResult, jd(List.of()));

        assertThat(gaps.preferredMissing()).isEmpty();
        assertThat(gaps.missingSkills()).containsExactly("Terraform");
    }
}
