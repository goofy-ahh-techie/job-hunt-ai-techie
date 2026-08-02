package com.jobhuntai.jobhunt_backend.matching.repository;

import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {

    /**
     * The single result for a (resume version, job description) pair, backed by the
     * UNIQUE constraint from changeset 012. This is the upsert lookup: present means
     * recalculate in place, absent means insert.
     */
    Optional<MatchResult> findByResumeVersionIdAndJobDescriptionId(
            UUID resumeVersionId, UUID jobDescriptionId);

    /** Dashboard list: the caller's matches, best score first. */
    List<MatchResult> findAllByUserIdOrderByOverallScoreDesc(UUID userId);

    List<MatchResult> findAllByUserId(UUID userId);

    /**
     * Ownership-scoped lookup: empty if the match does not exist <em>or</em> belongs
     * to another user — the caller maps that to a 404, never leaking existence.
     */
    Optional<MatchResult> findByIdAndUserId(UUID id, UUID userId);

    List<MatchResult> findAllByResumeIdAndUserIdOrderByOverallScoreDesc(
            UUID resumeId, UUID userId);

    List<MatchResult> findAllByJobDescriptionIdAndUserIdOrderByOverallScoreDesc(
            UUID jobDescriptionId, UUID userId);
}
