package ru.planetmc.vcut.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;
import ru.planetmc.vcut.common.events.*;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class VideoProcessingStreams {

    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {

        // Define serdes for event types
        JsonSerde<VideoUploadedEvent> videoUploadedSerde = new JsonSerde<>(VideoUploadedEvent.class);
        JsonSerde<VideoCutCreatedEvent> videoCutCreatedSerde = new JsonSerde<>(VideoCutCreatedEvent.class);
        JsonSerde<VideoProcessingCompletedEvent> processingCompletedSerde = new JsonSerde<>(VideoProcessingCompletedEvent.class);
        JsonSerde<NotificationEvent> notificationSerde = new JsonSerde<>(NotificationEvent.class);

        // Video Upload Processing Stream
        KStream<String, VideoUploadedEvent> videoUploadedStream = streamsBuilder
                .stream("video-uploaded", Consumed.with(Serdes.String(), videoUploadedSerde))
                .peek((key, value) -> log.info("Processing video upload event: {}", value.getUploadId()));

        // Trigger metadata extraction when video is uploaded
        videoUploadedStream
                .mapValues(this::createMetadataExtractionJob)
                .to("video-metadata-extraction", Produced.with(Serdes.String(), new JsonSerde<>(JobCreatedEvent.class)));

        // Trigger AI analysis when video is uploaded
        videoUploadedStream
                .filter((key, value) -> value.getFileSize() > 10_000_000) // Only process videos > 10MB
                .mapValues(this::createAiAnalysisJob)
                .to("video-ai-analysis", Produced.with(Serdes.String(), new JsonSerde<>(JobCreatedEvent.class)));

        // Video Cut Processing Stream
        KStream<String, VideoCutCreatedEvent> videoCutStream = streamsBuilder
                .stream("video-cut-created", Consumed.with(Serdes.String(), videoCutCreatedSerde))
                .peek((key, value) -> log.info("Processing video cut event: {}", value.getCutId()));

        // Create cutting job for each cut request
        videoCutStream
                .mapValues(this::createCuttingJob)
                .to("video-cutting-jobs", Produced.with(Serdes.String(), new JsonSerde<>(JobCreatedEvent.class)));

        // Video Processing Completion Stream
        KStream<String, VideoProcessingCompletedEvent> processingCompletedStream = streamsBuilder
                .stream("video-processing-completed", Consumed.with(Serdes.String(), processingCompletedSerde));

        // Join processing completion with upload events to create notifications
        KTable<String, VideoUploadedEvent> uploadsTable = videoUploadedStream
                .groupByKey()
                .reduce((latest, current) -> current,
                        Materialized.with(Serdes.String(), videoUploadedSerde));

        processingCompletedStream
                .join(uploadsTable,
                        (completion, upload) -> createProcessingNotification(completion, upload),
                        Joined.with(Serdes.String(), processingCompletedSerde, videoUploadedSerde))
                .to("notification-events", Produced.with(Serdes.String(), notificationSerde));

        // Error Handling Stream
        buildErrorHandlingStream(streamsBuilder);

        // Analytics Stream
        buildAnalyticsStream(streamsBuilder, videoUploadedStream, videoCutStream);

        // User Activity Stream
        buildUserActivityStream(streamsBuilder, videoUploadedStream, videoCutStream);
    }

    private JobCreatedEvent createMetadataExtractionJob(VideoUploadedEvent uploadEvent) {
        return JobCreatedEvent.builder()
                .jobId(java.util.UUID.randomUUID())
                .jobType("METADATA_EXTRACTION")
                .priority(5)
                .payload(uploadEvent)
                .build();
    }

    private JobCreatedEvent createAiAnalysisJob(VideoUploadedEvent uploadEvent) {
        return JobCreatedEvent.builder()
                .jobId(java.util.UUID.randomUUID())
                .jobType("AI_ANALYSIS")
                .priority(3)
                .payload(uploadEvent)
                .build();
    }

    private JobCreatedEvent createCuttingJob(VideoCutCreatedEvent cutEvent) {
        return JobCreatedEvent.builder()
                .jobId(java.util.UUID.randomUUID())
                .jobType("VIDEO_CUTTING")
                .priority(1) // High priority for user-facing operations
                .payload(cutEvent)
                .build();
    }

    private NotificationEvent createProcessingNotification(VideoProcessingCompletedEvent completion,
                                                           VideoUploadedEvent upload) {
        return NotificationEvent.builder()
                .userId(upload.getUserId())
                .type("VIDEO_PROCESSING_COMPLETED")
                .title("Video Processing Complete")
                .message(String.format("Your video '%s' has been processed successfully", upload.getFilename()))
                .metadata(Map.of(
                        "videoId", upload.getUploadId().toString(),
                        "processingTime", completion.getProcessingTimeMs().toString()
                ))
                .build();
    }

    private void buildErrorHandlingStream(StreamsBuilder streamsBuilder) {
        JsonSerde<ErrorEvent> errorSerde = new JsonSerde<>(ErrorEvent.class);

        // Error events from all services
        KStream<String, ErrorEvent> errorStream = streamsBuilder
                .stream("error-events", Consumed.with(Serdes.String(), errorSerde));

        // Critical errors trigger immediate notifications
        errorStream
                .filter((key, error) -> "CRITICAL".equals(error.getSeverity()))
                .mapValues(this::createErrorNotification)
                .to("notification-events", Produced.with(Serdes.String(), new JsonSerde<>(NotificationEvent.class)));

        // All errors are logged to monitoring
        errorStream
                .mapValues(this::createMonitoringAlert)
                .to("monitoring-alerts", Produced.with(Serdes.String(), new JsonSerde<>(MonitoringAlert.class)));
    }

    private void buildAnalyticsStream(StreamsBuilder streamsBuilder,
                                      KStream<String, VideoUploadedEvent> uploads,
                                      KStream<String, VideoCutCreatedEvent> cuts) {

        // Upload analytics
        uploads
                .groupBy((key, value) -> value.getUserId())
                .windowedBy(TimeWindows.of(Duration.ofHours(1)))
                .count(Materialized.as("user-uploads-per-hour"))
                .toStream()
                .map((windowedKey, count) -> KeyValue.pair(
                        windowedKey.key(),
                        UserAnalytics.builder()
                                .userId(windowedKey.key())
                                .uploads(count.intValue())
                                .window(windowedKey.window())
                                .build()))
                .to("user-analytics", Produced.with(Serdes.String(), new JsonSerde<>(UserAnalytics.class)));

        // Video format analytics
        uploads
                .groupBy((key, value) -> extractFormat(value.getMimeType()))
                .windowedBy(TimeWindows.of(Duration.ofDays(1)))
                .count(Materialized.as("format-popularity-daily"))
                .toStream()
                .to("format-analytics", Produced.with(Serdes.String(), Serdes.Long()));

        // Cut duration analytics
        cuts
                .mapValues(cut -> cut.getEndTime().subtract(cut.getStartTime()))
                .groupBy((key, duration) -> getDurationCategory(duration))
                .windowedBy(TimeWindows.of(Duration.ofDays(1)))
                .count(Materialized.as("cut-duration-categories"))
                .toStream()
                .to("duration-analytics", Produced.with(Serdes.String(), Serdes.Long()));
    }

    private void buildUserActivityStream(StreamsBuilder streamsBuilder,
                                         KStream<String, VideoUploadedEvent> uploads,
                                         KStream<String, VideoCutCreatedEvent> cuts) {

        // Merge user activities
        KStream<String, UserActivity> uploadActivities = uploads
                .map((key, upload) -> KeyValue.pair(
                        upload.getUserId(),
                        UserActivity.builder()
                                .userId(upload.getUserId())
                                .activityType("UPLOAD")
                                .timestamp(upload.getTimestamp())
                                .metadata(Map.of("videoId", upload.getUploadId().toString()))
                                .build()));

        KStream<String, UserActivity> cutActivities = cuts
                .map((key, cut) -> KeyValue.pair(
                        cut.getUserId(),
                        UserActivity.builder()
                                .userId(cut.getUserId())
                                .activityType("CUT")
                                .timestamp(cut.getTimestamp())
                                .metadata(Map.of("cutId", cut.getCutId().toString()))
                                .build()));

        // Combine all activities
        uploadActivities
                .merge(cutActivities)
                .groupByKey()
                .windowedBy(SessionWindows.with(Duration.ofMinutes(30)))
                .aggregate(
                        UserSession::new,
                        (key, activity, session) -> session.addActivity(activity),
                        (key, session1, session2) -> session1.merge(session2),
                        Materialized.with(Serdes.String(), new JsonSerde<>(UserSession.class)))
                .toStream()
                .to("user-sessions", Produced.with(
                        WindowedSerdes.sessionWindowedSerdeFrom(String.class),
                        new JsonSerde<>(UserSession.class)));
    }

    private NotificationEvent createErrorNotification(ErrorEvent error) {
        return NotificationEvent.builder()
                .userId("ADMIN") // Notify administrators
                .type("SYSTEM_ERROR")
                .title("Critical System Error")
                .message(String.format("Critical error in %s: %s", error.getService(), error.getMessage()))
                .metadata(Map.of(
                        "errorId", error.getErrorId(),
                        "service", error.getService(),
                        "severity", error.getSeverity()
                ))
                .build();
    }

    private MonitoringAlert createMonitoringAlert(ErrorEvent error) {
        return MonitoringAlert.builder()
                .alertId(java.util.UUID.randomUUID().toString())
                .service(error.getService())
                .severity(error.getSeverity())
                .message(error.getMessage())
                .timestamp(error.getTimestamp())
                .metadata(error.getMetadata())
                .build();
    }

    private String extractFormat(String mimeType) {
        if (mimeType.contains("mp4")) return "MP4";
        if (mimeType.contains("avi")) return "AVI";
        if (mimeType.contains("mov")) return "MOV";
        if (mimeType.contains("mkv")) return "MKV";
        return "OTHER";
    }

    private String getDurationCategory(java.math.BigDecimal duration) {
        double seconds = duration.doubleValue();
        if (seconds <= 15) return "VERY_SHORT";
        if (seconds <= 60) return "SHORT";
        if (seconds <= 300) return "MEDIUM";
        if (seconds <= 600) return "LONG";
        return "VERY_LONG";
    }
}
