package com.jobhuntai.jobhunt_backend.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_ping")
@Getter
@Setter
public class AppPingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
