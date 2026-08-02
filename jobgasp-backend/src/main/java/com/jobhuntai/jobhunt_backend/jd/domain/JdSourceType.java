package com.jobhuntai.jobhunt_backend.jd.domain;

/**
 * How a job description entered the system: pasted raw text, or an uploaded file.
 * The file-only columns on {@link JobDescription} (file_name, file_size_bytes,
 * storage_path) are null when this is {@link #PASTE}.
 */
public enum JdSourceType {
    PASTE,
    FILE
}
