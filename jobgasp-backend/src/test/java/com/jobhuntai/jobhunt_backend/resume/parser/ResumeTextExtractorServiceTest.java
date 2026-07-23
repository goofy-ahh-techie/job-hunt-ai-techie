package com.jobhuntai.jobhunt_backend.resume.parser;

import com.jobhuntai.jobhunt_backend.common.exception.TextExtractionException;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeTextExtractorServiceTest {

    private final ResumeTextExtractorService extractor = new ResumeTextExtractorService();

    @Test
    void extractsTextFromPdf(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("resume.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(100, 700);
                content.showText("Hello Resume PDF");
                content.endText();
            }
            document.save(pdf.toFile());
        }

        String text = extractor.extract(pdf, FileType.PDF);

        assertThat(text).isNotBlank().contains("Hello Resume PDF");
    }

    @Test
    void extractsTextFromDocx(@TempDir Path tempDir) throws Exception {
        Path docx = tempDir.resolve("resume.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Hello Resume DOCX");
            try (OutputStream out = Files.newOutputStream(docx)) {
                document.write(out);
            }
        }

        String text = extractor.extract(docx, FileType.DOCX);

        assertThat(text).isNotBlank().contains("Hello Resume DOCX");
    }

    @Test
    void throwsWhenFileTypeIsNull(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("resume.pdf");
        Files.writeString(file, "irrelevant");

        assertThatThrownBy(() -> extractor.extract(file, null))
                .isInstanceOf(TextExtractionException.class);
    }

    @Test
    void throwsWhenContentIsNotAValidPdf(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("broken.pdf");
        Files.writeString(file, "this is definitely not a PDF");

        assertThatThrownBy(() -> extractor.extract(file, FileType.PDF))
                .isInstanceOf(TextExtractionException.class);
    }
}
