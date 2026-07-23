package com.jobhuntai.jobhunt_backend.resume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
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
 * A section-labelled slice of a {@link ResumeVersion}'s text. These are what the
 * downstream matching and skill-gap phases consume. References its parent version
 * by {@code UUID resumeVersionId}.
 *
 * <p>Implements {@link Persistable} for the same reason as {@link Resume}: an
 * application-assigned id would otherwise push {@code save()} onto the {@code merge()}
 * path.
 */
@Entity
@Table(name = "resume_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeChunk implements Persistable<UUID> {

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
        this.createdAt = Instant.now();
    }
}
