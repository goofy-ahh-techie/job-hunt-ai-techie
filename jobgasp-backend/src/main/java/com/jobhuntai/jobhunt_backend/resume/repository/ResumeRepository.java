package com.jobhuntai.jobhunt_backend.resume.repository;

import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findAllByUserId(UUID userId);

    List<Resume> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Ownership-scoped lookup: returns empty if the resume does not exist
     * <em>or</em> is owned by another user — the caller maps that to a 404,
     * never leaking existence of other users' resumes.
     */
    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);
}
