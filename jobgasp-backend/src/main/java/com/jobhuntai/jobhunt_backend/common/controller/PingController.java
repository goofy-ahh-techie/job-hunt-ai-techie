package com.jobhuntai.jobhunt_backend.common.controller;

import com.jobhuntai.jobhunt_backend.common.client.PythonIntelligenceClient;
import com.jobhuntai.jobhunt_backend.common.model.AppPingEntity;
import com.jobhuntai.jobhunt_backend.common.repository.AppPingRepository;
import com.jobhuntai.jobhunt_backend.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ping")
@RequiredArgsConstructor
public class PingController {

    private final PythonIntelligenceClient intelligenceClient;
    private final AppPingRepository appPingRepository;

    @GetMapping("/python")
    public ResponseEntity<ApiResponse<?>> pythonAPIConnection() {
        Map<String, Object> objectMap = intelligenceClient.ping();
        ApiResponse<?> data = objectMap != null ? ApiResponse.success("Python reachable", objectMap): ApiResponse.failure("Python is not reachable", null);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/db")
    public ResponseEntity<ApiResponse<?>> dataBaseConnection() {
        List<AppPingEntity> appPingEntities = appPingRepository.findAll();
        ApiResponse<?> data = !appPingEntities.isEmpty() ?
                ApiResponse.success("Database is connected", appPingEntities): ApiResponse.failure("Database is not connected", null);
        return ResponseEntity.ok(data);
    }
}
