package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class NotificationEvent {
    private String userId;
    private String type;
    private String title;
    private String message;
    private String channel; // EMAIL, PUSH, SMS
    private Map<String, String> metadata;
    private LocalDateTime timestamp;
}
