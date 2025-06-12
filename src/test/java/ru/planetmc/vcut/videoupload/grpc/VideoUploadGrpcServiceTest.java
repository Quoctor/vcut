package ru.planetmc.vcut.videoupload.grpc;

import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import ru.planetmc.vcut.videoupload.domain.VideoUpload;
import ru.planetmc.vcut.videoupload.service.VideoUploadService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoUploadGrpcServiceTest {

    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Mock
    private VideoUploadService videoUploadService;

    private VideoUploadServiceGrpc.VideoUploadServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new VideoUploadGrpcService(videoUploadService))
                .build()
                .start());

        blockingStub = VideoUploadServiceGrpc.newBlockingStub(
                grpcCleanup.register(InProcessChannelBuilder
                        .forName(serverName)
                        .directExecutor()
                        .build()));
    }

    @Test
    void shouldInitiateUploadSuccessfully() {
        // Given
        VideoUpload mockUpload = VideoUpload.builder()
                .id(UUID.randomUUID())
                .userId("test-user")
                .originalFilename("test.mp4")
                .fileSize(1024L)
                .uploadStatus(VideoUpload.UploadStatus.PENDING)
                .build();

        when(videoUploadService.initiateUpload(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mockUpload));

        // When
        InitiateUploadRequest request = InitiateUploadRequest.newBuilder()
                .setUserId("test-user")
                .setFilename("test.mp4")
                .setFileSize(1024L)
                .setMimeType("video/mp4")
                .build();

        InitiateUploadResponse response = blockingStub.initiateUpload(request);

        // Then
        assertThat(response.getUploadId()).isEqualTo(mockUpload.getId().toString());
        assertThat(response.getChunkSize()).isPositive();
    }
}
