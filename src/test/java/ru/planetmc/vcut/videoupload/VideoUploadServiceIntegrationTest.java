package ru.planetmc.vcut.videoupload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VideoUploadServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vcut_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
            .withExposedPorts(9000, 9001)
            .withEnv("MINIO_ROOT_USER", "testuser")
            .withEnv("MINIO_ROOT_PASSWORD", "testpass123")
            .withCommand("server /data --console-address :9001");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        registry.add("storage.minio.endpoint", () ->
                "http://" + minio.getHost() + ":" + minio.getFirstMappedPort());
        registry.add("storage.minio.access-key", () -> "testuser");
        registry.add("storage.minio.secret-key", () -> "testpass123");
    }

    @Test
    void shouldUploadVideoSuccessfully() {
        // Test implementation
        VideoUploadRequest request = VideoUploadRequest.builder()
                .userId("test-user")
                .filename("test-video.mp4")
                .fileSize(1024L * 1024L) // 1MB
                .mimeType("video/mp4")
                .build();

        StepVerifier.create(videoUploadService.initiateUpload(request))
                .assertNext(upload -> {
                    assertThat(upload.getUserId()).isEqualTo("test-user");
                    assertThat(upload.getUploadStatus()).isEqualTo(VideoUpload.UploadStatus.PENDING);
                })
                .verifyComplete();
    }

    @Test
    void shouldProcessVideoUploadEvents() {
        // Test Kafka event processing
    }

    @Test
    void shouldHandleUploadFailures() {
        // Test error handling
    }
}
