package com.jobhuntai.jobhunt_backend.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart upload payload. Bound via {@code @ModelAttribute} so the record's
 * components map to form fields. Deep file validation (type, size, emptiness)
 * lives in {@code FileStorageService}; the annotations here are the first gate.
 */
public record ResumeUploadRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotNull(message = "File is required")
        MultipartFile file
) {
}
