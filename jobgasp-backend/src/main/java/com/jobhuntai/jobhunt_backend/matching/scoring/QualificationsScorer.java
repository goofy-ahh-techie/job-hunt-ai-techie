package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Sub-score 5 (weight 8%) — formal qualifications: degrees, certifications.
 *
 * <p>Keyword matching with one addition the other keyword scorers do not need. A JD
 * asks for a "Bachelor's degree"; a resume says "B.Tech" or "B.S." Plain containment
 * scores that mismatch at zero, and it is not a real gap — the same credential is
 * simply named differently on the two documents. The abbreviation table below closes
 * the specific, closed, well-known set where that happens.
 *
 * <p>This is a deliberately narrow patch, not a general normalisation layer: it
 * covers degrees because degrees have a handful of standard abbreviations. Skills do
 * not, which is why they get an embedding pass instead, and why {@code SkillRegistry}
 * remains a phase of its own.
 */
@Component
public class QualificationsScorer implements SubScorer {

    /**
     * Fragment appearing in a JD qualification → forms that mean the same on a
     * resume. Keys and values are lowercase; matching is by containment, so
     * {@code "bachelor"} covers "Bachelor's" and "Bachelors" alike.
     */
    static final Map<String, List<String>> DEGREE_SYNONYMS = Map.of(
            "bachelor", List.of("b.s.", "b.sc", "bsc", "b.e.", "b.tech", "btech", "b.a.", " bs ", " be "),
            "master", List.of("m.s.", "m.sc", "msc", "m.tech", "mtech", "m.a.", " ms ", " me "),
            "phd", List.of("ph.d", "doctorate", "doctoral"),
            "mba", List.of("m.b.a", "master of business")
    );

    @Override
    public SubScoreResult score(ScoringContext context) {
        List<String> qualifications =
                JdMapper.deserializeList(context.jdIntelligence().getQualifications());

        if (qualifications.isEmpty()) {
            return SubScoreResult.scoreOnly(100.0, "No qualifications specified");
        }

        KeywordMatcher.Partition partition = KeywordMatcher.partition(
                qualifications, context.chunkContents(), DEGREE_SYNONYMS);

        String explanation = partition.missing().isEmpty()
                ? "All qualifications matched"
                : "%d of %d qualifications matched"
                        .formatted(partition.matched().size(), partition.total());

        return SubScoreResult.of(
                partition.percentage(100.0),
                partition.matched(),
                partition.missing(),
                explanation);
    }
}
