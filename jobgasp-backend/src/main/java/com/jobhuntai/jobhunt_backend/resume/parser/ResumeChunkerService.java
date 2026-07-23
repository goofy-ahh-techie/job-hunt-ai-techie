package com.jobhuntai.jobhunt_backend.resume.parser;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Splits extracted resume text into section-labelled chunks.
 *
 * <p><b>DSA note:</b> section detection is driven by a
 * {@code Map<SectionLabel, List<String>>} keyword table (a {@link LinkedHashMap}
 * for deterministic, first-match-wins iteration). Detection is O(lines × keywords),
 * and the body is assembled with a single linear pass — a streaming state machine
 * over the lines, flushing the current buffer whenever a new header is seen.
 */
@Service
public class ResumeChunkerService {

    /** A header line must be short — this rejects prose that merely mentions a keyword. */
    private static final int MAX_HEADER_WORDS = 4;

    /**
     * Canonical section → header-keyword lookup. First label whose keyword matches
     * a header line wins, so iteration order is fixed via LinkedHashMap.
     * OTHER has no keywords — it is the fallback for unmatched / header-less text.
     */
    private static final Map<SectionLabel, List<String>> SECTION_KEYWORDS = buildKeywordTable();

    private static Map<SectionLabel, List<String>> buildKeywordTable() {
        Map<SectionLabel, List<String>> table = new LinkedHashMap<>();
        table.put(SectionLabel.SUMMARY, List.of("summary", "profile", "objective", "about"));
        table.put(SectionLabel.EXPERIENCE, List.of("experience", "work history", "employment", "career"));
        table.put(SectionLabel.SKILLS, List.of("skills", "technical skills", "competencies", "technologies"));
        table.put(SectionLabel.EDUCATION, List.of("education", "academic", "qualifications", "degrees"));
        return table;
    }

    /**
     * Chunks raw text by section header. Returns an empty list for blank input.
     * When no headers are detected, the whole text is returned as a single
     * {@link SectionLabel#OTHER} chunk.
     */
    public List<ResumeChunkData> chunk(String rawText) {
        List<ResumeChunkData> chunks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return chunks;
        }

        // Pre-header text is OTHER until the first header switches the active section.
        SectionLabel currentLabel = SectionLabel.OTHER;
        List<String> buffer = new ArrayList<>();

        for (String line : rawText.split("\n", -1)) {
            Optional<SectionLabel> header = detectHeader(line);
            if (header.isPresent()) {
                flush(chunks, currentLabel, buffer);
                currentLabel = header.get();
            }
            buffer.add(line);
        }
        flush(chunks, currentLabel, buffer);

        return chunks;
    }

    /** Emits a chunk from the buffered lines if any real content accumulated, then clears it. */
    private void flush(List<ResumeChunkData> chunks, SectionLabel label, List<String> buffer) {
        String content = String.join("\n", buffer).strip();
        buffer.clear();
        if (content.isEmpty()) {
            return;
        }
        chunks.add(new ResumeChunkData(label, content, wordCount(content)));
    }

    /** Returns the section a line opens, or empty if the line is not a header. */
    private Optional<SectionLabel> detectHeader(String line) {
        String cleaned = normalize(line);
        if (cleaned.isEmpty() || wordCount(cleaned) > MAX_HEADER_WORDS) {
            return Optional.empty();
        }
        for (Map.Entry<SectionLabel, List<String>> entry : SECTION_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (containsWord(cleaned, keyword)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /** Lowercase and strip everything but letters/digits/spaces, collapsing runs of space. */
    private String normalize(String line) {
        return line.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** Whole-word (or whole-phrase) containment on already-normalized text. */
    private boolean containsWord(String normalized, String keyword) {
        return (" " + normalized + " ").contains(" " + keyword + " ");
    }

    /** Counts non-empty whitespace-delimited tokens. */
    private int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }
}
