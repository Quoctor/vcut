package ru.planetmc.vcut.performance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.planetmc.vcut.videoupload.domain.VideoUpload;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("performance-test")
class VideoProcessingPerformanceTest {

    @Test
    void shouldHandleConcurrentUploads() {
        int concurrentUploads = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        Flux<VideoUploadRequest> requests = Flux.range(1, concurrentUploads)
                .map(i -> VideoUploadRequest.builder()
                        .userId("user-" + i)
                        .filename("video-" + i + ".mp4")
                        .fileSize(1024L * 1024L)
                        .mimeType("video/mp4")
                        .build());

        StepVerifier.create(
                        requests.flatMap(request ->
                                videoUploadService.initiateUpload(request)
                                        .doOnSuccess(upload -> successCount.incrementAndGet())
                                        .doOnError(error -> errorCount.incrementAndGet())
                                        .onErrorResume(error -> Mono.empty()), 10) // Concurrency of 10
                )
                .expectNextCount(concurrentUploads)
                .expectComplete()
                .verify(Duration.ofMinutes(5));

        // Assert performance metrics
        assertThat(successCount.get()).isEqualTo(concurrentUploads);
        assertThat(errorCount.get()).isZero();
    }

    @Test
    void shouldProcessLargeVideoFiles() {
        // Test with large file sizes
        long largeFileSize = 500L * 1024L * 1024L; // 500MB

        VideoUploadRequest request = VideoUploadRequest.builder()
                .userId("test-user")
                .filename("large-video.mp4")
                .fileSize(largeFileSize)
                .mimeType("video/mp4")
                .build();

        StepVerifier.create(videoUploadService.initiateUpload(request))
                .assertNext(upload -> {
                    assertThat(upload.getFileSize()).isEqualTo(largeFileSize);
                    assertThat(upload.getUploadStatus()).isEqualTo(VideoUpload.UploadStatus.PENDING);
                })
                .verifyComplete();
    }
}
