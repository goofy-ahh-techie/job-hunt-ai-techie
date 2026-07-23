package com.jobhuntai.jobhunt_backend.resume.service;

import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResumeProcessingException;
import com.jobhuntai.jobhunt_backend.resume.domain.ExtractionStatus;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeStatus;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeChunkResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeUploadRequest;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeVersionResponse;
import com.jobhuntai.jobhunt_backend.resume.mapper.ResumeMapper;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeChunkData;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeChunkerService;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeTextExtractorService;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
import com.jobhuntai.jobhunt_backend.resume.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates resume upload → storage → extraction → chunking → persistence, plus
 * ownership-scoped reads.
 *
 * <p>Intentionally not {@code @Transactional} at the method level for uploads: file
 * I/O runs outside any DB transaction, and the FAILED-recording write must survive
 * the failure that triggered it. Atomic DB writes are delegated to
 * {@link ResumePersistenceService}. Reads are read-only transactions.
 */
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractorService textExtractorService;
    private final ResumeChunkerService chunkerService;
    private final ResumePersistenceService persistenceService;

    /**
     * Full upload pipeline. File is stored first (a storage failure leaves no DB
     * state); on any processing failure after storage the resume is recorded as
     * FAILED and a {@link ResumeProcessingException} (422) is raised.
     */
    public ResumeResponse uploadResume(UUID userId, ResumeUploadRequest request) {
        MultipartFile file = request.file();
        FileType fileType = fileStorageService.validate(file);

        UUID resumeId = UUID.randomUUID();
        String storageKey = fileStorageService.store(file, userId, resumeId);

        Resume resume = Resume.builder()
                .id(resumeId)
                .userId(userId)
                .title(request.title())
                .fileName(resolveFileName(file))
                .fileType(fileType)
                .fileSizeBytes(file.getSize())
                .storagePath(storageKey)
                .status(ResumeStatus.UPLOADED)
                .build();

        try {
            Path filePath = fileStorageService.resolve(storageKey);
            String rawText = textExtractorService.extract(filePath, fileType);

            ResumeVersion version = buildFirstVersion(resume.getId(), rawText);
            List<ResumeChunk> chunks = buildChunks(version.getId(), rawText);

            resume.setStatus(ResumeStatus.PARSED);
            persistenceService.saveParsed(resume, version, chunks);
            return ResumeMapper.toResponse(resume);
        } catch (RuntimeException ex) {
            // The one deliberate catch in this service: it performs state
            // compensation (record the failed attempt so it is auditable), not HTTP
            // error mapping — that remains the global handler's job. Because this
            // method holds no transaction, the FAILED write commits independently.
            resume.setStatus(ResumeStatus.FAILED);
            persistenceService.saveResume(resume);
            throw new ResumeProcessingException("Failed to process resume: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> getResumes(UUID userId) {
        return ResumeMapper.toResponseList(resumeRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public ResumeResponse getResume(UUID resumeId, UUID userId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + resumeId));
        return ResumeMapper.toResponse(resume);
    }

    @Transactional(readOnly = true)
    public List<ResumeVersionResponse> getResumeVersions(UUID resumeId, UUID userId) {
        requireOwnedResume(resumeId, userId);
        return ResumeMapper.toVersionResponseList(resumeVersionRepository.findAllByResumeId(resumeId));
    }

    @Transactional(readOnly = true)
    public List<ResumeChunkResponse> getResumeChunks(UUID resumeId, UUID resumeVersionId, UUID userId) {
        requireOwnedResume(resumeId, userId);
        ResumeVersion version = resumeVersionRepository.findById(resumeVersionId)
                .filter(v -> v.getResumeId().equals(resumeId))
                .orElseThrow(() -> new ResourceNotFoundException("Resume version not found: " + resumeVersionId));
        return ResumeMapper.toChunkResponseList(
                resumeChunkRepository.findAllByResumeVersionIdOrderByChunkIndex(version.getId()));
    }

    private ResumeVersion buildFirstVersion(UUID resumeId, String rawText) {
        int nextVersion = resumeVersionRepository.findTopByResumeIdOrderByVersionNumberDesc(resumeId)
                .map(existing -> existing.getVersionNumber() + 1)
                .orElse(1);
        return ResumeVersion.builder()
                .id(UUID.randomUUID())
                .resumeId(resumeId)
                .versionNumber(nextVersion)
                .rawText(rawText)
                .wordCount(countWords(rawText))
                .charCount(rawText.length())
                .extractionStatus(ExtractionStatus.COMPLETED)
                .build();
    }

    private List<ResumeChunk> buildChunks(UUID versionId, String rawText) {
        List<ResumeChunkData> chunkData = chunkerService.chunk(rawText);
        List<ResumeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (ResumeChunkData data : chunkData) {
            chunks.add(ResumeChunk.builder()
                    .id(UUID.randomUUID())
                    .resumeVersionId(versionId)
                    .chunkIndex(index++)
                    .sectionLabel(data.sectionLabel())
                    .content(data.content())
                    .wordCount(data.wordCount())
                    .build());
        }
        return chunks;
    }

    /** Throws 404 if the resume does not exist or is not owned by the user. */
    private void requireOwnedResume(UUID resumeId, UUID userId) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Resume not found: " + resumeId);
        }
    }

    private String resolveFileName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String name = StringUtils.getFilename(StringUtils.cleanPath(original));
        return StringUtils.hasText(name) ? name : "resume";
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }
}
