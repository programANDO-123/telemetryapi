package com.telemetry.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.telemetry.api.model.Event;

public interface EventRepository extends MongoRepository<Event, String> {

    Optional<Event> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    void deleteByEventId(String eventId);

    List<Event> findByTypeAndTimestampBetween(String type, String from, String to);

    List<Event> findBySourceAndTimestampBetween(String source, String from, String to);
}
