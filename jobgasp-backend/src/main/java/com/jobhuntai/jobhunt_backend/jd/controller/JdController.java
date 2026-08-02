package com.jobhuntai.jobhunt_backend.jd.controller;

import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdDetailResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdFileRequest;
import com.jobhuntai.jobhunt_backend.jd.dto.JdIntelligenceResponse;
import com.jobhuntai.jobhunt_backend.jd.dto.JdPasteRequest;
import com.jobhuntai.jobhunt_backend.jd.dto.JdResponse;
import com.jobhuntai.jobhunt_backend.jd.service.JdService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Job description endpoints. Every route is authenticated: the owning {@code userId}
 * is resolved from the security principal, never taken from a request parameter, so a
 * user can only ever act on their own JDs. Mirrors {@code ResumeController}.
 */
@RestController
@RequestMapping("/api/v1/jds")
@RequiredArgsConstructor
public class JdController {

    private final JdService jdService;
    private final UserRepository userRepository;

    @PostMapping(path = "/paste", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JdDetailResponse>> ingestPaste(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody JdPasteRequest request) {
        JdDetailResponse response = jdService.ingestPaste(currentUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Job description ingested and extracted.", response));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JdDetailResponse>> ingestFile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @ModelAttribute JdFileRequest request) {
        JdDetailResponse response =
                jdService.ingestFile(currentUserId(principal), request.title(), request.file());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Job description uploaded and extracted.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JdResponse>>> getJds(
            @AuthenticationPrincipal UserDetails principal) {
        List<JdResponse> jds = jdService.getJds(currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Job descriptions retrieved.", jds));
    }

    @GetMapping("/{jdId}")
    public ResponseEntity<ApiResponse<JdResponse>> getJd(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jdId) {
        JdResponse response = jdService.getJd(jdId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Job description retrieved.", response));
    }

    @GetMapping("/{jdId}/intelligence")
    public ResponseEntity<ApiResponse<JdIntelligenceResponse>> getJdIntelligence(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jdId) {
        JdIntelligenceResponse response = jdService.getJdIntelligence(jdId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Job description intelligence retrieved.", response));
    }

    @GetMapping("/{jdId}/detail")
    public ResponseEntity<ApiResponse<JdDetailResponse>> getJdDetail(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jdId) {
        JdDetailResponse response = jdService.getJdDetail(jdId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Job description detail retrieved.", response));
    }

    /** Resolves the authenticated principal (email) to the owning user id. */
    private UUID currentUserId(UserDetails principal) {
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."))
                .getId();
    }
}
