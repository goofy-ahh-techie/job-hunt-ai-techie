package com.jobhuntai.jobhunt_backend.resume.service;

import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.common.exception.ResumeProcessingException;
import com.jobhuntai.jobhunt_backend.common.exception.TextExtractionException;
import com.jobhuntai.jobhunt_backend.resume.domain.ExtractionStatus;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeStatus;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeUploadRequest;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeChunkData;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeChunkerService;
import com.jobhuntai.jobhunt_backend.resume.parser.ResumeTextExtractorService;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeChunkRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeRepository;
import com.jobhuntai.jobhunt_backend.resume.repository.ResumeVersionRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeVersionRepository resumeVersionRepository;
    @Mock private ResumeChunkRepository resumeChunkRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ResumeTextExtractorService textExtractorService;
    @Mock private ResumeChunkerService chunkerService;
    @Mock private ResumePersistenceService persistenceService;

    @InjectMocks private ResumeService resumeService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void uploadResume_success_persistsFirstVersionAndMarksParsed() {
        MockMultipartFile file =
                new MockMultipartFile("file", "resume.pdf", "application/pdf", "dummy".getBytes());
        ResumeUploadRequest request = new ResumeUploadRequest("My Resume", file);

        when(fileStorageService.validate(file)).thenReturn(FileType.PDF);
        when(fileStorageService.store(eq(file), eq(userId), any(UUID.class))).thenReturn("key");
        when(fileStorageService.resolve("key")).thenReturn(Path.of("uploads", "key"));
        when(textExtractorService.extract(any(Path.class), eq(FileType.PDF))).thenReturn("alpha beta gamma");
        when(resumeVersionRepository.findTopByResumeIdOrderByVersionNumberDesc(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(chunkerService.chunk("alpha beta gamma"))
                .thenReturn(List.of(new ResumeChunkData(SectionLabel.OTHER, "alpha beta gamma", 3)));

        ResumeResponse response = resumeService.uploadResume(userId, request);

        assertThat(response.status()).isEqualTo(ResumeStatus.PARSED);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.fileType()).isEqualTo(FileType.PDF);

        ArgumentCaptor<ResumeVersion> versionCaptor = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(persistenceService).saveParsed(any(Resume.class), versionCaptor.capture(), anyList());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getExtractionStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        verify(persistenceService, never()).saveResume(any(Resume.class));
    }

    @Test
    void uploadResume_extractionFailure_marksResumeFailedAndThrows422() {
        MockMultipartFile file =
                new MockMultipartFile("file", "resume.pdf", "application/pdf", "dummy".getBytes());
        ResumeUploadRequest request = new ResumeUploadRequest("My Resume", file);

        when(fileStorageService.validate(file)).thenReturn(FileType.PDF);
        when(fileStorageService.store(eq(file), eq(userId), any(UUID.class))).thenReturn("key");
        when(fileStorageService.resolve("key")).thenReturn(Path.of("uploads", "key"));
        when(textExtractorService.extract(any(Path.class), eq(FileType.PDF)))
                .thenThrow(new TextExtractionException("boom"));

        assertThatThrownBy(() -> resumeService.uploadResume(userId, request))
                .isInstanceOf(ResumeProcessingException.class);

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(persistenceService).saveResume(resumeCaptor.capture());
        assertThat(resumeCaptor.getValue().getStatus()).isEqualTo(ResumeStatus.FAILED);
        verify(persistenceService, never()).saveParsed(any(), any(), anyList());
    }

    @Test
    void getResume_wrongUser_throwsResourceNotFound() {
        UUID resumeId = UUID.randomUUID();
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.getResume(resumeId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
