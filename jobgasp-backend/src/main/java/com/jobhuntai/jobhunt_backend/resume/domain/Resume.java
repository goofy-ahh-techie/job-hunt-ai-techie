package com.jobhuntai.jobhunt_backend.resume.domain;

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
 * Root of the resume aggregate — one row per uploaded file.
 *
 * <p>Other aggregates ({@link ResumeVersion}, {@link ResumeChunk}) reference this
 * by {@code UUID id}, not by JPA association: aggregates are linked by identity,
 * which keeps the object graph flat and avoids lazy-loading pitfalls.
 *
 * <p>Implements {@link Persistable} because the id is application-assigned: without
 * it, Spring Data would see a non-null id, assume the row exists, and route
 * {@code save()} through {@code merge()} (an extra SELECT, and the returned managed
 * instance — not the one we hold — is the one that gets {@code @PrePersist} audit
 * values). The {@code persisted} flag makes {@code save()} use {@code persist()} for
 * new rows so the in-hand instance is populated.
 */
@Entity
@Table(name = "resume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume implements Persistable<UUID> {

    // Id is assigned by the service (UUID.randomUUID()), matching the User entity
    // pattern. Deliberately not @UuidGenerator: the storage path embeds the id, so
    // the service must know it before the file is written and the row is inserted.
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 50)
    private FileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ResumeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Not a column: tracks whether this instance corresponds to a persisted row so
    // Persistable#isNew can drive save() to persist() vs merge().
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
            this.status = ResumeStatus.UPLOADED;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
