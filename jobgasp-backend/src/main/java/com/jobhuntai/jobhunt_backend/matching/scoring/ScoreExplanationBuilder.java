package com.jobhuntai.jobhunt_backend.matching.scoring;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Renders a scoring run as the human-readable summary stored on the match.
 *
 * <p>The explanation is the deliverable, not a log line. A number alone tells a user
 * they scored 62 and nothing about what to do next; this text names which dimension
 * cost them the points, which is the only part that is actionable. Each scorer
 * already produced its own sentence — this class only orders them and marks each with
 * how it fared, so the wording of a dimension stays owned by the scorer that
 * understands it.
 */
@Component
public class ScoreExplanationBuilder {

    private static final double STRONG = 75.0;
    private static final double PARTIAL = 50.0;

    public String build(OrchestratedScores scores, double overallScore) {
        StringBuilder builder = new StringBuilder();
        // Two decimals for the headline (it is the stored NUMERIC(5,2) value), one
        // for the components — a sub-score's second decimal is noise next to the
        // sentence explaining it.
        builder.append("Overall match: %s/100%n"
                .formatted(String.format(Locale.ROOT, "%.2f", overallScore)));

        appendLine(builder, "Must-have coverage", scores.mustHave());
        appendLine(builder, "Required skills", scores.requiredSkills());
        appendLine(builder, "Responsibilities", scores.responsibilities());
        appendLine(builder, "Experience", scores.experience());
        appendLine(builder, "Qualifications", scores.qualifications());
        // Bonus dimension: always marked '+', because a low preferred-skills score is
        // not a shortfall and marking it '✗' would read as one.
        appendMarked(builder, "+", "Preferred skills", scores.preferredSkills());

        return builder.toString().stripTrailing();
    }

    private void appendLine(StringBuilder builder, String label, SubScoreResult result) {
        appendMarked(builder, marker(result.rawScore()), label, result);
    }

    private void appendMarked(StringBuilder builder, String marker, String label,
                              SubScoreResult result) {
        builder.append("%s %s: %s — %s%n".formatted(
                marker, label, format(result.rawScore()), result.explanation()));
    }

    private String marker(double score) {
        if (score >= STRONG) {
            return "✓";
        }
        return score >= PARTIAL ? "~" : "✗";
    }

    /** Locale-fixed so the decimal separator cannot vary with the host's locale. */
    private String format(double score) {
        return String.format(Locale.ROOT, "%.1f", score);
    }
}
