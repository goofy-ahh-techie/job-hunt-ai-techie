package com.jobhuntai.jobhunt_backend.matching.scoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Splits resume chunk text into passage-sized units for semantic comparison.
 *
 * <p>Exists because of a measured failure, not a theory. An embedding is one vector
 * for whatever text it is given, so a 158-word EXPERIENCE chunk covering five
 * different achievements produces the <em>average</em> of those topics. A specific JD
 * phrase then matches that average weakly even when the chunk plainly contains the
 * thing: "Mentor and grow junior engineers" scored 0.74 against the single sentence
 * about coaching juniors, but only 0.55 against the whole chunk that sentence lives
 * in — under the threshold, and scored as a miss.
 *
 * <p>Splitting to bullets and sentences restores the signal: each passage is about
 * one thing, so a match lands on that passage instead of being averaged away. It also
 * costs more embedding calls, which is why the result is capped and deduplicated.
 *
 * <p>Keyword matching does <em>not</em> use this — plain substring search over whole
 * text is unaffected by chunk size and cheaper left as is.
 */
final class TextPassages {

    /** Shorter than this and a fragment carries no meaning worth embedding. */
    private static final int MIN_PASSAGE_LENGTH = 25;

    /**
     * Long passages are re-split rather than embedded whole — this is the dilution
     * threshold the class exists to avoid. Roughly two sentences of resume prose;
     * beyond that a single vector is already averaging distinct achievements.
     */
    private static final int MAX_PASSAGE_LENGTH = 250;

    /** Bounds the embedding cost of a pathologically long resume. */
    private static final int MAX_PASSAGES = 60;

    private TextPassages() {
    }

    /**
     * Flatten chunk contents into deduplicated, roughly one-idea passages.
     *
     * <p>Falls back to the original texts when splitting yields nothing usable, so a
     * resume of unusually terse lines is never reduced to an empty target set.
     */
    static List<String> split(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        // LinkedHashSet: deduplicate repeated lines (headers, contact rows) while
        // keeping resume order, which keeps the excerpt a caller sees predictable.
        LinkedHashSet<String> passages = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String line : text.split("\\R")) {
                addPassage(passages, line);
            }
        }

        if (passages.isEmpty()) {
            return texts.stream().filter(t -> t != null && !t.isBlank()).toList();
        }
        return passages.stream().limit(MAX_PASSAGES).toList();
    }

    private static void addPassage(LinkedHashSet<String> passages, String line) {
        String trimmed = line.strip();
        if (trimmed.length() < MIN_PASSAGE_LENGTH) {
            return;
        }
        if (trimmed.length() <= MAX_PASSAGE_LENGTH) {
            passages.add(trimmed);
            return;
        }
        // A long line is usually several sentences that a PDF or DOCX extractor ran
        // together; split on sentence ends so each lands on its own vector.
        for (String sentence : splitSentences(trimmed)) {
            if (sentence.length() >= MIN_PASSAGE_LENGTH) {
                passages.add(sentence);
            }
        }
    }

    /**
     * Split after '.', '!', '?' or ';' followed by whitespace. Deliberately naive:
     * an over-eager split on "99.95%" costs one slightly odd passage, whereas a
     * general sentence tokeniser would be a dependency and a tuning surface for a
     * gain that does not show up in the scores.
     */
    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String candidate : text.split("(?<=[.!?;])\\s+")) {
            String trimmed = candidate.strip();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }
}
