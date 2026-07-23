package com.jobhuntai.jobhunt_backend.resume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Root of the resume aggregate — one row per uploaded file.
 *
 * <p>Other aggregates ({@link ResumeVersion}, {@link ResumeChunk}) reference this
 * by {@code UUID id}, not by JPA association: aggregates are linked by identity,
 * which keeps the object graph flat and avoids lazy-loading pitfalls.
 */
@Entity
@Table(name = "resume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

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
