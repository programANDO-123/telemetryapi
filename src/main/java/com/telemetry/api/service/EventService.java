package com.telemetry.api.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.telemetry.api.model.Event;
import com.telemetry.api.repository.EventRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@Service
public class EventService {

    private final EventRepository repository;
    private final Validator validator;
    private final AuditService auditService;

    public EventService(EventRepository repository, Validator validator, AuditService auditService) {
        this.repository = repository;
        this.validator = validator;
        this.auditService = auditService;
    }

    public Event save(Event event) {
        String actorId = envOr("ACTOR_ID", "system");
        String traceId = envOr("TRACE_ID", "m06-verify");

        Set<ConstraintViolation<Event>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            auditService.record(actorId, "save_event", "document_events",
                    event != null ? event.getEventId() : null, traceId,
                    "failed", "validation_error");
            throw new ConstraintViolationException(violations);
        }

        Optional<Event> existing = repository.findByEventId(event.getEventId());
        if (existing.isPresent()) {
            event.setId(existing.get().getId());
        }

        Event saved = repository.save(event);
        auditService.record(actorId, "save_event", "document_events",
                event.getEventId(), traceId, "ok", null);
        return saved;
    }

    public Optional<Event> findByEventId(String eventId) {
        return repository.findByEventId(eventId);
    }

    public List<Event> findByTypeInRange(String type, String from, String to) {
        return repository.findByTypeAndTimestampBetween(type, from, to);
    }

    public List<Event> findBySourceInRange(String source, String from, String to) {
        return repository.findBySourceAndTimestampBetween(source, from, to);
    }

    public boolean deleteByEventId(String eventId) {
        String actorId = envOr("ACTOR_ID", "system");
        String traceId = envOr("TRACE_ID", "m06-verify");

        if (repository.existsByEventId(eventId)) {
            repository.deleteByEventId(eventId);
            auditService.record(actorId, "delete_event", "document_events",
                    eventId, traceId, "ok", null);
            return true;
        }
        auditService.record(actorId, "delete_event", "document_events",
                eventId, traceId, "failed", "not_found");
        return false;
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public long count() {
        return repository.count();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
