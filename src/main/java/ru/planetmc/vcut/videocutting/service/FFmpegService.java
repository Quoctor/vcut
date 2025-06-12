package ru.planetmc.vcut.videocutting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FFmpegService {

    public Mono<String> cutVideo(String inputPath, String outputPath,
                                 BigDecimal startTime, BigDecimal endTime,
                                 String outputFormat, String quality) {
        return Mono.fromCallable(() -> {
            List<String> command = buildFFmpegCommand(inputPath, outputPath, startTime, endTime, outputFormat, quality);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();

                // Read output for monitoring
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("FFmpeg output: {}", line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("Video cutting completed successfully: {}", outputPath);
                    return outputPath;
                } else {
                    throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error executing FFmpeg command", e);
                throw new RuntimeException("Video cutting failed", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<String> buildFFmpegCommand(String inputPath, String outputPath,
                                            BigDecimal startTime, BigDecimal endTime,
                                            String outputFormat, String quality) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputPath);
        command.add("-ss");
        command.add(startTime.toString());
        command.add("-to");
        command.add(endTime.toString());
        command.add("-c:v");

        // Set video codec based on quality
        switch (quality.toLowerCase()) {
            case "hd":
                command.add("libx264");
                command.add("-crf");
                command.add("23");
                break;
            case "medium":
                command.add("libx264");
                command.add("-crf");
                command.add("28");
                break;
            case "low":
                command.add("libx264");
                command.add("-crf");
                command.add("32");
                break;
            default:
                command.add("libx264");
                command.add("-crf");
                command.add("23");
        }

        command.add("-c:a");
        command.add("aac");
        command.add("-movflags");
        command.add("+faststart");
        command.add("-y"); // Overwrite output file
        command.add(outputPath);

        return command;
    }

    public Mono<Void> deleteFile(String filePath) {
        return Mono.fromRunnable(() -> {
            try {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("Deleted file: {}", filePath);
                }
            } catch (IOException e) {
                log.error("Error deleting file: {}", filePath, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<VideoMetadata> extractVideoMetadata(String filePath) {
        return Mono.fromCallable(() -> {
            List<String> command = new ArrayList<>();
            command.add("ffprobe");
            command.add("-v");
            command.add("quiet");
            command.add("-print_format");
            command.add("json");
            command.add("-show_format");
            command.add("-show_streams");
            command.add(filePath);

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            try {
                Process process = processBuilder.start();
                StringBuilder output = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return parseVideoMetadata(output.toString());
                } else {
                    throw new RuntimeException("FFprobe process failed with exit code: " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error extracting video metadata", e);
                throw new RuntimeException("Metadata extraction failed", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private VideoMetadata parseVideoMetadata(String jsonOutput) {
        // Parse JSON output from ffprobe
        // This is a simplified implementation
        return VideoMetadata.builder()
                .duration(BigDecimal.valueOf(0)) // Parse from JSON
                .resolution("1920x1080") // Parse from JSON
                .frameRate(BigDecimal.valueOf(30)) // Parse from JSON
                .bitrate(1000000) // Parse from JSON
                .codec("h264") // Parse from JSON
                .build();
    }
}
