package com.twowheeler.common.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern — JPA entity for the outbox table.
 *
 * Every service schema that publishes Kafka events has its own outbox table.
 * Example: repair schema has a "repair_outbox" table, towing schema has "towing_outbox".
 *
 * How it works (Outbox pattern):
 *   1. Service writes the business row (e.g. repair_orders) AND
 *      an OutboxEvent row in the SAME database transaction.
 *   2. Debezium CDC watches the outbox table via Postgres WAL.
 *   3. Debezium publishes the outbox row to Kafka automatically.
 *   4. If the DB transaction fails → neither row is written → no orphan event.
 *   5. If Kafka is down → event stays in outbox → Debezium retries when Kafka recovers.
 *
 * Each service creates its own @Entity that extends this base, naming
 * the table appropriately for its schema.
 *
 * Example:
 *   @Entity
 *   @Table(name = "repair_outbox", schema = "repair")
 *   public class RepairOutboxEvent extends OutboxEvent {}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Kafka topic this event should be published to.
     * Example: "repair.status_changed", "tow.dropped_off"
     */
    @Column(name = "topic", nullable = false)
    private String topic;

    /**
     * Event type discriminator — used by grouped topic consumers
     * to decide which handler to invoke.
     * Example: "repair.status_changed", "tow.requested"
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Kafka partition key — routes events for the same entity
     * to the same partition, preserving ordering.
     * Example: repairOrderId, towRequestId, listingId
     */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /**
     * Full JSON payload — the exact JSON that will be published to Kafka.
     * Stored as TEXT in Postgres, read as String here.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Outbox event status:
     *   PENDING   → written, waiting for Debezium to pick up
     *   PUBLISHED → Debezium confirmed publication to Kafka
     *   FAILED    → Debezium failed after max retries (alert + manual intervention)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    // ─── Enum ───────────────────────────────────────────────────────────────

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }

    // ─── Factory helper ─────────────────────────────────────────────────────

    /**
     * Convenience constructor — most common usage: create a pending outbox event.
     *
     * Usage:
     *   OutboxEvent event = RepairOutboxEvent.pending(
     *       "repair.status_changed",
     *       "repair.status_changed",
     *       repairOrderId.toString(),
     *       objectMapper.writeValueAsString(payload)
     *   );
     */
    protected OutboxEvent(String topic, String eventType, String aggregateId, String payload) {
        this.topic       = topic;
        this.eventType   = eventType;
        this.aggregateId = aggregateId;
        this.payload     = payload;
        this.status      = OutboxStatus.PENDING;
        this.createdAt   = Instant.now();
    }
}
