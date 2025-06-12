package ru.planetmc.vcut.videoupload.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.planetmc.vcut.videoupload.domain.VideoUpload;

import java.util.UUID;

public interface VideoUploadRepository extends R2dbcRepository<VideoUpload, UUID> {

    Flux<VideoUpload> findByUserId(String userId);

    Flux<VideoUpload> findByUploadStatus(VideoUpload.UploadStatus status);

    Mono<VideoUpload> findByYoutubeVideoId(String youtubeVideoId);

    @Query("SELECT * FROM video_uploads WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<VideoUpload> findByUserIdWithPagination(String userId, int limit, int offset);

    @Query("SELECT COUNT(*) FROM video_uploads WHERE user_id = :userId")
    Mono<Long> countByUserId(String userId);
}
