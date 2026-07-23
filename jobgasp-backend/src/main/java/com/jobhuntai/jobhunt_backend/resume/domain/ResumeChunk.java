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
 * A section-labelled slice of a {@link ResumeVersion}'s text. These are what the
 * downstream matching and skill-gap phases consume. References its parent version
 * by {@code UUID resumeVersionId}.
 */
@Entity
@Table(name = "resume_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeChunk {

    // Assigned by the service, consistent with Resume/User.
    @Id
    private UUID id;

    @Column(name = "resume_version_id", nullable = false)
    private UUID resumeVersionId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_label", length = 100)
    private SectionLabel sectionLabel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
