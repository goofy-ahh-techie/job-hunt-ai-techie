package com.jobhuntai.jobhunt_backend.resume.storage;

import com.jobhuntai.jobhunt_backend.common.exception.FileStorageException;
import com.jobhuntai.jobhunt_backend.resume.domain.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists uploaded resume files to a configurable local directory and hands back
 * an opaque storage key. The key — {@code {userId}/{resumeId}/{fileName}} — is what
 * gets stored in {@code resume.storage_path}; it stays valid even if the root
 * directory is relocated, since {@link #resolve(String)} rejoins it to the root.
 */
@Service
public class FileStorageService {

    /** Max accepted upload size: 10 MB. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final Path rootDir;

    public FileStorageService(@Value("${app.storage.resume-dir:./uploads/resumes}") String resumeDir) {
        this.rootDir = Paths.get(resumeDir).toAbsolutePath().normalize();
    }

    /**
     * Validates the upload and returns its {@link FileType}. Throws
     * {@link FileStorageException} if the file is missing, empty, too large, or
     * not a PDF/DOCX. Callers can use the returned type without re-sniffing.
     */
    public FileType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Uploaded file is missing or empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new FileStorageException("File exceeds the 10MB maximum.");
        }
        return resolveFileType(file.getOriginalFilename());
    }

    /**
     * Validates then writes the file to {@code {root}/{userId}/{resumeId}/{fileName}}.
     *
     * @return the storage key ({@code {userId}/{resumeId}/{fileName}}) to persist.
     */
    public String store(MultipartFile file, UUID userId, UUID resumeId) {
        validate(file);

        String fileName = StringUtils.cleanPath(
                StringUtils.getFilename(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (!StringUtils.hasText(fileName) || fileName.contains("..")) {
            throw new FileStorageException("Invalid file name.");
        }

        String storageKey = userId + "/" + resumeId + "/" + fileName;
        Path target = rootDir.resolve(storageKey).normalize();
        if (!target.startsWith(rootDir)) {
            // Defensive: a crafted name must never escape the storage root.
            throw new FileStorageException("Resolved storage path is outside the storage root.");
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file " + fileName, ex);
        }

        return storageKey;
    }

    /** Rejoins a stored key to the storage root, yielding the absolute file path. */
    public Path resolve(String storageKey) {
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new FileStorageException("Storage key resolves outside the storage root.");
        }
        return resolved;
    }

    private FileType resolveFileType(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return FileType.PDF;
        }
        if (name.endsWith(".docx")) {
            return FileType.DOCX;
        }
        throw new FileStorageException("Unsupported file type. Only PDF and DOCX are accepted.");
    }
}
