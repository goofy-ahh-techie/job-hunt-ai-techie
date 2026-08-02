package com.jobhuntai.jobhunt_backend.skillgap.domain;

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
 * The prioritised skill gap analysis for one {@code MatchResult} (1:1, enforced by the
 * UNIQUE constraint on {@code match_result_id}). Re-analysis updates this row rather
 * than appending, with {@code lastAnalyzedAt} recording when the current content was
 * produced.
 *
 * <p>{@code resumeId} and {@code jobDescriptionId} are denormalised from the match so
 * the "gaps for this resume" and "gaps for this JD" queries stay single-table.
 * {@code overallScoreContext} is likewise a copy, not a lookup: recalculating the match
 * changes its score, and these gaps were reasoned about against the old one.
 *
 * <p>{@code gaps} holds a JSON array of {@link GapItem} <em>objects</em> — richer than
 * the flat string arrays of Phases 3 and 4. {@code SkillGapMapper} owns that codec;
 * the entity stores opaque text and knows nothing about it.
 *
 * <p>Implements {@link Persistable} for the same assigned-id reason as every entity
 * since Phase 2.
 */
@Entity
@Table(name = "skill_gap")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillGap implements Persistable<UUID> {

    // Assigned by the service, consistent with the rest of the codebase.
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "match_result_id", nullable = false, unique = true)
    private UUID matchResultId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "job_description_id", nullable = false)
    private UUID jobDescriptionId;

    @Column(name = "gap_summary", columnDefinition = "TEXT")
    private String gapSummary;

    // JSON array of GapItem objects; deserialised in the mapper.
    @Column(columnDefinition = "TEXT")
    private String gaps;

    @Column(name = "quick_wins", columnDefinition = "TEXT")
    private String quickWins;

    @Column(name = "deal_breakers", columnDefinition = "TEXT")
    private String dealBreakers;

    @Column(name = "overall_score_context", precision = 5, scale = 2)
    private BigDecimal overallScoreContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SkillGapStatus status;

    // Populated only when status == FAILED.
    @Column(name = "analysis_error", columnDefinition = "TEXT")
    private String analysisError;

    @Column(name = "last_analyzed_at")
    private Instant lastAnalyzedAt;

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
            this.status = SkillGapStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
