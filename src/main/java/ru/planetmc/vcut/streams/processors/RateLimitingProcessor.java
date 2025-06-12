package ru.planetmc.vcut.streams.processors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class RateLimitingProcessor<K, V> implements Processor<K, V, K, V> {

    private ProcessorContext<K, V> context;
    private WindowStore<K, Long> rateLimitStore;
    private final int maxRequestsPerWindow;
    private final Duration windowSize;

    public RateLimitingProcessor(int maxRequestsPerWindow, Duration windowSize) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSize = windowSize;
    }

    @Override
    public void init(ProcessorContext<K, V> context) {
        this.context = context;
        this.rateLimitStore = context.getStateStore("rate-limit-store");
    }

    @Override
    public void process(Record<K, V> record) {
        final long eventTime = record.timestamp();
        final K key = record.key();

        // Count requests in the current window
        long windowStart = Instant.ofEpochMilli(eventTime).minus(windowSize).toEpochMilli();

        long requestCount = 0;
        try (var iterator = rateLimitStore.fetch(key, windowStart, eventTime)) {
            while (iterator.hasNext()) {
                requestCount += iterator.next().value;
            }
        }

        if (requestCount < maxRequestsPerWindow) {
            // Forward the record
            context.forward(record);

            // Record this request
            rateLimitStore.put(key, eventTime, 1L);

            log.debug("Processed rate-limited record for key: {} (count: {})", key, requestCount + 1);
        } else {
            log.warn("Rate limit exceeded for key: {} (count: {})", key, requestCount);

            // Optionally forward to a dead letter queue
            context.forward(record.withHeaders(record.headers().add("rate-limit-exceeded", "true".getBytes())),
                    "rate-limit-violations");
        }
    }
}
