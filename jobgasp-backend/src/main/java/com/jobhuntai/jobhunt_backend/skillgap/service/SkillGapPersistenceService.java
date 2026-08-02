package com.jobhuntai.jobhunt_backend.skillgap.service;

import com.jobhuntai.jobhunt_backend.skillgap.domain.SkillGap;
import com.jobhuntai.jobhunt_backend.skillgap.repository.SkillGapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transactional boundary for skill gap persistence. A separate bean rather
 * than private methods on {@link SkillGapService} so Spring's proxy actually applies
 * {@code @Transactional} — self-invocation would bypass it. Mirrors
 * {@code MatchPersistenceService} and its predecessors.
 */
@Service
@RequiredArgsConstructor
public class SkillGapPersistenceService {

    private final SkillGapRepository skillGapRepository;

    /**
     * Persists (insert or update) a gap analysis in its own transaction.
     *
     * <p>{@code REQUIRES_NEW} is the point of this method. The FAILED-recording write
     * happens while an exception is propagating; joining an existing transaction would
     * let that same exception roll the record back, and the failure would vanish
     * exactly when it most needs to be visible. Same rollback trap as Phases 2–4.
     *
     * <p>Returns the managed instance: for an update Spring Data goes through
     * {@code merge()}, which returns a copy carrying the audit values, so the caller
     * must keep the returned reference rather than the one it passed in.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SkillGap save(SkillGap gap) {
        return skillGapRepository.save(gap);
    }
}
