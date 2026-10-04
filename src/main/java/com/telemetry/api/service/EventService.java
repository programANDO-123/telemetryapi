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

    public EventService(EventRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public Event save(Event event) {
        Set<ConstraintViolation<Event>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return repository.save(event);
    }

    public Optional<Event> findById(String eventId) {
        return repository.findById(eventId);
    }

    public List<Event> findByTypeInRange(String type, String from, String to) {
        return repository.findByTypeAndTimestampBetween(type, from, to);
    }

    public List<Event> findBySourceInRange(String source, String from, String to) {
        return repository.findBySourceAndTimestampBetween(source, from, to);
    }

    public boolean deleteById(String eventId) {
        if (repository.existsById(eventId)) {
            repository.deleteById(eventId);
            return true;
        }
        return false;
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public long count() {
        return repository.count();
    }
}
