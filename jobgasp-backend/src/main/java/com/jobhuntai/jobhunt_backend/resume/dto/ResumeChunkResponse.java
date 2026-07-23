package com.jobhuntai.jobhunt_backend.resume.dto;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;

import java.util.UUID;

public record ResumeChunkResponse(
        UUID id,
        UUID resumeVersionId,
        Integer chunkIndex,
        SectionLabel sectionLabel,
        String content,
        Integer wordCount
) {
}
