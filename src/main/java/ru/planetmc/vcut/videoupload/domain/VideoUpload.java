package ru.planetmc.vcut.videoupload.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Table("video_uploads")
public class VideoUpload {
    @Id
    private UUID id;
    private String userId;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private UploadStatus uploadStatus;
    private String storagePath;
    private String storageBucket;
    private Integer uploadProgress;
    private String errorMessage;
    private String youtubeUrl;
    private String youtubeVideoId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum UploadStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }
}
