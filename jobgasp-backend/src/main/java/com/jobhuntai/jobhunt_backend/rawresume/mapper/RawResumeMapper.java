package com.jobhuntai.jobhunt_backend.rawresume.mapper;

import com.jobhuntai.jobhunt_backend.rawresume.constants.ResumeStatus;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeRequest;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeResponse;
import com.jobhuntai.jobhunt_backend.rawresume.entity.RawResumeEntity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class RawResumeMapper {

    private RawResumeMapper() {
    }

    public static RawResumeEntity toEntity(RawResumeRequest resumeRequest, UUID userID) {
        RawResumeEntity rawResumeEntity = new RawResumeEntity();

        rawResumeEntity.setId(UUID.randomUUID());
        rawResumeEntity.setUserId(userID);
        rawResumeEntity.setFileName(resumeRequest.fileName());
        rawResumeEntity.setSourceType(resumeRequest.sourceType().name());
        rawResumeEntity.setRawText(resumeRequest.rawText());
        rawResumeEntity.setStatus(ResumeStatus.RECEIVED.name());

        Timestamp currentTime = Timestamp.from(Instant.now());
        rawResumeEntity.setUploadedAt(currentTime);
        rawResumeEntity.setUpdatedAt(currentTime);

        return rawResumeEntity;
    }

    public static RawResumeResponse toResponse(RawResumeEntity entity) {
        return new RawResumeResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getFileName(),
                entity.getSourceType(),
                entity.getStatus(),
                entity.getUploadedAt(),
                entity.getUpdatedAt()
        );
    }

}
