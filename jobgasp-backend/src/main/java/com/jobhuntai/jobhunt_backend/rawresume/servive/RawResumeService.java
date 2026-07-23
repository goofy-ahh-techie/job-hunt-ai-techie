package com.jobhuntai.jobhunt_backend.rawresume.servive;

import com.jobhuntai.jobhunt_backend.common.exception.ResourceNotFoundException;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeRequest;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeResponse;
import com.jobhuntai.jobhunt_backend.rawresume.entity.RawResumeEntity;
import com.jobhuntai.jobhunt_backend.rawresume.mapper.RawResumeMapper;
import com.jobhuntai.jobhunt_backend.rawresume.repository.RawResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RawResumeService {

    private final RawResumeRepository resumeRepository;

    public RawResumeResponse uploadRawResumeData(RawResumeRequest rawResumeRequest) {
        RawResumeEntity rawResume = RawResumeMapper.toEntity(rawResumeRequest, UUID.randomUUID());
        RawResumeEntity result = resumeRepository.save(rawResume);
        return RawResumeMapper.toResponse(result);
    }

    public RawResumeEntity getRawResumeDataByID(UUID id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Raw resume not found: " + id));
    }
}
