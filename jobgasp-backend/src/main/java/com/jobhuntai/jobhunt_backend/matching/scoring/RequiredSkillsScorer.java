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
 * Sub-score 2 (weight 25%) — how many of the JD's required skills the resume shows,
 * by name or by meaning.
 *
 * <p>Two passes, cheap first. Keyword containment settles the skills a resume spells
 * out verbatim, which is most of them and costs nothing. Only the leftovers go to the
 * embedding service, where "container orchestration" can still satisfy "Kubernetes".
 * Running the semantic pass over the full list instead would embed dozens of phrases
 * to re-confirm matches already proven — the ordering is a cost decision, not a
 * correctness one, since a keyword hit and a semantic hit both mean "present".
 *
 * <p>When the intelligence service is down the keyword score stands and the
 * explanation says so. A degraded score with a note beats a 503: the user asked for a
 * match, and four fifths of an answer is worth more than none.
 */
@Component
public class RequiredSkillsScorer implements SubScorer {

    private static final Logger log = LoggerFactory.getLogger(RequiredSkillsScorer.class);

    /**
     * Cosine floor for "this skill is present", measured rather than assumed.
     *
     * <p>Calibrated against nomic-embed-text with real resume prose. True positives
     * ("Kubernetes" against "operated container orchestration clusters") land at
     * 0.52–0.60; unrelated skills (Rust, COBOL, Salesforce) land at 0.35–0.45. 0.50
     * sits in that gap with margin on both sides.
     *
     * <p>Deliberately <em>lower</em> than the responsibilities threshold, which
     * inverts the intuition that a bare skill noun is an easier match than a
     * sentence. It is not: a two-word phrase compared against a paragraph scores
     * lower than two paragraphs compared to each other, because length asymmetry
     * pulls the vectors apart regardless of meaning. A 0.65 bar here rejected every
     * true positive in calibration.
     */
    static final double DEFAULT_SEMANTIC_THRESHOLD = 0.50;

    static final String DEGRADED_NOTE = "Semantic matching unavailable — keyword match only";

    private final MatchingIntelligenceClient intelligenceClient;
    private final double semanticThreshold;

    public RequiredSkillsScorer(
            MatchingIntelligenceClient intelligenceClient,
            @Value("${matching.semantic.skills-threshold:" + DEFAULT_SEMANTIC_THRESHOLD + "}")
            double semanticThreshold) {
        this.intelligenceClient = intelligenceClient;
        this.semanticThreshold = semanticThreshold;
    }

    @Override
    public SubScoreResult score(ScoringContext context) {
        List<String> requiredSkills =
                JdMapper.deserializeList(context.jdIntelligence().getRequiredSkills());

        if (requiredSkills.isEmpty()) {
            return SubScoreResult.scoreOnly(100.0, "No required skills specified");
        }

        List<String> chunkContents = context.chunkContents();
        KeywordMatcher.Partition keyword =
                KeywordMatcher.partition(requiredSkills, chunkContents);

        List<String> matched = new ArrayList<>(keyword.matched());
        List<String> missing = new ArrayList<>(keyword.missing());
        int semanticMatches = 0;
        String note = "";

        if (!missing.isEmpty() && !chunkContents.isEmpty()) {
            try {
                // Passage-split targets, not whole chunks: a skill matches the one
                // line that evidences it, and would be averaged away against a
                // multi-topic chunk. See TextPassages.
                SemanticSimilarityResult semantic = intelligenceClient.semanticSimilarity(
                        List.copyOf(missing), TextPassages.split(chunkContents), semanticThreshold);
                List<String> semanticallyMatched = semantic.matchedPhrases();
                semanticMatches = semanticallyMatched.size();
                matched.addAll(semanticallyMatched);
                missing.removeAll(semanticallyMatched);
            } catch (IntelligenceServiceUnavailableException | SemanticMatchingFailedException ex) {
                // Deliberately caught, not propagated: this is the degradation the
                // whole two-pass design exists to allow. Re-throwing would turn a
                // partial answer into a failed match.
                log.warn("Semantic pass unavailable for required skills, keeping keyword score: {}",
                        ex.getMessage());
                note = " — " + DEGRADED_NOTE;
            }
        }

        int total = requiredSkills.size();
        double rawScore = (matched.size() * 100.0) / total;
        String semanticSuffix = semanticMatches > 0
                ? " (%d via semantic)".formatted(semanticMatches)
                : "";

        return SubScoreResult.of(
                rawScore,
                List.copyOf(matched),
                List.copyOf(missing),
                "%d of %d required skills matched%s%s"
                        .formatted(matched.size(), total, semanticSuffix, note));
    }
}
