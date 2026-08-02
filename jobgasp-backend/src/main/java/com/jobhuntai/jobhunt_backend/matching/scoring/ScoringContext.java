package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;

import java.util.List;

/**
 * Everything a {@link SubScorer} is allowed to see: one resume version, its
 * section-labelled chunks, and the JD's extracted intelligence.
 *
 * <p>Loaded once by {@code MatchService} and shared across all six scorers, so a
 * match costs one read of each aggregate rather than six. Passing the loaded
 * state rather than repositories also keeps scorers free of persistence
 * concerns — they are pure functions over this record, which is what makes them
 * unit-testable without a database.
 *
 * <p>This is the {@code extracted facts → derived intelligence} boundary: both
 * inputs are already-extracted facts (Phase 2 chunks, Phase 3 intelligence), and
 * the scorers derive from them without touching raw text or the LLM's prose.
 */
public record ScoringContext(
        ResumeVersion resumeVersion,
        List<ResumeChunk> resumeChunks,
        JdIntelligence jdIntelligence
) {

    public ScoringContext {
        resumeChunks = resumeChunks == null ? List.of() : List.copyOf(resumeChunks);
    }

    /** Chunk contents, in order — the target texts for keyword and semantic passes. */
    public List<String> chunkContents() {
        return resumeChunks.stream()
                .map(ResumeChunk::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
    }
}
