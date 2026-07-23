package com.jobhuntai.jobhunt_backend.resume.parser;

import com.jobhuntai.jobhunt_backend.common.exception.TextExtractionException;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Extracts plain text from a stored resume file. Strategy is selected off
 * {@link FileType}: PDFBox for PDF, Apache POI for DOCX. Any failure surfaces as a
 * {@link TextExtractionException}, which the global handler maps to 500.
 */
@Service
public class ResumeTextExtractorService {

    public String extract(Path filePath, FileType fileType) {
        if (fileType == null) {
            throw new TextExtractionException("File type is required for extraction.");
        }
        return switch (fileType) {
            case PDF -> extractPdf(filePath);
            case DOCX -> extractDocx(filePath);
        };
    }

    private String extractPdf(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            String text = stripper.getText(document);
            return text == null ? "" : text;
        } catch (IOException ex) {
            throw new TextExtractionException("Failed to extract text from PDF: " + filePath.getFileName(), ex);
        }
    }

    private String extractDocx(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(in)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            throw new TextExtractionException("Failed to extract text from DOCX: " + filePath.getFileName(), ex);
        }
    }
}
