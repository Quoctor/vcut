package ru.planetmc.vcut.streams.processors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class DeduplicationProcessor<K, V> implements Processor<K, V, K, V> {

    private ProcessorContext<K, V> context;
    private KeyValueStore<K, Long> deduplicationStore;
    private final Duration retentionPeriod;

    public DeduplicationProcessor(Duration retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    @Override
    public void init(ProcessorContext<K, V> context) {
        this.context = context;
        this.deduplicationStore = context.getStateStore("deduplication-store");
    }

    @Override
    public void process(Record<K, V> record) {
        final long eventTime = record.timestamp();
        final K key = record.key();

        // Check if we've seen this key recently
        Long lastSeen = deduplicationStore.get(key);

        if (lastSeen == null ||
                Instant.ofEpochMilli(eventTime).isAfter(
                        Instant.ofEpochMilli(lastSeen).plus(retentionPeriod))) {

            // Forward the record
            context.forward(record);

            // Update the store
            deduplicationStore.put(key, eventTime);

            log.debug("Processed record with key: {}", key);
        } else {
            log.debug("Deduplicated record with key: {}", key);
        }
    }
}
