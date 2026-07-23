package com.jobhuntai.jobhunt_backend.resume.parser;

import com.jobhuntai.jobhunt_backend.resume.domain.SectionLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeChunkerServiceTest {

    private final ResumeChunkerService chunker = new ResumeChunkerService();

    @Test
    void returnsSingleOtherChunk_whenNoHeadersPresent() {
        String text = "This is a plain paragraph of prose with no recognizable section headers at all.";

        List<ResumeChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).sectionLabel()).isEqualTo(SectionLabel.OTHER);
        assertThat(chunks.get(0).content()).isEqualTo(text);
    }

    @Test
    void detectsEachSection_inOrder() {
        String text = """
                Summary
                Backend engineer with 5 years of experience.
                Experience
                Built distributed systems at Acme.
                Skills
                Java, Spring, PostgreSQL
                Education
                BSc Computer Science
                """;

        List<ResumeChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).extracting(ResumeChunkData::sectionLabel)
                .containsExactly(
                        SectionLabel.SUMMARY,
                        SectionLabel.EXPERIENCE,
                        SectionLabel.SKILLS,
                        SectionLabel.EDUCATION);
    }

    @Test
    void detectsSectionsViaAlternateKeywords() {
        String text = """
                Profile
                Motivated developer.
                Work History
                Senior dev at Foo.
                Technical Skills
                Go, Kubernetes
                Academic
                MSc Software Engineering
                """;

        List<ResumeChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).extracting(ResumeChunkData::sectionLabel)
                .containsExactly(
                        SectionLabel.SUMMARY,
                        SectionLabel.EXPERIENCE,
                        SectionLabel.SKILLS,
                        SectionLabel.EDUCATION);
    }

    @Test
    void doesNotTreatProseMentioningKeywordAsHeader() {
        // A long sentence that merely contains "experience" must not open a section.
        String text = "I have extensive experience building scalable services across many teams.";

        List<ResumeChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).sectionLabel()).isEqualTo(SectionLabel.OTHER);
    }

    @Test
    void countsWordsAccurately() {
        List<ResumeChunkData> chunks = chunker.chunk("alpha beta gamma delta");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).wordCount()).isEqualTo(4);
    }

    @Test
    void returnsEmptyList_forNullBlankOrEmptyInput() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n  \t ")).isEmpty();
    }
}
