package ru.planetmc.vcut.videocutting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.planetmc.vcut.videocutting.domain.VideoCut;
import ru.planetmc.vcut.videocutting.repository.VideoCutRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCuttingService {

    private final VideoCutRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FFmpegService ffmpegService;

    @Transactional
    public Mono<VideoCut> createCut(UUID videoUploadId, String userId, String cutName,
                                    BigDecimal startTime, BigDecimal endTime,
                                    String outputFormat, String quality) {

        BigDecimal duration = endTime.subtract(startTime);

        VideoCut cut = VideoCut.builder()
                .videoUploadId(videoUploadId)
                .userId(userId)
                .cutName(cutName)
                .startTime(startTime)
                .endTime(endTime)
                .duration(duration)
                .outputFormat(outputFormat)
                .quality(quality)
                .cuttingStatus(VideoCut.CuttingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.save(cut)
                .doOnSuccess(savedCut -> {
                    log.info("Created cut for video: {} with ID: {}", videoUploadId, savedCut.getId());
                    publishVideoCutCreatedEvent(savedCut);
                });
    }

    public Mono<VideoCut> getCutStatus(UUID cutId) {
        return repository.findById(cutId);
    }

    public Flux<VideoCut> getUserCuts(String userId, UUID videoId) {
        if (videoId != null) {
            return repository.findByUserIdAndVideoId(userId, videoId);
        }
        return repository.findByUserId(userId);
    }

    @Transactional
    public Mono<VideoCut> updateCutStatus(UUID cutId, VideoCut.CuttingStatus status,
                                          String outputPath, Long outputSize) {
        return repository.findById(cutId)
                .flatMap(cut -> {
                    cut.setCuttingStatus(status);
                    cut.setOutputFilePath(outputPath);
                    cut.setOutputFileSize(outputSize);
                    cut.setUpdatedAt(LocalDateTime.now());
                    return repository.save(cut);
                })
                .doOnSuccess(cut -> {
                    if (status == VideoCut.CuttingStatus.COMPLETED) {
                        publishVideoCutCompletedEvent(cut);
                    }
                });
    }

    @Transactional
    public Mono<Void> deleteCut(UUID cutId, String userId) {
        return repository.findById(cutId)
                .filter(cut -> cut.getUserId().equals(userId))
                .flatMap(cut -> {
                    // Delete file from storage
                    return ffmpegService.deleteFile(cut.getOutputFilePath())
                            .then(repository.deleteById(cutId));
                })
                .then();
    }

    private void publishVideoCutCreatedEvent(VideoCut cut) {
        VideoCutCreatedEvent event = VideoCutCreatedEvent.builder()
                .cutId(cut.getId())
                .videoUploadId(cut.getVideoUploadId())
                .userId(cut.getUserId())
                .startTime(cut.getStartTime())
                .endTime(cut.getEndTime())
                .outputFormat(cut.getOutputFormat())
                .quality(cut.getQuality())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("video-cut-created", cut.getId().toString(), event);
    }

    private void publishVideoCutCompletedEvent(VideoCut cut) {
        kafkaTemplate.send("video-cut-completed", cut.getId().toString(), cut);
        log.info("Published video cut completed event for cut ID: {}", cut.getId());
    }
}
