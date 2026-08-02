package com.jobhuntai.jobhunt_backend.jd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload-a-JD payload ({@code multipart/form-data}). Bound via {@code @ModelAttribute}
 * so the record's components map to form fields. Deep file validation (type, size)
 * is reused from {@code FileStorageService}; the annotations here are the first gate.
 */
public record JdFileRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotNull(message = "File is required")
        MultipartFile file
) {
}
