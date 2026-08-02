package com.jobhuntai.jobhunt_backend.jd.service;

import com.jobhuntai.jobhunt_backend.jd.domain.JdIntelligence;
import com.jobhuntai.jobhunt_backend.jd.domain.JobDescription;
import com.jobhuntai.jobhunt_backend.jd.repository.JdIntelligenceRepository;
import com.jobhuntai.jobhunt_backend.jd.repository.JobDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transactional boundaries for JD persistence. Kept a separate bean (not
 * private methods on {@link JdService}) so Spring's proxy actually applies
 * {@code @Transactional} — self-invocation would bypass it. Mirrors
 * {@code ResumePersistenceService}.
 */
@Service
@RequiredArgsConstructor
public class JdPersistenceService {

    private final JobDescriptionRepository jobDescriptionRepository;
    private final JdIntelligenceRepository jdIntelligenceRepository;

    /** Persists (insert or update) a single job description in its own transaction. */
    @Transactional
    public JobDescription saveJobDescription(JobDescription jd) {
        return jobDescriptionRepository.save(jd);
    }

    /** Persists (insert or update) a single intelligence row in its own transaction. */
    @Transactional
    public JdIntelligence saveJdIntelligence(JdIntelligence intel) {
        return jdIntelligenceRepository.save(intel);
    }

    /**
     * Atomically persists a job description and its intelligence row together. Used for
     * both the success path (JD → EXTRACTED + COMPLETED intelligence) and the
     * extraction-failed path (JD → FAILED + FAILED intelligence): either both land or
     * neither does.
     */
    @Transactional
    public void saveParsed(JobDescription jd, JdIntelligence intel) {
        jobDescriptionRepository.save(jd);
        jdIntelligenceRepository.save(intel);
    }
}
