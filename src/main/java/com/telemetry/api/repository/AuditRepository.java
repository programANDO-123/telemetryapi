package com.telemetry.api.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.telemetry.api.model.AuditEntry;

public interface AuditRepository extends MongoRepository<AuditEntry, String> {

    List<AuditEntry> findByTraceId(String traceId);

    List<AuditEntry> findByEntity(String entity);

    long countByResult(String result);
}
