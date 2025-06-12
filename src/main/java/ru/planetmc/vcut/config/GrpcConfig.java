package ru.planetmc.vcut.config;

import io.grpc.ServerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    @Bean
    public GrpcServerBuilderConfigurer grpcServerBuilderConfigurer() {
        return serverBuilder -> {
            if (serverBuilder instanceof ServerBuilder) {
                ((ServerBuilder<?>) serverBuilder)
                        .maxInboundMessageSize(50 * 1024 * 1024) // 50MB for video uploads
                        .maxInboundMetadataSize(8192)
                        .keepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                        .keepAliveTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .permitKeepAliveWithoutCalls(true);
            }
            return serverBuilder;
        };
    }
}
