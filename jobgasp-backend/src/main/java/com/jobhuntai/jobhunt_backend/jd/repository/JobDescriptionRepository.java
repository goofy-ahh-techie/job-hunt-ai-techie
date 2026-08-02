package com.jobhuntai.jobhunt_backend.jd.repository;

import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {

    List<JobDescription> findAllByUserId(UUID userId);

    List<JobDescription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Ownership-scoped lookup: returns empty if the JD does not exist <em>or</em> is
     * owned by another user — the caller maps that to a 404, never leaking existence
     * of other users' job descriptions.
     */
    Optional<JobDescription> findByIdAndUserId(UUID id, UUID userId);
}
