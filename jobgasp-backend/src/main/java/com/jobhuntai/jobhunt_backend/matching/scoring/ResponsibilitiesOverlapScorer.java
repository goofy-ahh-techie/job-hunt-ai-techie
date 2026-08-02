package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.SemanticMatchingFailedException;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.client.MatchingIntelligenceClient;
import com.jobhuntai.jobhunt_backend.matching.client.SemanticSimilarityResult;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sub-score 3 (weight 20%) — how much of what the job involves the candidate has
 * demonstrably done.
 *
 * <p>Semantic-first, unlike the skill scorers. A responsibility is a sentence
 * ("Own the reliability of a high-traffic payments API"), and nobody writes that
 * sentence into their resume verbatim; keyword containment would score almost every
 * real pairing at zero. The threshold is correspondingly lower than the skill
 * scorer's — prose paraphrases sit further apart in embedding space than
 * near-synonymous skill nouns do.
 *
 * <p>Scoped to {@code EXPERIENCE} chunks when the resume has them, because that is
 * where "what I have done" lives; a responsibility matching against a skills list is
 * a claim of familiarity, not of having done the work. When the chunker found no
 * EXPERIENCE section the whole resume is used rather than scoring zero on a
 * sectioning artefact.
 */
@Component
public class ResponsibilitiesOverlapScorer implements SubScorer {

    private static final Logger log = LoggerFactory.getLogger(ResponsibilitiesOverlapScorer.class);

    /**
     * Cosine floor for "the candidate has done this", measured against
     * nomic-embed-text with real resume prose. Sentence-to-paragraph true positives
     * land at 0.65–0.74 and out-of-domain responsibilities (social media, clinical
     * trials) at 0.43–0.47, so 0.60 clears the negatives with room to spare.
     *
     * <p>Higher than the skills threshold, not lower: comparing prose to prose is
     * the <em>easier</em> match, because both sides carry comparable context. See
     * {@link RequiredSkillsScorer#DEFAULT_SEMANTIC_THRESHOLD}.
     */
    static final double DEFAULT_SEMANTIC_THRESHOLD = 0.60;

    static final String DEGRADED_NOTE = "Semantic matching unavailable — keyword match only";

    private final MatchingIntelligenceClient intelligenceClient;
    private final double semanticThreshold;

    public ResponsibilitiesOverlapScorer(
            MatchingIntelligenceClient intelligenceClient,
            @Value("${matching.semantic.responsibilities-threshold:" + DEFAULT_SEMANTIC_THRESHOLD + "}")
            double semanticThreshold) {
        this.intelligenceClient = intelligenceClient;
        this.semanticThreshold = semanticThreshold;
    }

    @Override
    public SubScoreResult score(ScoringContext context) {
        List<String> responsibilities =
                JdMapper.deserializeList(context.jdIntelligence().getResponsibilities());

        if (responsibilities.isEmpty()) {
            return SubScoreResult.scoreOnly(100.0, "No responsibilities specified");
        }

        List<String> targets = experienceTexts(context);
        if (targets.isEmpty()) {
            return SubScoreResult.of(0.0, List.of(), responsibilities,
                    "0 of %d responsibilities overlapped — resume has no readable content"
                            .formatted(responsibilities.size()));
        }

        try {
            // Passage-split, not whole chunks: a responsibility matches the specific
            // achievement that evidences it. Measured — "mentor junior engineers"
            // scores 0.74 against the sentence about coaching juniors but 0.55
            // against the whole EXPERIENCE chunk containing it. See TextPassages.
            SemanticSimilarityResult semantic = intelligenceClient.semanticSimilarity(
                    responsibilities, TextPassages.split(targets), semanticThreshold);
            return SubScoreResult.of(
                    semantic.matchPercentage(),
                    semantic.matchedPhrases(),
                    semantic.unmatchedPhrases(),
                    "%d of %d responsibilities overlapped"
                            .formatted(semantic.matchCount(), responsibilities.size()));
        } catch (IntelligenceServiceUnavailableException | SemanticMatchingFailedException ex) {
            // Keyword fallback will score low here — full sentences rarely appear
            // verbatim — but a low real number with a stated caveat is honest, and
            // the alternative is failing a match the user can still act on.
            log.warn("Semantic pass unavailable for responsibilities, falling back to keyword: {}",
                    ex.getMessage());
            KeywordMatcher.Partition keyword = KeywordMatcher.partition(responsibilities, targets);
            return SubScoreResult.of(
                    keyword.percentage(100.0),
                    keyword.matched(),
                    keyword.missing(),
                    "%d of %d responsibilities overlapped — %s"
                            .formatted(keyword.matched().size(), keyword.total(), DEGRADED_NOTE));
        }
    }

    /**
     * EXPERIENCE chunk contents, or every chunk when the resume has no EXPERIENCE
     * section — a chunker that failed to find the header should not be scored as an
     * absence of experience.
     */
    private List<String> experienceTexts(ScoringContext context) {
        List<String> experience = context.resumeChunks().stream()
                .filter(chunk -> chunk.getSectionLabel() == SectionLabel.EXPERIENCE)
                .map(ResumeChunk::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        return experience.isEmpty() ? context.chunkContents() : experience;
    }
}
