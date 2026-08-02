package com.jobhuntai.jobhunt_backend.jd.domain;

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

import java.time.Instant;
import java.util.UUID;

/**
 * The AI-extracted intelligence for one {@link JobDescription} (1:1, enforced by the
 * UNIQUE constraint on {@code job_description_id}). References its parent by
 * {@code UUID jobDescriptionId}, not by JPA association.
 *
 * <p>The list-valued fields (responsibilities, required_skills, and so on) are stored
 * as JSON-serialised {@code TEXT} — a documented Phase 3 tradeoff that avoids an array
 * column type and keeps the rows readable. Serialisation to/from {@code List<String>}
 * is the mapper's job; the entity holds the raw JSON string and knows nothing about it.
 *
 * <p>Implements {@link Persistable} for the same assigned-id reason as
 * {@link JobDescription}.
 */
@Entity
@Table(name = "jd_intelligence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JdIntelligence implements Persistable<UUID> {

    // Assigned by the service, consistent with the rest of the codebase.
    @Id
    private UUID id;

    @Column(name = "job_description_id", nullable = false, unique = true)
    private UUID jobDescriptionId;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 50)
    private EmploymentType employmentType;

    @Column(name = "experience_years_min")
    private Integer experienceYearsMin;

    @Column(name = "experience_years_max")
    private Integer experienceYearsMax;

    // --- List fields: JSON arrays serialised as TEXT; deserialised in the mapper. ---

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(name = "preferred_skills", columnDefinition = "TEXT")
    private String preferredSkills;

    @Column(columnDefinition = "TEXT")
    private String qualifications;

    @Column(name = "must_have", columnDefinition = "TEXT")
    private String mustHave;

    @Column(name = "nice_to_have", columnDefinition = "TEXT")
    private String niceToHave;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "raw_summary", columnDefinition = "TEXT")
    private String rawSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 50)
    private JdExtractionStatus extractionStatus;

    // Populated only when extractionStatus == FAILED.
    @Column(name = "extraction_error", columnDefinition = "TEXT")
    private String extractionError;

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
        if (this.extractionStatus == null) {
            this.extractionStatus = JdExtractionStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
