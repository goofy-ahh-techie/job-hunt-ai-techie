package com.jobhuntai.jobhunt_backend.resume.domain;

/**
 * Supported upload formats. Extraction strategy is selected off this value:
 * PDF via PDFBox, DOCX via Apache POI.
 * Persisted as a string — never ordinal.
 */
public enum FileType {
    PDF,
    DOCX
}
