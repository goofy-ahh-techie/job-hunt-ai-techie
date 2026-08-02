package com.jobhuntai.jobhunt_backend.matching.scoring;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sub-score 4 (weight 12%) — does the candidate's experience span reach what the JD
 * asks for.
 *
 * <p>The only sub-score that is numeric rather than textual, and the only one whose
 * input the resume never states directly: nobody writes "7 years of experience" in a
 * parseable field, so the span is inferred from the date ranges on their roles. The
 * earliest start to the latest end is deliberately a *span*, not a sum — overlapping
 * roles (a job and a concurrent contract) would otherwise double-count into a
 * career length nobody has.
 *
 * <p>The banding degrades in steps rather than linearly. A year short of a "5 years"
 * ask is a rounding difference in how someone counts their own career; three years
 * short is a different candidate. A linear scale would blur that distinction, and the
 * blur sits exactly where the hiring decision is.
 */
@Component
public class ExperienceLevelScorer implements SubScorer {

    /**
     * A year range on a resume line: {@code 2019 - 2022}, {@code 2020–present}.
     * Hyphen, en dash, and em dash all appear in real resumes depending on whether
     * the editor auto-formatted them.
     */
    private static final Pattern YEAR_RANGE = Pattern.compile(
            "(\\d{4})\\s*[-–—]\\s*(\\d{4}|present|current|now)",
            Pattern.CASE_INSENSITIVE);

    /** Guards against a stray 4-digit number (a postcode, a metric) parsing as a year. */
    private static final int EARLIEST_PLAUSIBLE_YEAR = 1950;

    @Override
    public SubScoreResult score(ScoringContext context) {
        Integer requiredMin = context.jdIntelligence().getExperienceYearsMin();
        Integer requiredMax = context.jdIntelligence().getExperienceYearsMax();

        if (requiredMin == null || requiredMax == null) {
            // No stated requirement means nothing to fall short of. Scoring this
            // dimension down for a JD that never asked would penalise the candidate
            // for the job ad's vagueness.
            return SubScoreResult.scoreOnly(100.0, "No experience requirement specified");
        }

        int resumeYears = inferYearsOfExperience(context.chunkContents());
        double rawScore = band(resumeYears, requiredMin);

        return SubScoreResult.scoreOnly(rawScore,
                "Resume shows ~%d years experience; JD requires %d–%d years"
                        .formatted(resumeYears, requiredMin, requiredMax));
    }

    /**
     * Full marks at or above the minimum, then three steps down: within a year,
     * within two, and further than that.
     */
    private double band(int resumeYears, int requiredMin) {
        if (resumeYears >= requiredMin) {
            return 100.0;
        }
        if (resumeYears >= requiredMin - 1) {
            return 75.0;
        }
        if (resumeYears >= requiredMin - 2) {
            return 40.0;
        }
        return 10.0;
    }

    /**
     * Span from the earliest start year to the latest end year across all date
     * ranges in the resume. Returns 0 when no range parses — an unparseable resume
     * scores as unproven experience rather than as an error.
     */
    private int inferYearsOfExperience(List<String> chunkContents) {
        int currentYear = Year.now().getValue();
        int earliestStart = Integer.MAX_VALUE;
        int latestEnd = Integer.MIN_VALUE;

        for (String content : chunkContents) {
            Matcher matcher = YEAR_RANGE.matcher(content);
            while (matcher.find()) {
                int start = Integer.parseInt(matcher.group(1));
                if (start < EARLIEST_PLAUSIBLE_YEAR || start > currentYear) {
                    continue;
                }
                int end = parseEndYear(matcher.group(2), currentYear);
                if (end < start) {
                    // A backwards range is a parse artefact, not a role.
                    continue;
                }
                earliestStart = Math.min(earliestStart, start);
                latestEnd = Math.max(latestEnd, end);
            }
        }

        if (earliestStart == Integer.MAX_VALUE) {
            return 0;
        }
        return latestEnd - earliestStart;
    }

    private int parseEndYear(String rawEnd, int currentYear) {
        // "present" / "current" / "now" all mean the role is ongoing.
        if (!Character.isDigit(rawEnd.charAt(0))) {
            return currentYear;
        }
        int end = Integer.parseInt(rawEnd);
        // A future end year (a graduation date, say) is capped so it cannot inflate
        // the span beyond what has actually been worked.
        return Math.min(end, currentYear);
    }
}
