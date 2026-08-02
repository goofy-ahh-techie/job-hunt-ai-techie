package com.jobhuntai.jobhunt_backend.skillgap.controller;

import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.SkillGapSummaryResponse;
import com.jobhuntai.jobhunt_backend.skillgap.dto.StandaloneGapResponse;
import com.jobhuntai.jobhunt_backend.skillgap.service.SkillGapService;
import com.jobhuntai.jobhunt_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Skill gap endpoints. Every route is authenticated: the owning {@code userId} is
 * resolved from the security principal, never from a request parameter, so a user can
 * only analyse and read their own matches and resumes. Mirrors {@code MatchController}
 * and its predecessors.
 *
 * <p>Note the two shapes of {@code /match/{matchId}}: {@code POST} runs the analysis
 * (an expensive LLM call, hence 201 and an explicit trigger), while {@code GET} reads
 * a previous result and 404s if none has been run. Keeping them on one path makes the
 * relationship obvious; keeping them on different verbs stops a page refresh from
 * silently re-billing an LLM call.
 */
@RestController
@RequestMapping("/api/v1/skill-gaps")
@RequiredArgsConstructor
public class SkillGapController {

    private final SkillGapService skillGapService;
    private final UserRepository userRepository;

    /** 201: a gap analysis is created (or an existing one re-analysed in place). */
    @PostMapping("/match/{matchId}")
    public ResponseEntity<ApiResponse<SkillGapResponse>> analyzeMatchGap(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID matchId) {
        SkillGapResponse response =
                skillGapService.analyzeMatchGap(currentUserId(principal), matchId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Skill gap analysis complete.", response));
    }

    @GetMapping("/{skillGapId}")
    public ResponseEntity<ApiResponse<SkillGapResponse>> getSkillGap(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID skillGapId) {
        SkillGapResponse response =
                skillGapService.getSkillGap(skillGapId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Skill gap analysis retrieved.", response));
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<ApiResponse<SkillGapResponse>> getSkillGapByMatch(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID matchId) {
        SkillGapResponse response =
                skillGapService.getSkillGapByMatch(matchId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Skill gap analysis retrieved.", response));
    }

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<ApiResponse<List<SkillGapSummaryResponse>>> getSkillGapsForResume(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId) {
        List<SkillGapSummaryResponse> gaps =
                skillGapService.getSkillGapsForResume(resumeId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Skill gaps for resume retrieved.", gaps));
    }

    @GetMapping("/jd/{jdId}")
    public ResponseEntity<ApiResponse<List<SkillGapSummaryResponse>>> getSkillGapsForJd(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jdId) {
        List<SkillGapSummaryResponse> gaps =
                skillGapService.getSkillGapsForJd(jdId, currentUserId(principal));
        return ResponseEntity.ok(
                ApiResponse.success("Skill gaps for job description retrieved.", gaps));
    }

    /**
     * Resume-only assessment. A GET despite running an LLM call, because it creates no
     * server-side state — nothing is persisted, so the request is safely repeatable.
     */
    @GetMapping("/resume/{resumeId}/standalone")
    public ResponseEntity<ApiResponse<StandaloneGapResponse>> analyzeResumeStandalone(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId) {
        StandaloneGapResponse response =
                skillGapService.analyzeResumeStandalone(currentUserId(principal), resumeId);
        return ResponseEntity.ok(
                ApiResponse.success("Standalone resume gap analysis complete.", response));
    }

    /** Resolves the authenticated principal (email) to the owning user id. */
    private UUID currentUserId(UserDetails principal) {
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."))
                .getId();
    }
}
