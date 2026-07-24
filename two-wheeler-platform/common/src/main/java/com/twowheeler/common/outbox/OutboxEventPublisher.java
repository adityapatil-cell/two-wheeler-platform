package com.twowheeler.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Base publisher — wraps business logic + outbox write in one transaction.
 *
 * Services extend this class and call publishEvent() inside their
 * @Transactional service methods. The outbox row is written in the same
 * DB transaction as the business row — atomicity guaranteed.
 *
 * Debezium CDC then picks up the outbox row from Postgres WAL and
 * publishes it to Kafka without any application code needing to call Kafka directly.
 *
 * Usage in RepairService:
 *
 *   @Transactional
 *   public RepairOrderDto updateStatus(UUID repairOrderId, UpdateStatusRequest req) {
 *       RepairOrder order = repairRepository.findById(repairOrderId).orElseThrow(...);
 *       order.setStatus(req.getStatus());
 *       repairRepository.save(order);                    // business write
 *
 *       publishEvent(                                     // outbox write (same tx)
 *           "repair.status_changed",
 *           "repair.status_changed",
 *           repairOrderId.toString(),
 *           RepairStatusChangedEvent.from(order)
 *       );
 *
 *       return RepairOrderMapper.toDto(order);
 *   }
 */
@Slf4j
@RequiredArgsConstructor
public abstract class OutboxEventPublisher<E extends OutboxEvent> {

    private final JpaRepository<E, UUID> outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Write an outbox event row — must be called inside a @Transactional method.
     * Propagation.MANDATORY ensures this is never called outside a transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishEvent(String topic, String eventType,
                              String aggregateId, Object payload) {
        try {
            String json = buildEnvelope(eventType, aggregateId, payload);
            E outboxEvent = createOutboxEvent(topic, eventType, aggregateId, json);
            outboxRepository.save(outboxEvent);
            log.debug("Outbox event written: topic={} eventType={} aggregateId={}",
                topic, eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for eventType={}", eventType, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    /**
     * Each service implements this to return its concrete OutboxEvent subclass.
     * Example in RepairService: return new RepairOutboxEvent(topic, eventType, aggregateId, json)
     */
    protected abstract E createOutboxEvent(String topic, String eventType,
                                            String aggregateId, String json);

    /**
     * Wraps the payload in the standard event envelope (Phase 1 Kafka contracts).
     * Every Kafka message has: eventId, eventType, version, timestamp, traceId, data
     */
    private String buildEnvelope(String eventType, String aggregateId,
                                  Object payload) throws JsonProcessingException {
        EventEnvelope envelope = EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .version("1.0")
            .timestamp(Instant.now().toString())
            .aggregateId(aggregateId)
            .data(payload)
            .build();
        return objectMapper.writeValueAsString(envelope);
    }

    // ─── Envelope DTO — matches the Kafka contract defined in Phase 1 ────────

    @lombok.Builder
    @lombok.Getter
    private static class EventEnvelope {
        private String eventId;
        private String eventType;
        private String version;
        private String timestamp;
        private String aggregateId;
        private Object data;
    }
}
