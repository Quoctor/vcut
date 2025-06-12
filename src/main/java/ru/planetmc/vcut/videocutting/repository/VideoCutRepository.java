package ru.planetmc.vcut.videocutting.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.planetmc.vcut.videocutting.domain.VideoCut;

import java.util.UUID;

public interface VideoCutRepository extends R2dbcRepository<VideoCut, UUID> {

    Flux<VideoCut> findByUserId(String userId);

    Flux<VideoCut> findByVideoUploadId(UUID videoUploadId);

    Flux<VideoCut> findByCuttingStatus(VideoCut.CuttingStatus status);

    @Query("SELECT * FROM video_cuts WHERE user_id = :userId AND video_upload_id = :videoId ORDER BY created_at DESC")
    Flux<VideoCut> findByUserIdAndVideoId(String userId, UUID videoId);

    @Query("SELECT COUNT(*) FROM video_cuts WHERE user_id = :userId")
    Mono<Long> countByUserId(String userId);
}
