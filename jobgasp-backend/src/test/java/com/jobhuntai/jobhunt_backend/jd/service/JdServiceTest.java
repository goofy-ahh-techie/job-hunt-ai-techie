package com.jobhuntai.jobhunt_backend.jd.service;

import com.jobhuntai.jobhunt_backend.common.exception.IntelligenceServiceUnavailableException;
import com.jobhuntai.jobhunt_backend.common.exception.JdExtractionFailedException;
import com.jobhuntai.jobhunt_backend.common.exception.JdIntelligenceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.jd.client.JdExtractionResult;
import com.jobhuntai.jobhunt_backend.jd.client.JdIntelligenceClient;
import com.jobhuntai.jobhunt_backend.jd.domain.JdExtractionStatus;
import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.domain.JdSourceType;
import com.jobhuntai.jobhunt_backend.jd.domain.JdStatus;
import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import com.jobhuntai.jobhunt_backend.jd.dto.JdDetailResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdPasteRequest;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.jd.repository.JobDescriptionRepository;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeTextExtractorService;
import com.jobhuntai.jobhunt_backend.resume.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdServiceTest {

    @Mock private JobDescriptionRepository jobDescriptionRepository;
    @Mock private JdIntelligenceRepository jdIntelligenceRepository;
    @Mock private JdIntelligenceClient intelligenceClient;
    @Mock private FileStorageService fileStorageService;
    @Mock private ResumeTextExtractorService textExtractorService;
    @Mock private JdPersistenceService persistenceService;

    @InjectMocks private JdService jdService;

    private final UUID userId = UUID.randomUUID();

    private static JdExtractionResult sampleResult() {
        return new JdExtractionResult(
                "Senior Java Engineer", "Acme", null, "Remote", "FULL_TIME", 5, 9,
                List.of("Build APIs"), List.of("Java", "Spring Boot"), List.of("Kafka"),
                List.of("BSc"), List.of("5+ years Java"), List.of("OSS"), List.of("Health"),
                "A backend role.");
    }

    @Test
    void ingestPaste_success_marksExtractedAndPersistsIntelligence() {
        JdPasteRequest request = new JdPasteRequest("My JD", "a".repeat(80));
        when(intelligenceClient.extract(request.rawText())).thenReturn(sampleResult());

        JdDetailResponse response = jdService.ingestPaste(userId, request);

        assertThat(response.jd().status()).isEqualTo(JdStatus.EXTRACTED);
        assertThat(response.jd().sourceType()).isEqualTo(JdSourceType.PASTE);
        assertThat(response.intelligence().extractionStatus()).isEqualTo(JdExtractionStatus.COMPLETED);
        assertThat(response.intelligence().requiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(response.intelligence().employmentType().name()).isEqualTo("FULL_TIME");

        ArgumentCaptor<JobDescription> jdCaptor = ArgumentCaptor.forClass(JobDescription.class);
        ArgumentCaptor<JdIntelligence> intelCaptor = ArgumentCaptor.forClass(JdIntelligence.class);
        verify(persistenceService).saveParsed(jdCaptor.capture(), intelCaptor.capture());
        assertThat(jdCaptor.getValue().getStatus()).isEqualTo(JdStatus.EXTRACTED);
        assertThat(intelCaptor.getValue().getExtractionStatus()).isEqualTo(JdExtractionStatus.COMPLETED);
    }

    @Test
    void ingestFile_success_storesExtractsAndMarksExtracted() {
        MockMultipartFile file =
                new MockMultipartFile("file", "jd.pdf", "application/pdf", "dummy".getBytes());
        when(fileStorageService.validate(file)).thenReturn(FileType.PDF);
        when(fileStorageService.store(eq(file), eq(userId), any(UUID.class))).thenReturn("key");
        when(fileStorageService.resolve("key")).thenReturn(Path.of("uploads", "key"));
        when(textExtractorService.extract(any(Path.class), eq(FileType.PDF)))
                .thenReturn("extracted job description text well over fifty characters long");
        when(intelligenceClient.extract(any(String.class))).thenReturn(sampleResult());

        JdDetailResponse response = jdService.ingestFile(userId, "My JD", file);

        assertThat(response.jd().status()).isEqualTo(JdStatus.EXTRACTED);
        assertThat(response.jd().sourceType()).isEqualTo(JdSourceType.FILE);
        verify(fileStorageService).store(eq(file), eq(userId), any(UUID.class));
        verify(textExtractorService).extract(any(Path.class), eq(FileType.PDF));
        verify(persistenceService).saveParsed(any(JobDescription.class), any(JdIntelligence.class));
    }

    @Test
    void ingestPaste_serviceUnavailable_marksFailedWithNoIntelligenceRow() {
        JdPasteRequest request = new JdPasteRequest("My JD", "a".repeat(80));
        when(intelligenceClient.extract(request.rawText()))
                .thenThrow(new IntelligenceServiceUnavailableException("down"));

        assertThatThrownBy(() -> jdService.ingestPaste(userId, request))
                .isInstanceOf(IntelligenceServiceUnavailableException.class);

        // saveJobDescription twice: once UPLOADED, once FAILED. No intelligence row.
        ArgumentCaptor<JobDescription> jdCaptor = ArgumentCaptor.forClass(JobDescription.class);
        verify(persistenceService, times(2)).saveJobDescription(jdCaptor.capture());
        assertThat(jdCaptor.getValue().getStatus()).isEqualTo(JdStatus.FAILED);
        verify(persistenceService, never()).saveParsed(any(), any());
    }

    @Test
    void ingestPaste_extractionFailed_marksFailedWithFailedIntelligenceRow() {
        JdPasteRequest request = new JdPasteRequest("My JD", "a".repeat(80));
        when(intelligenceClient.extract(request.rawText()))
                .thenThrow(new JdExtractionFailedException("JD extraction failed"));

        assertThatThrownBy(() -> jdService.ingestPaste(userId, request))
                .isInstanceOf(JdExtractionFailedException.class);

        ArgumentCaptor<JobDescription> jdCaptor = ArgumentCaptor.forClass(JobDescription.class);
        ArgumentCaptor<JdIntelligence> intelCaptor = ArgumentCaptor.forClass(JdIntelligence.class);
        verify(persistenceService).saveParsed(jdCaptor.capture(), intelCaptor.capture());
        assertThat(jdCaptor.getValue().getStatus()).isEqualTo(JdStatus.FAILED);
        assertThat(intelCaptor.getValue().getExtractionStatus()).isEqualTo(JdExtractionStatus.FAILED);
        assertThat(intelCaptor.getValue().getExtractionError()).isEqualTo("JD extraction failed");
    }

    @Test
    void getJd_wrongUser_throwsResourceNotFound() {
        UUID jdId = UUID.randomUUID();
        when(jobDescriptionRepository.findByIdAndUserId(jdId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jdService.getJd(jdId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getJdIntelligence_noIntelligenceRow_throwsJdIntelligenceNotFound() {
        UUID jdId = UUID.randomUUID();
        when(jobDescriptionRepository.findByIdAndUserId(jdId, userId))
                .thenReturn(Optional.of(JobDescription.builder().id(jdId).userId(userId).build()));
        when(jdIntelligenceRepository.findByJobDescriptionId(jdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jdService.getJdIntelligence(jdId, userId))
                .isInstanceOf(JdIntelligenceNotFoundException.class);
    }
}
