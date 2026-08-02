package com.jobhuntai.jobhunt_backend.skillgap.extractor;

import java.util.List;

/**
 * The three gap lists derived from a match result, kept separate because the
 * separation <em>is</em> the priority: a must-have miss is CRITICAL, a required
 * skill miss is HIGH, a preferred skill miss is MEDIUM. Flattening them into one
 * list would throw away the only information the priority ranking is built from.
 */
public record ExtractedGaps(
        List<String> missingSkills,
        List<String> missingMustHaves,
        List<String> preferredMissing
) {

    public ExtractedGaps {
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
        missingMustHaves = missingMustHaves == null ? List.of() : List.copyOf(missingMustHaves);
        preferredMissing = preferredMissing == null ? List.of() : List.copyOf(preferredMissing);
    }

    public static ExtractedGaps empty() {
        return new ExtractedGaps(List.of(), List.of(), List.of());
    }

    /**
     * True when the match left nothing to close. The service short-circuits the
     * LLM call on this — asking a model to explain an empty list invites it to
     * invent one.
     */
    public boolean isEmpty() {
        return missingSkills.isEmpty() && missingMustHaves.isEmpty() && preferredMissing.isEmpty();
    }

    public int total() {
        return missingSkills.size() + missingMustHaves.size() + preferredMissing.size();
    }
}
