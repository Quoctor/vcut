package ru.planetmc.vcut.videoupload.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import ru.planetmc.vcut.common.proto.CommonProto;
import ru.planetmc.vcut.videoupload.proto.VideoUploadProto;
import ru.planetmc.vcut.videoupload.proto.VideoUploadServiceGrpc;
import ru.planetmc.vcut.videoupload.service.VideoUploadService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class VideoUploadGrpcService extends VideoUploadServiceGrpc.VideoUploadServiceImplBase {

    private final VideoUploadService videoUploadService;

    @Override
    public void initiateUpload(VideoUploadProto.InitiateUploadRequest request,
                               StreamObserver<VideoUploadProto.InitiateUploadResponse> responseObserver) {
        videoUploadService.initiateUpload(
                        request.getUserId(),
                        request.getFilename(),
                        request.getFileSize(),
                        request.getMimeType(),
                        request.getYoutubeUrl()
                )
                .subscribe(
                        upload -> {
                            VideoUploadProto.InitiateUploadResponse response = VideoUploadProto.InitiateUploadResponse.newBuilder()
                                    .setUploadId(upload.getId().toString())
                                    .setChunkSize(1024 * 1024) // 1MB chunks
                                    .build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        error -> {
                            log.error("Error initiating upload", error);
                            responseObserver.onError(error);
                        }
                );
    }

    @Override
    public StreamObserver<VideoUploadProto.UploadChunkRequest> uploadChunk(
            StreamObserver<VideoUploadProto.UploadChunkResponse> responseObserver) {
        return new StreamObserver<>() {
            private String uploadId;
            private int chunksReceived = 0;

            @Override
            public void onNext(VideoUploadProto.UploadChunkRequest request) {
                if (uploadId == null) {
                    uploadId = request.getUploadId();
                }

                chunksReceived++;

                // Process chunk (save to storage)
                // Update progress
                int progress = request.getIsLastChunk() ? 100 :
                        Math.min(95, (chunksReceived * 10)); // Rough progress estimation

                videoUploadService.updateUploadProgress(UUID.fromString(uploadId), progress)
                        .subscribe();

                VideoUploadProto.UploadChunkResponse response = VideoUploadProto.UploadChunkResponse.newBuilder()
                        .setUploadId(uploadId)
                        .setChunksReceived(chunksReceived)
                        .setUploadComplete(request.getIsLastChunk())
                        .build();

                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error during chunk upload", t);
                if (uploadId != null) {
                    videoUploadService.markUploadFailed(UUID.fromString(uploadId), t.getMessage())
                            .subscribe();
                }
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getUploadStatus(VideoUploadProto.GetUploadStatusRequest request,
                                StreamObserver<VideoUploadProto.GetUploadStatusResponse> responseObserver) {
        UUID uploadId = UUID.fromString(request.getUploadId());

        videoUploadService.getUploadStatus(uploadId)
                .subscribe(
                        upload -> {
                            CommonProto.ProcessingStatus status = CommonProto.ProcessingStatus.newBuilder()
                                    .setJobId(upload.getId().toString())
                                    .setProgress(upload.getUploadProgress())
                                    .setMessage(upload.getErrorMessage() != null ? upload.getErrorMessage() : "")
                                    .build();

                            CommonProto.VideoInfo videoInfo = CommonProto.VideoInfo.newBuilder()
                                    .setId(upload.getId().toString())
                                    .setUserId(upload.getUserId())
                                    .setFilename(upload.getOriginalFilename())
                                    .setFileSize(upload.getFileSize())
                                    .setMimeType(upload.getMimeType())
                                    .build();

                            VideoUploadProto.GetUploadStatusResponse response = VideoUploadProto.GetUploadStatusResponse.newBuilder()
                                    .setStatus(status)
                                    .setVideoInfo(videoInfo)
                                    .build();

                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        error -> {
                            log.error("Error getting upload status", error);
                            responseObserver.onError(error);
                        }
                );
    }
}
