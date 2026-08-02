package com.jobhuntai.jobhunt_backend.jd.service;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdExtractionFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.client.JdExtractionResult;
import com.jobhuntai.jobhunt_backend.jd.client.JdIntelligenceClient;
import com.jobhuntai.jobhunt_backend.jd.domain.EmploymentType;
import com.jobhuntai.jobhunt_backend.jd.domain.JdExtractionStatus;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.domain.JdSourceType;
import com.jobhuntai.jobhunt_backend.jd.domain.JdStatus;
import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import com.jobhuntai.jobhunt_backend.jd.dto.JdDetailResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdIntelligenceResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdPasteRequest;
import com.jobhuntai.jobhunt_backend.jd.dto.JdResponse;
import com.jobhuntai.jobhunt_backend.jd.mapper.JdMapper;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.jd.repository.JobDescriptionRepository;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeTextExtractorService;
import com.jobhuntai.jobhunt_backend.resume.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates JD ingestion (paste or file) → AI extraction → persistence, plus
 * ownership-scoped reads.
 *
 * <p>Not {@code @Transactional} at the class level: the intelligence call is a slow
 * remote HTTP request that must not hold a DB transaction open, and the FAILED-recording
 * writes must survive the failure that triggered them. Atomic DB writes are delegated to
 * {@link JdPersistenceService}. Reads are read-only transactions. Mirrors
 * {@code ResumeService}.
 *
 * <p>File uploads reuse Phase 2's {@link FileStorageService} and
 * {@link ResumeTextExtractorService} — a PDF/DOCX is a PDF/DOCX regardless of whether it
 * is a resume or a JD.
 */
@Service
@RequiredArgsConstructor
public class JdService {

    private final JobDescriptionRepository jobDescriptionRepository;
    private final JdIntelligenceRepository jdIntelligenceRepository;
    private final JdIntelligenceClient intelligenceClient;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractorService textExtractorService;
    private final JdPersistenceService persistenceService;

    /** Ingest a pasted JD: persist raw text, extract intelligence, persist result. */
    public JdDetailResponse ingestPaste(UUID userId, JdPasteRequest request) {
        return ingest(UUID.randomUUID(), userId, request.title(), request.rawText(),
                JdSourceType.PASTE, null, null, null);
    }

    /** Ingest an uploaded JD file: store, extract text, then the same pipeline as paste. */
    public JdDetailResponse ingestFile(UUID userId, String title, MultipartFile file) {
        FileType fileType = fileStorageService.validate(file);

        UUID jdId = UUID.randomUUID();
        String storageKey = fileStorageService.store(file, userId, jdId);
        Path filePath = fileStorageService.resolve(storageKey);
        String rawText = textExtractorService.extract(filePath, fileType);

        return ingest(jdId, userId, title, rawText, JdSourceType.FILE,
                resolveFileName(file), file.getSize(), storageKey);
    }

    @Transactional(readOnly = true)
    public List<JdResponse> getJds(UUID userId) {
        return JdMapper.toJdResponseList(
                jobDescriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public JdResponse getJd(UUID jdId, UUID userId) {
        return JdMapper.toJdResponse(requireOwnedJd(jdId, userId));
    }

    @Transactional(readOnly = true)
    public JdIntelligenceResponse getJdIntelligence(UUID jdId, UUID userId) {
        requireOwnedJd(jdId, userId);
        JdIntelligence intel = jdIntelligenceRepository.findByJobDescriptionId(jdId)
                .orElseThrow(() -> new JdIntelligenceNotFoundException(
                        "Intelligence not available for job description: " + jdId));
        return JdMapper.toJdIntelligenceResponse(intel);
    }

    @Transactional(readOnly = true)
    public JdDetailResponse getJdDetail(UUID jdId, UUID userId) {
        JobDescription jd = requireOwnedJd(jdId, userId);
        JdIntelligence intel = jdIntelligenceRepository.findByJobDescriptionId(jdId).orElse(null);
        return JdMapper.toJdDetailResponse(jd, intel);
    }

    /**
     * Shared ingest core. The JD is persisted as UPLOADED first, then extraction runs.
     * On success both the JD (→ EXTRACTED) and its intelligence (→ COMPLETED) are saved
     * atomically. On failure the JD is recorded FAILED — with a FAILED intelligence row
     * carrying the error when the service reached the model but extraction failed, or
     * with no intelligence row when the service was unreachable — and the original
     * exception is re-thrown for the global handler to map (422 / 503).
     */
    private JdDetailResponse ingest(UUID jdId, UUID userId, String title, String rawText,
                                    JdSourceType sourceType, String fileName,
                                    Long fileSizeBytes, String storagePath) {
        JobDescription jd = JobDescription.builder()
                .id(jdId)
                .userId(userId)
                .title(title)
                .rawText(rawText)
                .sourceType(sourceType)
                .fileName(fileName)
                .fileSizeBytes(fileSizeBytes)
                .storagePath(storagePath)
                .status(JdStatus.UPLOADED)
                .build();
        persistenceService.saveJobDescription(jd);

        try {
            JdExtractionResult result = intelligenceClient.extract(rawText);
            JdIntelligence intel = buildIntelligence(jdId, result);
            jd.setStatus(JdStatus.EXTRACTED);
            persistenceService.saveParsed(jd, intel);
            return JdMapper.toJdDetailResponse(jd, intel);
        } catch (JdExtractionFailedException ex) {
            // Service reached the model but could not produce a usable extraction:
            // record the JD and a FAILED intelligence row (with the reason) so the
            // failure is visible through the API, then re-throw → 422.
            jd.setStatus(JdStatus.FAILED);
            JdIntelligence failed = JdIntelligence.builder()
                    .id(UUID.randomUUID())
                    .jobDescriptionId(jdId)
                    .extractionStatus(JdExtractionStatus.FAILED)
                    .extractionError(ex.getMessage())
                    .build();
            persistenceService.saveParsed(jd, failed);
            throw ex;
        } catch (IntelligenceServiceUnavailableException ex) {
            // Service unreachable: no intelligence was produced, so record only the
            // FAILED JD, then re-throw → 503.
            jd.setStatus(JdStatus.FAILED);
            persistenceService.saveJobDescription(jd);
            throw ex;
        }
    }

    private JdIntelligence buildIntelligence(UUID jdId, JdExtractionResult r) {
        return JdIntelligence.builder()
                .id(UUID.randomUUID())
                .jobDescriptionId(jdId)
                .jobTitle(r.jobTitle())
                .companyName(r.companyName())
                .companyDescription(r.companyDescription())
                .location(r.location())
                .employmentType(parseEmploymentType(r.employmentType()))
                .experienceYearsMin(r.experienceYearsMin())
                .experienceYearsMax(r.experienceYearsMax())
                .responsibilities(JdMapper.serializeList(r.responsibilities()))
                .requiredSkills(JdMapper.serializeList(r.requiredSkills()))
                .preferredSkills(JdMapper.serializeList(r.preferredSkills()))
                .qualifications(JdMapper.serializeList(r.qualifications()))
                .mustHave(JdMapper.serializeList(r.mustHave()))
                .niceToHave(JdMapper.serializeList(r.niceToHave()))
                .benefits(JdMapper.serializeList(r.benefits()))
                .rawSummary(r.rawSummary())
                .extractionStatus(JdExtractionStatus.COMPLETED)
                .build();
    }

    /**
     * Convert the extracted employment-type string to the enum. The Python service
     * already normalises to an enum name or null, so an unrecognised value is defensive
     * only: it degrades to null rather than failing the whole extraction.
     */
    private EmploymentType parseEmploymentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EmploymentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Throws 404 if the JD does not exist or is not owned by the user. */
    private JobDescription requireOwnedJd(UUID jdId, UUID userId) {
        return jobDescriptionRepository.findByIdAndUserId(jdId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job description not found: " + jdId));
    }

    private String resolveFileName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "jd" : file.getOriginalFilename();
        String name = StringUtils.getFilename(StringUtils.cleanPath(original));
        return StringUtils.hasText(name) ? name : "jd";
    }
}
