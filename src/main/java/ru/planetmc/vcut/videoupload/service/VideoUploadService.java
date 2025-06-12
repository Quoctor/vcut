package ru.planetmc.vcut.videoupload.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.planetmc.vcut.videoupload.domain.VideoUpload;
import ru.planetmc.vcut.videoupload.repository.VideoUploadRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUploadService {

    private final VideoUploadRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectStorageService objectStorageService;

    @Transactional
    public Mono<VideoUpload> initiateUpload(String userId, String filename, Long fileSize, String mimeType, String youtubeUrl) {
        VideoUpload upload = VideoUpload.builder()
                .userId(userId)
                .originalFilename(filename)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .youtubeUrl(youtubeUrl)
                .uploadStatus(VideoUpload.UploadStatus.PENDING)
                .uploadProgress(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.save(upload)
                .doOnSuccess(savedUpload -> {
                    log.info("Initiated upload for user: {} with ID: {}", userId, savedUpload.getId());
                    publishVideoUploadInitiatedEvent(savedUpload);
                });
    }

    @Transactional
    public Mono<VideoUpload> updateUploadProgress(UUID uploadId, Integer progress) {
        return repository.findById(uploadId)
                .flatMap(upload -> {
                    upload.setUploadProgress(progress);
                    upload.setUpdatedAt(LocalDateTime.now());
                    if (progress >= 100) {
                        upload.setUploadStatus(VideoUpload.UploadStatus.COMPLETED);
                    }
                    return repository.save(upload);
                })
                .doOnSuccess(upload -> {
                    if (upload.getUploadStatus() == VideoUpload.UploadStatus.COMPLETED) {
                        publishVideoUploadedEvent(upload);
                    }
                });
    }

    public Mono<VideoUpload> getUploadStatus(UUID uploadId) {
        return repository.findById(uploadId);
    }

    public Flux<VideoUpload> getUserUploads(String userId, int page, int size) {
        return repository.findByUserIdWithPagination(userId, size, page * size);
    }

    @Transactional
    public Mono<VideoUpload> markUploadFailed(UUID uploadId, String errorMessage) {
        return repository.findById(uploadId)
                .flatMap(upload -> {
                    upload.setUploadStatus(VideoUpload.UploadStatus.FAILED);
                    upload.setErrorMessage(errorMessage);
                    upload.setUpdatedAt(LocalDateTime.now());
                    return repository.save(upload);
                });
    }

    private void publishVideoUploadInitiatedEvent(VideoUpload upload) {
        VideoUploadedEvent event = VideoUploadedEvent.builder()
                .uploadId(upload.getId())
                .userId(upload.getUserId())
                .filename(upload.getOriginalFilename())
                .fileSize(upload.getFileSize())
                .mimeType(upload.getMimeType())
                .youtubeUrl(upload.getYoutubeUrl())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("video-upload-initiated", upload.getId().toString(), event);
    }

    private void publishVideoUploadedEvent(VideoUpload upload) {
        VideoUploadedEvent event = VideoUploadedEvent.builder()
                .uploadId(upload.getId())
                .userId(upload.getUserId())
                .filename(upload.getOriginalFilename())
                .fileSize(upload.getFileSize())
                .storagePath(upload.getStoragePath())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("video-uploaded", upload.getId().toString(), event);
        log.info("Published video uploaded event for upload ID: {}", upload.getId());
    }
}
