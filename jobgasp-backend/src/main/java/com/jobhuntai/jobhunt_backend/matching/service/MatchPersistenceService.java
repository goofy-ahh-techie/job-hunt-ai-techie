package com.jobhuntai.jobhunt_backend.matching.service;

import com.jobhuntai.jobhunt_backend.matching.domain.MatchResult;
import com.jobhuntai.jobhunt_backend.matching.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transactional boundary for match persistence. A separate bean rather than
 * private methods on {@link MatchService} so Spring's proxy actually applies
 * {@code @Transactional} — self-invocation would bypass it. Mirrors
 * {@code JdPersistenceService} and {@code ResumePersistenceService}.
 */
@Service
@RequiredArgsConstructor
public class MatchPersistenceService {

    private final MatchResultRepository matchResultRepository;

    /**
     * Persists (insert or update) a match result in its own transaction.
     *
     * <p>{@code REQUIRES_NEW} is the point of this method. The FAILED-recording write
     * happens while an exception is propagating; joining an existing transaction would
     * let that same exception roll the record back, and the failure would vanish
     * exactly when it most needs to be visible. Same rollback trap as Phases 2 and 3.
     *
     * <p>Returns the managed instance: for an update Spring Data goes through
     * {@code merge()}, which returns a copy, and audit fields land on the copy — the
     * caller must keep the returned reference, not the one it passed in.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchResult save(MatchResult result) {
        return matchResultRepository.save(result);
    }
}
