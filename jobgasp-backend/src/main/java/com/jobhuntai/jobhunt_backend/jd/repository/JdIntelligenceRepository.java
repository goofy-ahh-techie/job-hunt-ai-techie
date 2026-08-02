package com.jobhuntai.jobhunt_backend.jd.repository;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JdIntelligenceRepository extends JpaRepository<JdIntelligence, UUID> {

    /** One intelligence row per job description (enforced by a UNIQUE constraint). */
    Optional<JdIntelligence> findByJobDescriptionId(UUID jobDescriptionId);
}
