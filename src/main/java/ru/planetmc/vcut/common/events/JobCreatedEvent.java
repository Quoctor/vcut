package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class JobCreatedEvent {
    private UUID jobId;
    private String jobType;
    private Integer priority;
    private Object payload;
    private LocalDateTime timestamp;
}
