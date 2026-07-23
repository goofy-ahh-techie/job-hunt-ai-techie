package com.jobhuntai.jobhunt_backend.resume.controller;

import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeChunkResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeResponse;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeUploadRequest;
import com.jobhuntai.jobhunt_backend.resume.dto.ResumeVersionResponse;
import com.jobhuntai.jobhunt_backend.resume.service.ResumeService;
import com.jobhuntai.jobhunt_backend.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Resume endpoints. Every route is authenticated: the owning {@code userId} is
 * resolved from the security principal, never taken from a request parameter, so a
 * user can only ever act on their own resumes.
 */
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @ModelAttribute ResumeUploadRequest request) {
        ResumeResponse response = resumeService.uploadResume(currentUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resume uploaded and parsed.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> getResumes(
            @AuthenticationPrincipal UserDetails principal) {
        List<ResumeResponse> resumes = resumeService.getResumes(currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Resumes retrieved.", resumes));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<ApiResponse<ResumeResponse>> getResume(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId) {
        ResumeResponse response = resumeService.getResume(resumeId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Resume retrieved.", response));
    }

    @GetMapping("/{resumeId}/versions")
    public ResponseEntity<ApiResponse<List<ResumeVersionResponse>>> getResumeVersions(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId) {
        List<ResumeVersionResponse> versions = resumeService.getResumeVersions(resumeId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Resume versions retrieved.", versions));
    }

    @GetMapping("/{resumeId}/versions/{versionId}/chunks")
    public ResponseEntity<ApiResponse<List<ResumeChunkResponse>>> getResumeChunks(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId,
            @PathVariable UUID versionId) {
        List<ResumeChunkResponse> chunks =
                resumeService.getResumeChunks(resumeId, versionId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Resume chunks retrieved.", chunks));
    }

    /** Resolves the authenticated principal (email) to the owning user id. */
    private UUID currentUserId(UserDetails principal) {
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."))
                .getId();
    }
}
