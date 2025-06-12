package ru.planetmc.vcut.videocutting.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Table("video_cuts")
public class VideoCut {
    @Id
    private UUID id;
    private UUID videoUploadId;
    private String userId;
    private String cutName;
    private BigDecimal startTime;
    private BigDecimal endTime;
    private BigDecimal duration;
    private String outputFormat;
    private String quality;
    private CuttingStatus cuttingStatus;
    private String outputFilePath;
    private Long outputFileSize;
    private JsonNode cuttingParameters;
    private String errorMessage;
    private Integer processingTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum CuttingStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }
}
