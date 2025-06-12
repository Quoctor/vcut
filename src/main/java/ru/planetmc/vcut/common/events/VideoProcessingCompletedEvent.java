package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VideoProcessingCompletedEvent {
    private UUID jobId;
    private UUID videoId;
    private String userId;
    private String status; // COMPLETED, FAILED
    private String outputPath;
    private Long outputSize;
    private Long processingTimeMs;
    private String errorMessage;
    private LocalDateTime timestamp;
}
