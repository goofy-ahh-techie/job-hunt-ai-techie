package com.jobhuntai.jobhunt_backend.matching.scoring;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.chunk;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.context;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.jd;
import static com.jobhuntai.jobhunt_backend.matching.scoring.ScoringFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class QualificationsScorerTest {

    private final QualificationsScorer scorer = new QualificationsScorer();

    @Test
    void degreeAbbreviationOnResumeSatisfiesSpelledOutRequirement() {
        ScoringContext context = context(
                jd().qualifications(json(List.of("Bachelor's degree in Computer Science"))).build(),
                chunk(SectionLabel.EDUCATION, "B.Tech, Computer Science, IIT Delhi, 2015"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.matched()).containsExactly("Bachelor's degree in Computer Science");
        assertThat(result.explanation()).isEqualTo("All qualifications matched");
    }

    @Test
    void mastersAbbreviationsAreRecognisedToo() {
        ScoringContext context = context(
                jd().qualifications(json(List.of("Master's degree preferred"))).build(),
                chunk(SectionLabel.EDUCATION, "M.Tech, Distributed Systems"));

        assertThat(scorer.score(context).rawScore()).isEqualTo(100.0);
    }

    @Test
    void exactMatchStillWorksWithoutSynonyms() {
        ScoringContext context = context(
                jd().qualifications(json(List.of("AWS Certified Solutions Architect"))).build(),
                chunk(SectionLabel.OTHER, "AWS Certified Solutions Architect - Associate, 2022"));

        assertThat(scorer.score(context).rawScore()).isEqualTo(100.0);
    }

    @Test
    void unmatchedQualificationIsReportedAsMissing() {
        ScoringContext context = context(
                jd().qualifications(json(List.of("PhD in Machine Learning", "AWS certification")))
                        .build(),
                chunk(SectionLabel.EDUCATION, "B.Tech, Computer Science"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(0.0);
        assertThat(result.missing())
                .containsExactly("PhD in Machine Learning", "AWS certification");
        assertThat(result.explanation()).isEqualTo("0 of 2 qualifications matched");
    }

    @Test
    void emptyQualifications_scoresHundred() {
        ScoringContext context = context(
                jd().qualifications(json(List.of())).build(),
                chunk(SectionLabel.EDUCATION, "B.Tech"));

        SubScoreResult result = scorer.score(context);

        assertThat(result.rawScore()).isEqualTo(100.0);
        assertThat(result.explanation()).isEqualTo("No qualifications specified");
    }
}
