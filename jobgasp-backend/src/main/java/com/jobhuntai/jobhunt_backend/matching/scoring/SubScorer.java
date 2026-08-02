package com.jobhuntai.jobhunt_backend.matching.scoring;

/**
 * One componentised dimension of a match score (strategy pattern).
 *
 * <p>Six implementations exist, one per locked sub-score. Each takes the same
 * {@link ScoringContext} and returns an independent 0–100 result plus its own
 * explanation; nothing about weighting, ordering, or combination lives here —
 * {@link SubScoreOrchestrator} runs them and {@link WeightedScoreCalculator}
 * combines them. Adding a seventh dimension is a new class and a weight, not an
 * edit to the ones already working.
 */
public interface SubScorer {

    /**
     * Score this dimension of the match.
     *
     * <p>Implementations must not throw for ordinary data conditions — an empty
     * JD list is a defined score, not an error. A genuine failure (a scorer's
     * remote dependency dying in a way it cannot degrade around) may throw; the
     * orchestrator isolates it so one dimension cannot abort the whole match.
     */
    SubScoreResult score(ScoringContext context);
}
