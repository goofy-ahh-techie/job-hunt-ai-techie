package com.jobhuntai.jobhunt_backend.resume.mapper;

import com.jobhuntai.jobhunt_backend.resume.domain.Resume;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeChunk;
import com.jobhuntai.jobhunt_backend.resume.domain.ResumeVersion;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeChunkResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeVersionResponse;

import java.util.List;

/**
 * Single home for entity → DTO conversion. Keeping mapping here (not in the
 * service or controller) is a deliberate convention: controllers stay thin and
 * services stay focused on orchestration.
 */
public final class ResumeMapper {

    private ResumeMapper() {
    }

    public static ResumeResponse toResponse(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getUserId(),
                resume.getTitle(),
                resume.getFileName(),
                resume.getFileType(),
                resume.getFileSizeBytes(),
                resume.getStatus(),
                resume.getCreatedAt()
        );
    }

    public static ResumeVersionResponse toVersionResponse(ResumeVersion version) {
        return new ResumeVersionResponse(
                version.getId(),
                version.getResumeId(),
                version.getVersionNumber(),
                version.getLabel(),
                version.getWordCount(),
                version.getCharCount(),
                version.getExtractionStatus(),
                version.getCreatedAt()
        );
    }

    public static ResumeChunkResponse toChunkResponse(ResumeChunk chunk) {
        return new ResumeChunkResponse(
                chunk.getId(),
                chunk.getResumeVersionId(),
                chunk.getChunkIndex(),
                chunk.getSectionLabel(),
                chunk.getContent(),
                chunk.getWordCount()
        );
    }

    public static List<ResumeResponse> toResponseList(List<Resume> resumes) {
        return resumes.stream().map(ResumeMapper::toResponse).toList();
    }

    public static List<ResumeVersionResponse> toVersionResponseList(List<ResumeVersion> versions) {
        return versions.stream().map(ResumeMapper::toVersionResponse).toList();
    }

    public static List<ResumeChunkResponse> toChunkResponseList(List<ResumeChunk> chunks) {
        return chunks.stream().map(ResumeMapper::toChunkResponse).toList();
    }
}
