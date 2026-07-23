package com.jobhuntai.jobhunt_backend.resume.service;

import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the transactional boundaries for resume persistence. Kept as a separate bean
 * (not private methods on {@link ResumeService}) so Spring's proxy actually applies
 * {@code @Transactional} — self-invocation would bypass it.
 */
@Service
@RequiredArgsConstructor
public class ResumePersistenceService {

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeChunkRepository resumeChunkRepository;

    /**
     * Atomically persists a fully parsed resume and all three layers: the resume
     * row, its version, and every chunk. If any save fails, the whole thing rolls
     * back — no partially-parsed resume is ever left behind.
     */
    @Transactional
    public void saveParsed(Resume resume, ResumeVersion version, List<ResumeChunk> chunks) {
        resumeRepository.save(resume);
        resumeVersionRepository.save(version);
        resumeChunkRepository.saveAll(chunks);
    }

    /**
     * Persists a single resume row in its own transaction. Used to record a FAILED
     * attempt: because the caller ({@link ResumeService#uploadResume}) holds no outer
     * transaction, this commit survives the failure that triggered it.
     */
    @Transactional
    public void saveResume(Resume resume) {
        resumeRepository.save(resume);
    }
}
