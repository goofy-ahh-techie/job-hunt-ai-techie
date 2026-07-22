package com.jobhuntai.jobhunt_backend.rawresume.repository;

import com.jobhuntai.jobhunt_backend.rawresume.entity.RawResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RawResumeRepository extends JpaRepository<RawResumeEntity, UUID> {
}
