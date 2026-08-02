package com.jobhuntai.jobhunt_backend.jd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Paste-a-JD payload ({@code application/json}). {@code title} is the user's own
 * label for the JD; {@code rawText} is the pasted job description. The 50-char
 * minimum mirrors the intelligence service's own floor — too little text can't be
 * meaningfully extracted.
 */
public record JdPasteRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Job description text is required")
        @Size(min = 50, message = "Job description text must be at least 50 characters")
        String rawText
) {
}
