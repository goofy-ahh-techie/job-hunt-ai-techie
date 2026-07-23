package com.jobhuntai.jobhunt_backend.rawresume.controller;

import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeRequest;
import com.jobhuntai.jobhunt_backend.rawresume.dto.RawResumeResponse;
import com.jobhuntai.jobhunt_backend.rawresume.entity.RawResumeEntity;
import com.jobhuntai.jobhunt_backend.rawresume.servive.RawResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class RawResumeController {

    private final RawResumeService resumeService;

    @PostMapping("/upload-resume")
    public ResponseEntity<ApiResponse<RawResumeResponse>> uploadRawResume(@RequestBody RawResumeRequest rawResumeRequest) {
        RawResumeResponse response = resumeService.uploadRawResumeData(rawResumeRequest);
        ApiResponse<RawResumeResponse> result = response != null ? ApiResponse.success("Raw Resume details added in DB.", response)
                : ApiResponse.failure("Failed to add resume details in DB.", null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/fetch/{id}")
    public ResponseEntity<ApiResponse<?>> getRawResumeDataByID(@PathVariable UUID id) {
        RawResumeEntity data = resumeService.getRawResumeDataByID(id);
        ApiResponse<?> result = data != null ? ApiResponse.success("Got the data", data)
                : ApiResponse.failure("No data present", null);
        return ResponseEntity.ok(result);
    }

}
