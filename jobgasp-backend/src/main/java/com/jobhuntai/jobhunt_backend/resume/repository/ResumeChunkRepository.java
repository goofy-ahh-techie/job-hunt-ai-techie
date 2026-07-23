package com.jobhuntai.jobhunt_backend.resume.repository;

import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResumeChunkRepository extends JpaRepository<ResumeChunk, UUID> {

    List<ResumeChunk> findAllByResumeVersionIdOrderByChunkIndex(UUID resumeVersionId);
}
