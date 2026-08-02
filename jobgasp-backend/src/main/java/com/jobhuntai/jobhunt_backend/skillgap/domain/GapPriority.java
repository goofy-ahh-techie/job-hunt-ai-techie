package com.jobhuntai.jobhunt_backend.skillgap.domain;

import java.util.Locale;

/**
 * How much a single skill gap matters for the role it was found against.
 *
 * <p>Priority is decided by provenance, not severity judgement: an item is
 * {@code CRITICAL} because it came from the JD's must-haves, {@code HIGH} because
 * it came from required skills, {@code MEDIUM} because it was merely preferred.
 * {@code LOW} is reserved for gaps the model inferred rather than ones detected by
 * the matching engine. The Python service enforces that mapping before the data
 * crosses the boundary, so this enum only has to name the four values.
 *
 * <p>Declared in ascending urgency order so {@link #compareTo} sorts a gap list
 * the way the API reports it — most urgent first.
 */
public enum GapPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    /**
     * Parse a priority string from the intelligence service, defaulting to
     * {@code MEDIUM}.
     *
     * <p>Defensive only: the Python schema already coerces to these four names.
     * An unrecognised value degrades rather than failing the whole analysis,
     * because the gap itself is real even when its label is not — the same
     * reasoning that keeps the Python side from dropping it.
     */
    public static GapPriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
