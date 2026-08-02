package com.jobhuntai.jobhunt_backend.matching.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Case-insensitive containment of JD phrases in resume text.
 *
 * <p>Four of the six scorers ask the same question — "which of these strings appear
 * anywhere in the resume?" — so the answer lives here once rather than four times.
 * It is also the fallback every semantic scorer degrades to when the Python service
 * is unreachable, which makes it the one matching primitive that must never fail.
 *
 * <p><strong>Known limitation.</strong> This is plain substring containment, so a
 * one- or two-character skill ("R", "Go") matches inside unrelated words, and
 * "Kubernetes" does not match "K8s". Both are the same underlying gap: skills are
 * compared as raw strings because they are not yet normalised. Closing it is the
 * {@code SkillRegistry} phase's job — a canonical skill layer is a locked
 * architectural decision precisely because string matching cannot carry this weight.
 * The semantic pass in {@code RequiredSkillsScorer} already covers the synonym half
 * of the problem when the intelligence service is up.
 */
final class KeywordMatcher {

    private KeywordMatcher() {
    }

    /** Which phrases were found, and which were not. */
    record Partition(List<String> matched, List<String> missing) {

        int total() {
            return matched.size() + missing.size();
        }

        /**
         * Coverage as a 0–100 percentage. An empty phrase list yields
         * {@code emptyScore} — the callers disagree on what "the JD asked for
         * nothing" means (full marks for requirements, no bonus for preferences),
         * so the decision stays with them.
         */
        double percentage(double emptyScore) {
            return total() == 0 ? emptyScore : (matched.size() * 100.0) / total();
        }
    }

    /**
     * Split {@code phrases} by whether each appears in any of {@code targetTexts}.
     *
     * <p>The targets are lowercased and joined once, not per phrase: an N-phrase
     * check over an M-chunk resume is then N searches rather than N×M.
     */
    static Partition partition(List<String> phrases, List<String> targetTexts) {
        return partition(phrases, targetTexts, Map.of());
    }

    /**
     * As {@link #partition(List, List)}, but a phrase also counts as found when any
     * of its {@code synonyms} entries appears. Used for degree abbreviations, where
     * "Bachelor's degree" and "B.Tech" are the same claim written two ways.
     *
     * @param synonyms lowercased phrase fragment → alternative fragments to accept
     */
    static Partition partition(List<String> phrases, List<String> targetTexts,
                               Map<String, List<String>> synonyms) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (phrases == null || phrases.isEmpty()) {
            return new Partition(List.of(), List.of());
        }

        String haystack = joinLowercased(targetTexts);

        for (String phrase : phrases) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            if (contains(haystack, phrase, synonyms)) {
                matched.add(phrase);
            } else {
                missing.add(phrase);
            }
        }
        return new Partition(List.copyOf(matched), List.copyOf(missing));
    }

    private static boolean contains(String haystack, String phrase,
                                    Map<String, List<String>> synonyms) {
        String needle = phrase.toLowerCase(Locale.ROOT).trim();
        if (haystack.contains(needle)) {
            return true;
        }
        // Synonym keys are matched as fragments of the phrase, not equality: a JD
        // writes "Bachelor's degree in Computer Science", never the bare token.
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (!needle.contains(entry.getKey())) {
                continue;
            }
            for (String alternative : entry.getValue()) {
                if (haystack.contains(alternative)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * One lowercased haystack. Chunks are separated by a newline so a phrase cannot
     * accidentally match across the seam between two unrelated sections.
     */
    private static String joinLowercased(List<String> targetTexts) {
        if (targetTexts == null || targetTexts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String text : targetTexts) {
            if (text != null && !text.isBlank()) {
                builder.append(text.toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        return builder.toString();
    }
}
