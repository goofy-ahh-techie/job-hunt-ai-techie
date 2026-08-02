package com.jobhuntai.jobhunt_backend.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The scored, explainable match between one resume version and one job description.
 *
 * <p>Exactly one row exists per {@code (resume_version_id, job_description_id)} pair —
 * the UNIQUE constraint from changeset 012 enforces it, and recalculation updates that
 * row rather than appending a new one. {@code lastCalculatedAt} is what makes the
 * distinction visible: {@code createdAt} records when the pair was first scored,
 * {@code lastCalculatedAt} when the numbers currently in the row were produced.
 *
 * <p>The six sub-scores are stored individually alongside the weighted overall — a
 * locked architectural decision. An overall score alone cannot answer "why", and the
 * whole point of the matching engine is that it can.
 *
 * <p>The matched/missing lists are JSON-serialised {@code TEXT}, the same documented
 * tradeoff as {@code JdIntelligence}: the entity holds opaque JSON and
 * {@code MatchMapper} owns the codec. Only the keyword-driven lists are persisted —
 * must-have, required skills, preferred skills — because those are the actionable gaps
 * a user acts on; the prose-level detail lives in {@code scoreExplanation}.
 *
 * <p>Implements {@link Persistable} for the same assigned-id reason as the Phase 2/3
 * entities: without it, Spring Data sees a non-null id and routes {@code save()} to
 * {@code merge()}, which costs a SELECT and lands audit values on a detached copy.
 */
@Entity
@Table(name = "match_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResult implements Persistable<UUID> {

    // Assigned by the service, consistent with the rest of the codebase.
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "resume_version_id", nullable = false)
    private UUID resumeVersionId;

    @Column(name = "job_description_id", nullable = false)
    private UUID jobDescriptionId;

    // --- scores (NUMERIC(5,2) -> BigDecimal; 0.00-100.00) ---

    @Column(name = "overall_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "must_have_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal mustHaveScore;

    @Column(name = "required_skills_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal requiredSkillsScore;

    @Column(name = "responsibilities_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal responsibilitiesScore;

    @Column(name = "experience_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal experienceScore;

    @Column(name = "qualifications_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal qualificationsScore;

    @Column(name = "preferred_skills_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal preferredSkillsScore;

    // --- gap detail: JSON arrays serialised as TEXT; deserialised in the mapper ---

    @Column(name = "must_have_matched", columnDefinition = "TEXT")
    private String mustHaveMatched;

    @Column(name = "must_have_missing", columnDefinition = "TEXT")
    private String mustHaveMissing;

    @Column(name = "skills_matched", columnDefinition = "TEXT")
    private String skillsMatched;

    @Column(name = "skills_missing", columnDefinition = "TEXT")
    private String skillsMissing;

    @Column(name = "preferred_matched", columnDefinition = "TEXT")
    private String preferredMatched;

    @Column(name = "score_explanation", columnDefinition = "TEXT")
    private String scoreExplanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MatchStatus status;

    // Null until the first successful calculation completes; updated on every
    // recalculation, which is how a stale result is spotted.
    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean persisted = false;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = MatchStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
