package com.jobhuntai.jobhunt_backend.skillgap.repository;

import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillGapRepository extends JpaRepository<SkillGap, UUID> {

    /**
     * The single gap analysis for a match, backed by the UNIQUE constraint from
     * changeset 013. This is the upsert lookup: present means re-analyse in place,
     * absent means insert.
     */
    Optional<SkillGap> findByMatchResultId(UUID matchResultId);

    /**
     * Ownership-scoped lookup: empty if the analysis does not exist <em>or</em>
     * belongs to another user — the caller maps that to a 404, never leaking
     * existence.
     */
    Optional<SkillGap> findByIdAndUserId(UUID id, UUID userId);

    List<SkillGap> findAllByResumeIdAndUserIdOrderByLastAnalyzedAtDesc(
            UUID resumeId, UUID userId);

    List<SkillGap> findAllByJobDescriptionIdAndUserIdOrderByLastAnalyzedAtDesc(
            UUID jobDescriptionId, UUID userId);
}
