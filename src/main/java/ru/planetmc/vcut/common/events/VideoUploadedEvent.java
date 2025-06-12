package ru.planetmc.vcut.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VideoUploadedEvent {
    private UUID uploadId;
    private String userId;
    private String filename;
    private Long fileSize;
    private String mimeType;
    private String storagePath;
    private String youtubeUrl;
    private LocalDateTime timestamp;
}
