package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorEvent {
    private String errorId;
    private String service;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String message;
    private String stackTrace;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
}
