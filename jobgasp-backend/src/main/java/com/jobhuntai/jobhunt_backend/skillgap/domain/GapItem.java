package com.jobhuntai.jobhunt_backend.skillgap.domain;

/**
 * One skill gap: what is missing, how much it matters, and how to close it.
 *
 * <p>A value object, not an entity — there is no {@code gap_item} table. Gaps live
 * as a JSON array inside {@code skill_gap.gaps}, which is a deliberate step up from
 * the flat string arrays of Phases 3 and 4: a gap is not a name, it is a name plus
 * a priority plus a rationale plus a recommendation, and splitting those across
 * parallel arrays would make them corruptible independently.
 *
 * <p>{@code estimatedWeeks} is nullable because "how long would this take" is
 * genuinely unanswerable for some gaps; a fabricated number would be worse than an
 * absent one, since it is what {@code quickWins} is computed from.
 */
public record GapItem(
        String skill,
        GapPriority priority,
        String reason,
        String learningRecommendation,
        Integer estimatedWeeks
) {
}
