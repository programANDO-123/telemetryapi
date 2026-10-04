package com.telemetry.api.model;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Document(collection = "document_events")
public class Event {

    @Id
    @NotBlank
    private String eventId;

    @NotBlank
    private String type;

    @NotBlank
    private String source;

    @NotBlank
    private String timestamp;

    @NotNull
    private Map<String, Object> payload;

    private Map<String, Object> metadata;

    public Event() {
    }

    public Event(String eventId, String type, String source, String timestamp,
                 Map<String, Object> payload, Map<String, Object> metadata) {
        this.eventId = eventId;
        this.type = type;
        this.source = source;
        this.timestamp = timestamp;
        this.payload = payload;
        this.metadata = metadata;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
