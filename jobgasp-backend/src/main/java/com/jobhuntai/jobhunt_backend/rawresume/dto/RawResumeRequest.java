package com.jobhuntai.jobhunt_backend.rawresume.dto;

import com.jobhuntai.jobhunt_backend.rawresume.constants.ResumeSourceType;

public record RawResumeRequest(
        String fileName,
        ResumeSourceType sourceType,
        String rawText
) {
}
