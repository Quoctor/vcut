package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class UserActivity {
    private String userId;
    private String activityType;
    private LocalDateTime timestamp;
    private Map<String, String> metadata;
}
