package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VideoCutCreatedEvent {
    private UUID cutId;
    private UUID videoUploadId;
    private String userId;
    private BigDecimal startTime;
    private BigDecimal endTime;
    private String outputFormat;
    private String quality;
    private LocalDateTime timestamp;
}
