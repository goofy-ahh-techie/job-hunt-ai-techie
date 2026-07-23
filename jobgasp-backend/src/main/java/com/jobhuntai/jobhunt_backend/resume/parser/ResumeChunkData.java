package com.jobhuntai.jobhunt_backend.resume.parser;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;

/**
 * Internal carrier for a chunked resume section, produced by
 * {@link ResumeChunkerService}. The {@code chunk_index} is intentionally absent —
 * ordering is the list's responsibility and the index is assigned when persisted.
 */
public record ResumeChunkData(
        SectionLabel sectionLabel,
        String content,
        int wordCount
) {
}
