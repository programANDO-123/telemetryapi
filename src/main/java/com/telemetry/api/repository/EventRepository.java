package com.telemetry.api.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.telemetry.api.model.Event;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByTypeAndTimestampBetween(String type, String from, String to);

    List<Event> findBySourceAndTimestampBetween(String source, String from, String to);
}
