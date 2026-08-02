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
 * Root of the JD aggregate — one row per pasted or uploaded job description.
 *
 * <p>Its extracted intelligence lives in {@link JdIntelligence}, referenced by
 * {@code UUID id}, not by JPA association — the same identity-linked, flat-graph
 * approach the resume aggregate uses.
 *
 * <p>Implements {@link Persistable} because the id is application-assigned (the
 * file-upload path embeds the id in the storage key, so the service must know it
 * before insert). Without it, a non-null id would push {@code save()} through
 * {@code merge()} — an extra SELECT, and audit values set in {@code @PrePersist}
 * would land on the managed copy rather than the instance we hold. The
 * {@code persisted} flag makes {@code save()} use {@code persist()} for new rows.
 */
@Entity
@Table(name = "job_description")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDescription implements Persistable<UUID> {

    // Assigned by the service (UUID.randomUUID()), consistent with Resume/User.
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // User-provided label for the JD, not the extracted job title.
    @Column(nullable = false)
    private String title;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private JdSourceType sourceType;

    // File columns are null when sourceType == PASTE.
    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JdStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Not a column: drives Persistable#isNew so save() picks persist() over merge().
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
            this.status = JdStatus.UPLOADED;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
