package com.jobhuntai.jobhunt_backend.resume.repository;

import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {

    List<ResumeVersion> findAllByResumeId(UUID resumeId);

    /**
     * The current highest version for a resume. Used to compute the next
     * {@code version_number} (last + 1, or 1 when empty).
     */
    Optional<ResumeVersion> findTopByResumeIdOrderByVersionNumberDesc(UUID resumeId);
}
