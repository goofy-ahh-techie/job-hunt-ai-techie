package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.matching.client.PhraseMatchResult;
import com.jobhuntai.jobhunt_backend.matching.client.SemanticSimilarityResult;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builders for the scoring test fixtures. Kept in one place because every scorer test
 * needs the same two awkward things: a {@code JdIntelligence} whose list fields are
 * JSON text rather than lists, and section-labelled resume chunks.
 */
final class ScoringFixtures {

    private ScoringFixtures() {
    }

    /** A JD intelligence row with only the fields a given scorer reads populated. */
    static JdIntelligence.JdIntelligenceBuilder jd() {
        return JdIntelligence.builder()
                .id(UUID.randomUUID())
                .jobDescriptionId(UUID.randomUUID());
    }

    /** Serialise a list the way the entity stores it. */
    static String json(List<String> values) {
        return JdMapper.serializeList(values);
    }

    static ResumeChunk chunk(SectionLabel label, String content) {
        return ResumeChunk.builder()
                .id(UUID.randomUUID())
                .resumeVersionId(UUID.randomUUID())
                .chunkIndex(0)
                .sectionLabel(label)
                .content(content)
                .build();
    }

    static ScoringContext context(JdIntelligence intelligence, ResumeChunk... chunks) {
        ResumeVersion version = ResumeVersion.builder()
                .id(UUID.randomUUID())
                .resumeId(UUID.randomUUID())
                .versionNumber(1)
                .rawText("raw")
                .build();
        return new ScoringContext(version, List.of(chunks), intelligence);
    }

    /**
     * A canned semantic response: every phrase in {@code matchedPhrases} comes back
     * matched, everything else in {@code allPhrases} unmatched.
     */
    static SemanticSimilarityResult semanticResult(List<String> allPhrases,
                                                   List<String> matchedPhrases) {
        List<PhraseMatchResult> results = new ArrayList<>();
        AtomicInteger matches = new AtomicInteger();
        for (String phrase : allPhrases) {
            boolean matched = matchedPhrases.contains(phrase);
            if (matched) {
                matches.incrementAndGet();
            }
            results.add(new PhraseMatchResult(
                    phrase, matched, matched ? 0.9 : 0.1, matched ? "excerpt" : ""));
        }
        double percentage = allPhrases.isEmpty()
                ? 0.0
                : (matches.get() * 100.0) / allPhrases.size();
        return new SemanticSimilarityResult(results, matches.get(), percentage);
    }
}
