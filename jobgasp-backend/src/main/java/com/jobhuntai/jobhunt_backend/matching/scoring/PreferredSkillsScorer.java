package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sub-score 6 (weight 5%) — nice-to-haves the candidate happens to bring.
 *
 * <p>A bonus dimension, and the empty case is where that shows. Every other scorer
 * returns 100 when the JD lists nothing, because "nothing was required" means
 * "nothing is missing". Here that reasoning inverts: a JD with no preferred skills
 * offers no bonus to earn, so the score is 0. Awarding full marks instead would hand
 * five free points to every candidate for a JD that simply did not list preferences —
 * inflating matches against vague postings over precise ones.
 *
 * <p>The score never goes negative and no {@code missing} list is reported: an
 * unmatched preferred skill is not a gap the user should be told to close.
 */
@Component
public class PreferredSkillsScorer implements SubScorer {

    @Override
    public SubScoreResult score(ScoringContext context) {
        List<String> preferredSkills =
                JdMapper.deserializeList(context.jdIntelligence().getPreferredSkills());

        if (preferredSkills.isEmpty()) {
            return SubScoreResult.scoreOnly(0.0, "No preferred skills specified (no bonus)");
        }

        KeywordMatcher.Partition partition =
                KeywordMatcher.partition(preferredSkills, context.chunkContents());

        return SubScoreResult.of(
                partition.percentage(0.0),
                partition.matched(),
                List.of(),
                "%d of %d preferred skills found (bonus)"
                        .formatted(partition.matched().size(), partition.total()));
    }
}
