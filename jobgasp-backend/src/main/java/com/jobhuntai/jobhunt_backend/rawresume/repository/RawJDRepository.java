package com.jobhuntai.jobhunt_backend.rawresume.repository;

import com.jobhuntai.jobhunt_backend.rawresume.entity.RawJDEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RawJDRepository extends JpaRepository<RawJDEntity, UUID> {
}
