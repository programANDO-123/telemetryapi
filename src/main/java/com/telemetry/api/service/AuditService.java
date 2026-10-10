package com.telemetry.api.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.telemetry.api.model.AuditEntry;
import com.telemetry.api.repository.AuditRepository;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public AuditEntry record(String actorId, String action, String entity, String entityId,
            String traceId, String result, String reason) {
        AuditEntry entry = new AuditEntry(
                actorId,
                action,
                entity,
                entityId,
                Instant.now().toString(),
                traceId,
                result,
                reason);
        return repository.save(entry);
    }

    public List<AuditEntry> findByTraceId(String traceId) {
        return repository.findByTraceId(traceId);
    }

    public List<AuditEntry> findByEntity(String entity) {
        return repository.findByEntity(entity);
    }

    public long count() {
        return repository.count();
    }

    public void deleteAll() {
        repository.deleteAll();
    }
}
