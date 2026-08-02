package com.jobhuntai.jobhunt_backend.skillgap.extractor;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives the raw gap lists from an already-computed match.
 *
 * <p>Phase 5 detects nothing of its own. Phase 4 already worked out which must-haves
 * and required skills the resume failed to evidence — including the semantic pass —
 * and persisted those lists. Re-deriving them here would mean running the whole
 * matching engine again to reach an answer already stored, and would risk the two
 * phases disagreeing about the same resume.
 *
 * <p>The one thing that <em>is</em> computed here is {@code preferredMissing}, because
 * Phase 4 deliberately does not persist it: an unmatched preferred skill is not a gap
 * a match should report, since missing a nice-to-have is not a shortfall. For gap
 * analysis it becomes a MEDIUM item worth mentioning, so it is reconstructed by
 * subtracting the matched skills from the JD's preferred list.
 */
@Component
public class GapExtractor {

    public ExtractedGaps extract(MatchResult matchResult, JdIntelligence jdIntelligence) {
        List<String> missingMustHaves = JdMapper.deserializeList(matchResult.getMustHaveMissing());
        List<String> missingSkills = JdMapper.deserializeList(matchResult.getSkillsMissing());
        List<String> preferredMissing = derivePreferredMissing(matchResult, jdIntelligence);

        return new ExtractedGaps(missingSkills, missingMustHaves, preferredMissing);
    }

    /**
     * JD preferred skills minus the ones the match already found.
     *
     * <p>Matched skills are compared case-insensitively and by containment, because
     * the two lists come from different places — the JD's wording and the matcher's
     * echo of it — and "Kafka" should not be reported missing when the match recorded
     * "Apache Kafka".
     */
    private List<String> derivePreferredMissing(MatchResult matchResult,
                                                JdIntelligence jdIntelligence) {
        List<String> preferred = JdMapper.deserializeList(jdIntelligence.getPreferredSkills());
        if (preferred.isEmpty()) {
            return List.of();
        }

        // Both sides of the match are consulted: skillsMatched covers required
        // skills the resume evidenced, preferredMatched covers the bonus scorer's
        // own hits. A skill found by either is not missing.
        Set<String> matched = normalise(JdMapper.deserializeList(matchResult.getSkillsMatched()));
        matched.addAll(normalise(JdMapper.deserializeList(matchResult.getPreferredMatched())));

        return preferred.stream()
                .filter(skill -> skill != null && !skill.isBlank())
                .filter(skill -> !isMatched(skill, matched))
                .toList();
    }

    private Set<String> normalise(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

    private boolean isMatched(String skill, Set<String> matched) {
        String needle = skill.toLowerCase(Locale.ROOT).trim();
        for (String candidate : matched) {
            if (candidate.equals(needle) || candidate.contains(needle) || needle.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
