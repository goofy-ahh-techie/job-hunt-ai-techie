package com.jobhuntai.jobhunt_backend.resume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single extracted snapshot of a {@link Resume}. Versions let a user keep
 * multiple tailored variants of the same resume (locked decision: resume versioning).
 * References its parent resume by {@code UUID resumeId}.
 */
@Entity
@Table(name = "resume_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeVersion {

    // Assigned by the service, consistent with Resume/User.
    @Id
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(length = 255)
    private String label;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "char_count")
    private Integer charCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 50)
    private ExtractionStatus extractionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.extractionStatus == null) {
            this.extractionStatus = ExtractionStatus.PENDING;
        }
    }
}
