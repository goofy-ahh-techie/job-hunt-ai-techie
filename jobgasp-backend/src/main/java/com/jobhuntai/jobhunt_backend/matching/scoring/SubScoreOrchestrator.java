package com.jobhuntai.jobhunt_backend.matching.scoring;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs all six scorers over one {@link ScoringContext} and collects their results.
 *
 * <p>Each scorer is isolated: a scorer that throws is recorded as 0.0 with the
 * failure named in its explanation, and the other five still run. The reasoning is
 * that the dimensions are genuinely independent — a regex fault in experience
 * parsing says nothing about whether the skills matched — so aborting the whole
 * match would discard five correct answers to avoid reporting one wrong one. The
 * failure stays visible in the explanation rather than being smoothed over.
 *
 * <p>Note what is <em>not</em> isolated here: the intelligence service being down.
 * The two semantic scorers handle that themselves and degrade to keyword matching,
 * so it never reaches this class as an exception. This catch is for genuine defects.
 */
@Component
@RequiredArgsConstructor
public class SubScoreOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SubScoreOrchestrator.class);

    private final MustHaveCoverageScorer mustHaveCoverageScorer;
    private final RequiredSkillsScorer requiredSkillsScorer;
    private final ResponsibilitiesOverlapScorer responsibilitiesOverlapScorer;
    private final ExperienceLevelScorer experienceLevelScorer;
    private final QualificationsScorer qualificationsScorer;
    private final PreferredSkillsScorer preferredSkillsScorer;

    public OrchestratedScores orchestrate(ScoringContext context) {
        return new OrchestratedScores(
                runSafely(mustHaveCoverageScorer, context),
                runSafely(requiredSkillsScorer, context),
                runSafely(responsibilitiesOverlapScorer, context),
                runSafely(experienceLevelScorer, context),
                runSafely(qualificationsScorer, context),
                runSafely(preferredSkillsScorer, context));
    }

    private SubScoreResult runSafely(SubScorer scorer, ScoringContext context) {
        try {
            return scorer.score(context);
        } catch (Exception ex) {
            log.error("Sub-scorer {} failed; recording 0.0 and continuing",
                    scorer.getClass().getSimpleName(), ex);
            return SubScoreResult.failed(
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }
}
