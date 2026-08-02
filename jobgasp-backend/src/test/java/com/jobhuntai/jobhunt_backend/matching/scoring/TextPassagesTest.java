package com.jobhuntai.jobhunt_backend.matching.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextPassagesTest {

    @Test
    void multiLineChunkIsSplitIntoOnePassagePerLine() {
        List<String> passages = TextPassages.split(List.of(
                "EXPERIENCE\n"
                        + "Led a team of five engineers building payment APIs at scale.\n"
                        + "Deployed and operated container orchestration clusters on AWS."));

        assertThat(passages).containsExactly(
                "Led a team of five engineers building payment APIs at scale.",
                "Deployed and operated container orchestration clusters on AWS.");
    }

    @Test
    void shortFragmentsAndHeadersAreDropped() {
        // "EXPERIENCE" and "SKILLS" are section headers, not evidence — embedding
        // them wastes a call and adds a target nothing should ever match.
        List<String> passages = TextPassages.split(List.of(
                "EXPERIENCE\n"
                        + "Deployed and operated container orchestration clusters on AWS.\n"
                        + "SKILLS\n"
                        + "Java"));

        assertThat(passages)
                .containsExactly("Deployed and operated container orchestration clusters on AWS.");
    }

    @Test
    void longRunTogetherLineIsSplitIntoSentences() {
        String longLine = "Led a team of five engineers building payment APIs handling twelve "
                + "thousand requests per second across the platform. Owned the reliability and "
                + "the on-call rotation for the core payments service every quarter. Designed a "
                + "fleet of independently deployable Java services communicating over an event "
                + "bus backed by Apache Kafka in production.";

        List<String> passages = TextPassages.split(List.of(longLine));

        assertThat(passages).hasSize(3);
        assertThat(passages.getFirst()).startsWith("Led a team of five engineers");
        assertThat(passages.getLast()).startsWith("Designed a fleet");
    }

    @Test
    void duplicateLinesAreCollapsed() {
        String line = "Deployed and operated container orchestration clusters on AWS.";

        assertThat(TextPassages.split(List.of(line, line))).containsExactly(line);
    }

    @Test
    void resumeOfOnlyShortLinesFallsBackToTheOriginalTexts() {
        // Splitting must never leave the semantic pass with nothing to compare
        // against: a resume of unusually terse lines should still be searchable,
        // even though every line individually looks like a header.
        List<String> terse = List.of("Java", "Kafka");

        assertThat(TextPassages.split(terse)).isEqualTo(terse);
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertThat(TextPassages.split(List.of())).isEmpty();
        assertThat(TextPassages.split(null)).isEmpty();
    }
}
