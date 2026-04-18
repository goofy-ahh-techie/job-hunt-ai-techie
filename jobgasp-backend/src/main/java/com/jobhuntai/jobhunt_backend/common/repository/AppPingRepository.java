package com.jobhuntai.jobhunt_backend.common.repository;

import com.jobhuntai.jobhunt_backend.common.model.AppPingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppPingRepository extends JpaRepository<AppPingEntity, Long> {
}
