package com.twowheeler.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Shared Resilience4j defaults — applied to all inter-service REST calls.
 *
 * Pattern decisions from Phase 1:
 *   Circuit breaker  → prevents cascading failures (e.g. workshop-service down shouldn't kill gateway)
 *   Retry            → handles transient network blips (max 3 attempts with backoff)
 *   Bulkhead         → limits concurrent calls per downstream service
 *
<<<<<<< HEAD
=======
 *   What it actually does at runtime
 *
 * No new threads are spun up.
 * At most 25 requests to a given downstream call can be "in flight" concurrently on your existing request threads.
 * If a 26th request comes in while 25 are active, it waits up to 100ms for a slot to free up.
 * If still no slot after 100ms, that call fails immediately with a BulkheadFullException — protecting the
 * rest of your app's threads from getting exhausted/blocked waiting on a slow or misbehaving
 * downstream service.
 *
>>>>>>> 1817c6444880877d2ba3abd16033b59077091182
 * Services can override these defaults by defining their own beans
 * or via application.yml resilience4j.* properties.
 *
 * Usage in a service (RestTemplate or WebClient call):
 *
 *   @CircuitBreaker(name = "workshopService", fallbackMethod = "fallback")
 *   @Retry(name = "workshopService")
 *   @Bulkhead(name = "workshopService")
 *   public RepairOrderDto getRepairOrder(String id) { ... }
 *
 *   public RepairOrderDto fallback(String id, Exception ex) {
 *       log.warn("Circuit breaker open for workshopService, returning fallback");
 *       throw ApiException.internalError("Workshop service unavailable");
 *   }
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit breaker defaults:
     *   - Opens after 50% failure rate in a 10-call sliding window
     *   - Stays open for 30 seconds before testing again (half-open)
     *   - 5 calls allowed in half-open state to test recovery
     */
    @Bean
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // Do not count ApiException as a failure (it's a business exception, not a network error)
            .ignoreExceptions(com.twowheeler.common.exception.ApiException.class)
            .build();
    }

    /**
     * Retry defaults:
     *   - Max 3 attempts (1 initial + 2 retries)
     *   - Exponential backoff: 500ms, 1000ms, 2000ms
     *   - Only retry on network/IO errors, not business exceptions
     */
    @Bean
    public RetryConfig defaultRetryConfig() {
        return RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            .ignoreExceptions(com.twowheeler.common.exception.ApiException.class)
            .build();
    }

    /**
     * Bulkhead defaults:
     *   - Max 25 concurrent calls to any single downstream service
     *   - Wait max 100ms for a slot before rejecting (fail fast)
     *   Prevents one slow downstream from exhausting all threads.
     */
    @Bean
    public BulkheadConfig defaultBulkheadConfig() {
        return BulkheadConfig.custom()
            .maxConcurrentCalls(25)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();
    }

    /**
     * Time limiter defaults:
     *   - Max 3 seconds for any downstream call
     *   - After 3s → TimeoutException → circuit breaker counts as failure
     */
    @Bean
    public TimeLimiterConfig defaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(3))
            .cancelRunningFuture(true)
            .build();
    }
}
