package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.SemanticMatchingFailedException;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.client.MatchingIntelligenceClient;
import com.jobhuntai.jobhunt_backend.matching.client.SemanticSimilarityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-score 1 (weight 30%) — how many of the JD's hard requirements the resume
 * actually evidences.
 *
 * <p>The heaviest dimension, and the one where a keyword-only design failed loudest
 * in practice. Must-haves do not arrive as keywords: the Phase 3 extraction returns
 * them as whole sentences ("Strong experience with Java and Spring Boot in
 * production", "Experience owning services end to end"), and no resume contains those
 * sentences verbatim. A pure substring pass therefore scored 0 of 4 on a resume that
 * genuinely satisfied three of them — pinning 30% of every match at zero and filling
 * {@code must_have_missing} with the entire requirement list rather than real gaps.
 *
 * <p>So this scorer runs the same two-pass shape as {@link RequiredSkillsScorer}:
 * cheap keyword containment first (which still catches the occasional literal
 * requirement), then embeddings for whatever is left. The threshold matches the
 * responsibilities scorer's rather than the skills one's, because must-haves are
 * prose sentences and prose-to-prose comparisons sit higher.
 *
 * <p>What has <em>not</em> softened is the consequence: a genuinely unmet must-have
 * still costs the full weight. Screening criteria should hurt when missed; the fix
 * here was to stop reporting met requirements as missed.
 */
@Component
public class MustHaveCoverageScorer implements SubScorer {

    private static final Logger log = LoggerFactory.getLogger(MustHaveCoverageScorer.class);

    /** Must-haves are sentences, so they calibrate like responsibilities, not skills. */
    static final double DEFAULT_SEMANTIC_THRESHOLD = 0.60;

    static final String DEGRADED_NOTE = "Semantic matching unavailable — keyword match only";

    private final MatchingIntelligenceClient intelligenceClient;
    private final double semanticThreshold;

    public MustHaveCoverageScorer(
            MatchingIntelligenceClient intelligenceClient,
            @Value("${matching.semantic.must-have-threshold:" + DEFAULT_SEMANTIC_THRESHOLD + "}")
            double semanticThreshold) {
        this.intelligenceClient = intelligenceClient;
        this.semanticThreshold = semanticThreshold;
    }

    @Override
    public SubScoreResult score(ScoringContext context) {
        List<String> mustHave = JdMapper.deserializeList(context.jdIntelligence().getMustHave());

        if (mustHave.isEmpty()) {
            // A JD that states no hard requirements cannot be failed on them.
            // Full marks, not zero: the candidate is not lacking anything.
            return SubScoreResult.scoreOnly(100.0, "No must-have requirements specified");
        }

        List<String> chunkContents = context.chunkContents();
        KeywordMatcher.Partition keyword = KeywordMatcher.partition(mustHave, chunkContents);

        List<String> matched = new ArrayList<>(keyword.matched());
        List<String> missing = new ArrayList<>(keyword.missing());
        int semanticMatches = 0;
        String note = "";

        if (!missing.isEmpty() && !chunkContents.isEmpty()) {
            try {
                SemanticSimilarityResult semantic = intelligenceClient.semanticSimilarity(
                        List.copyOf(missing),
                        TextPassages.split(chunkContents),
                        semanticThreshold);
                List<String> semanticallyMatched = semantic.matchedPhrases();
                semanticMatches = semanticallyMatched.size();
                matched.addAll(semanticallyMatched);
                missing.removeAll(semanticallyMatched);
            } catch (IntelligenceServiceUnavailableException | SemanticMatchingFailedException ex) {
                // Degrade rather than fail: a keyword-only must-have score is poor,
                // but it is still a match the user can read, and the note says why
                // the number should not be trusted.
                log.warn("Semantic pass unavailable for must-haves, keeping keyword score: {}",
                        ex.getMessage());
                note = " — " + DEGRADED_NOTE;
            }
        }

        int total = mustHave.size();
        String semanticSuffix = semanticMatches > 0
                ? " (%d via semantic)".formatted(semanticMatches)
                : "";

        return SubScoreResult.of(
                (matched.size() * 100.0) / total,
                List.copyOf(matched),
                List.copyOf(missing),
                "%d of %d must-have requirements found in resume%s%s"
                        .formatted(matched.size(), total, semanticSuffix, note));
    }
}
