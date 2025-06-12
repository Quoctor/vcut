package ru.planetmc.vcut.videoprocessing.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.planetmc.vcut.common.events.VideoCutCreatedEvent;
import ru.planetmc.vcut.common.events.VideoUploadedEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingEventHandler {

    private final VideoProcessingService videoProcessingService;

    @KafkaListener(topics = "video-uploaded", groupId = "video-processing-group")
    public void handleVideoUploaded(VideoUploadedEvent event) {
        log.info("Received video uploaded event for upload ID: {}", event.getUploadId());

        videoProcessingService.processVideo(event.getUploadId(), event.getStoragePath())
                .subscribe(
                        result -> log.info("Video processing completed for upload ID: {}", event.getUploadId()),
                        error -> log.error("Video processing failed for upload ID: {}", event.getUploadId(), error)
                );
    }

    @KafkaListener(topics = "video-cut-created", groupId = "video-cutting-group")
    public void handleVideoCutCreated(VideoCutCreatedEvent event) {
        log.info("Received video cut created event for cut ID: {}", event.getCutId());

        videoProcessingService.processCut(event)
                .subscribe(
                        result -> log.info("Video cut processing completed for cut ID: {}", event.getCutId()),
                        error -> log.error("Video cut processing failed for cut ID: {}", event.getCutId(), error)
                );
    }
}
