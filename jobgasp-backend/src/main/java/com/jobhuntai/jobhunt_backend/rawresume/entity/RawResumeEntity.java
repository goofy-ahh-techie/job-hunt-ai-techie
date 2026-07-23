package com.jobhuntai.jobhunt_backend.rawresume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "raw_resume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawResumeEntity {
    @Id
    private UUID id;
    private UUID userId;
    private String fileName;
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    private String status;
    private Timestamp uploadedAt;
    private Timestamp updatedAt;
}
