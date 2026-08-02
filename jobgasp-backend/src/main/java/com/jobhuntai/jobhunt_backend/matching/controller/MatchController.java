package com.jobhuntai.jobhunt_backend.matching.controller;

import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchRequest;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchResponse;
import com.jobhuntai.jobhunt_backend.matching.dto.MatchSummaryResponse;
import com.jobhuntai.jobhunt_backend.matching.service.MatchService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Matching endpoints. Every route is authenticated: the owning {@code userId} is
 * resolved from the security principal, never taken from a request parameter, so a
 * user can only score and read their own resumes and JDs. Mirrors
 * {@code JdController} and {@code ResumeController}.
 *
 * <p>Calculation is explicitly client-triggered rather than implicit on upload — a
 * match is a deliberate question about a specific pair, and scoring every resume
 * against every JD would waste the LLM calls the semantic dimensions depend on.
 */
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final UserRepository userRepository;

    /** 201: a match result is created (or an existing one recalculated in place). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MatchResponse>> calculateMatch(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MatchRequest request) {
        MatchResponse response = matchService.calculateMatch(currentUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Match calculated.", response));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponse<MatchResponse>> getMatch(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID matchId) {
        MatchResponse response = matchService.getMatch(matchId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Match retrieved.", response));
    }

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<ApiResponse<List<MatchSummaryResponse>>> getMatchesForResume(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID resumeId) {
        List<MatchSummaryResponse> matches =
                matchService.getMatchesForResume(resumeId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Matches for resume retrieved.", matches));
    }

    @GetMapping("/jd/{jdId}")
    public ResponseEntity<ApiResponse<List<MatchSummaryResponse>>> getMatchesForJd(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jdId) {
        List<MatchSummaryResponse> matches =
                matchService.getMatchesForJd(jdId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Matches for job description retrieved.", matches));
    }

    /** Resolves the authenticated principal (email) to the owning user id. */
    private UUID currentUserId(UserDetails principal) {
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."))
                .getId();
    }
}
