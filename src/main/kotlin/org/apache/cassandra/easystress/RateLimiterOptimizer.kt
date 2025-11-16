/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.easystress

import com.google.common.util.concurrent.RateLimiter
import org.apache.logging.log4j.kotlin.logger
import java.lang.Math.cbrt
import java.lang.Math.min
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Dynamically adjusts the rate limiter based on observed latency metrics.
 *
 * This class implements an adaptive algorithm that:
 * 1. Gradually increases load during an initial "step phase"
 * 2. Monitors latencies to ensure they stay within target thresholds
 * 3. Dynamically adjusts throughput based on current performance metrics
 */
class RateLimiterOptimizer(
    val rateLimiter: RateLimiter,
    val metrics: Metrics,
    val maxReadLatency: Long?,
    val maxWriteLatency: Long?,
    var isStepPhase: Boolean = true,
) {
    companion object {
        val log = logger()
    }

    val durationFactor = 1.0 / TimeUnit.MILLISECONDS.toNanos(1)

    val initial: Double = rateLimiter.rate
    val stepValue = initial / 10.0

    init {
        println("Stepping rate limiter by $stepValue to $initial")
    }

    /**
     * Updates the rate limiter based on current metrics and returns the new rate limit.
     *
     * This method handles two operational phases:
     * 1. Step phase: gradually ramps up load to the initial target
     * 2. Optimization phase: dynamically adjusts based on latency measurements
     *
     * @return The updated rate limit value
     */
    fun execute(): Double {
        // Handle fresh start or when reset() was called
        if (isStepPhase) {
            return handleStepPhase()
        }

        // Skip optimization if we don't have enough metrics
        if (getTotalOperations() < 100) {
            log.info("Not enough operations performed yet to optimize")
            return rateLimiter.rate
        }

        // Get current latency metrics and optimize if available
        return getCurrentAndMaxLatency()
            .map { (currentLatency, maxLatency) -> optimizeRateLimit(currentLatency, maxLatency) }
            .orElse(rateLimiter.rate)
    }

    /**
     * Handles the initial step phase where we gradually increase load
     */
    private fun handleStepPhase(): Double {
        log.info("Stepping rate limiter by $stepValue")
        val newValue = min(rateLimiter.rate + stepValue, initial)

        // Check if we've reached the initial target
        if (newValue >= initial) {
            log.info("Moving to optimization phase")
            isStepPhase = false
        }

        rateLimiter.rate = newValue
        log.info("New rate limiter value: ${rateLimiter.rate}")
        return rateLimiter.rate
    }

    /**
     * Optimizes the rate limit based on current latency measurements
     */
    private fun optimizeRateLimit(
        currentLatency: Double,
        maxLatency: Long,
    ): Double {
        val currentRate = rateLimiter.rate
        val newLimit = getNextValue(currentRate, currentLatency, maxLatency)

        // No change needed
        if (newLimit == currentRate) {
            log.info("Optimizer has nothing to do")
            return currentRate
        }

        // Get current throughput for decision making
        val currentThroughput = getCurrentTotalThroughput()
        val utilizationRatio = currentThroughput / currentRate

        // Handle rate increases - only if we're utilizing enough of current capacity
        if (newLimit > currentRate) {
            if (utilizationRatio < 0.9) {
                log.info(
                    "Not increasing rate limiter, current utilization too low (${utilizationRatio.format(2)})" +
                        " - throughput: $currentThroughput, limit: $currentRate",
                )
                return currentRate
            }
        } else if (newLimit < currentRate && utilizationRatio < 0.9) {
            // Handle rate decreases - avoid oscillation
            log.info(
                "Not decreasing rate limiter despite high latency - throughput ($currentThroughput) " +
                    "is well below current limit ($currentRate)",
            )
            return currentRate
        }

        // Apply the new rate limit
        log.info("Updating rate limiter from $currentRate to $newLimit")
        rateLimiter.rate = newLimit
        return newLimit
    }

    /**
     * Format a double to specified decimal places
     */
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

    /**
     * Added to prevent the rate limiter from acting when queries aren't running, generally during populate phase
     */
    fun getTotalOperations(): Long = metrics.mutations.count + metrics.selects.count

    fun getCurrentTotalThroughput(): Double =
        metrics.getSelectThroughput() +
            metrics.getMutationThroughput() +
            metrics.getDeletionThroughput() +
            metrics.getPopulateThroughput()

    /**
     * Determines which latency metric is most critical and returns it with its target.
     *
     * This method intelligently selects between read, write, and populate latencies
     * based on the current test phase and latency ratio to target.
     *
     * @return An Optional containing the current latency and its target maximum value
     */
    fun getCurrentAndMaxLatency(): Optional<Pair<Double, Long>> {
        // Case: No latency targets specified
        if (maxWriteLatency == null && maxReadLatency == null) {
            log.debug("No latency targets specified")
            return Optional.empty()
        }

        // Case: In populate phase
        if (isInPopulatePhase()) {
            return getPopulatePhaseLatency()
        }

        // Case: Only write latency target specified
        if (maxReadLatency == null && maxWriteLatency != null) {
            val writeLatency = getWriteLatency()
            log.info("Only write latency target specified, current: $writeLatency ms, max: $maxWriteLatency ms")
            return Optional.of(Pair(writeLatency, maxWriteLatency))
        }

        // Case: Only read latency target specified
        if (maxWriteLatency == null && maxReadLatency != null) {
            val readLatency = getReadLatency()
            log.info("Only read latency target specified, current: $readLatency ms, max: $maxReadLatency ms")
            return Optional.of(Pair(readLatency, maxReadLatency))
        }

        // Case: Both read and write latency targets specified - determine which is more critical
        return determineCriticalLatency()
    }

    /**
     * Checks if we're currently in the populate phase
     */
    private fun isInPopulatePhase(): Boolean =
        metrics.mutations.count == 0L &&
            metrics.selects.count == 0L &&
            metrics.deletions.count == 0L &&
            metrics.populate.count > 0L

    /**
     * Gets latency information during populate phase
     */
    private fun getPopulatePhaseLatency(): Optional<Pair<Double, Long>> {
        if (maxWriteLatency != null) {
            val populateLatency = getPopulateLatency()
            log.info("In populate phase, using populate latency: $populateLatency ms, max: $maxWriteLatency ms")
            return Optional.of(Pair(populateLatency, maxWriteLatency))
        }
        return Optional.empty()
    }

    /**
     * Determines which latency (read or write) is most critical relative to its target
     */
    private fun determineCriticalLatency(): Optional<Pair<Double, Long>> {
        val readLatency = getReadLatency()
        val writeLatency = getWriteLatency()

        // Calculate how close each latency is to its limit as a ratio
        val readLatencyRatio = readLatency / maxReadLatency!!.toDouble()
        val writeLatencyRatio = writeLatency / maxWriteLatency!!.toDouble()

        // Return the latency that's closest to or exceeding its target
        return if (readLatencyRatio > writeLatencyRatio) {
            log.debug("Read latency more critical: ${readLatencyRatio.format(2)} of max vs write ${writeLatencyRatio.format(2)}")
            Optional.of(Pair(readLatency, maxReadLatency))
        } else {
            log.debug("Write latency more critical: ${writeLatencyRatio.format(2)} of max vs read ${readLatencyRatio.format(2)}")
            Optional.of(Pair(writeLatency, maxWriteLatency))
        }
    }

    /**
     * Calculates the optimal rate limit value based on current performance metrics.
     *
     * This implements an adaptive algorithm with three cases:
     * 1. If latency exceeds target: reduce throughput quickly (by 10%)
     * 2. If within 90% of target latency: maintain current throughput to avoid oscillation
     * 3. If well below target: increase throughput proportionally to available headroom
     *
     * @param currentRate The current rate limit value
     * @param currentLatency The current observed latency (in ms)
     * @param maxLatency The maximum acceptable latency (in ms)
     * @return The calculated new rate limit
     */
    fun getNextValue(
        currentRate: Double,
        currentLatency: Double,
        maxLatency: Long,
    ): Double {
        val maxLatencyDouble = maxLatency.toDouble()
        val latencyRatio = currentLatency / maxLatencyDouble

        // Case 1: Latency is too high - reduce throughput
        if (latencyRatio > 1.0) {
            val reductionFactor = 0.90
            log.info(
                "Latency exceeded target: ${currentLatency.format(2)}ms > ${maxLatency}ms, " +
                    "reducing throughput by ${(1 - reductionFactor) * 100}%",
            )
            return currentRate * reductionFactor
        } else if (latencyRatio > 0.90) {
            // Case 2: Within 90% of target - maintain current throughput
            log.info("Latency approaching target (${(latencyRatio * 100).format(1)}% of max), maintaining throughput")
            return currentRate
        } else {
            // Case 3: Well below target - increase proportionally to available headroom
            // Calculate increase factor - more aggressive for low latencies, gentler as we approach target
            // Uses cube root to provide a non-linear response curve
            // Small latency requirements (< 10ms) will use smaller adjustments due to sensitivity
            val maxIncreaseFactor = (1.0 + sqrt(maxLatencyDouble) / 100.0).coerceAtMost(1.05)
            val headroom = maxLatencyDouble - currentLatency
            val adjustmentFactor = (1.0 + cbrt(headroom) / maxLatencyDouble).coerceAtMost(maxIncreaseFactor)

            val newLimit = currentRate * adjustmentFactor
            log.info(
                "Latency (${currentLatency.format(2)}ms) well below target (${maxLatency}ms): " +
                    "increasing throughput by ${((adjustmentFactor - 1) * 100).format(1)}% " +
                    "from $currentRate to ${newLimit.format(1)}",
            )
            return newLimit
        }
    }

    /**
     * Gets the current 99th percentile read latency in milliseconds
     */
    fun getReadLatency() = metrics.selects.snapshot.get99thPercentile() * durationFactor

    /**
     * Gets the current 99th percentile write latency in milliseconds
     */
    fun getWriteLatency() = metrics.mutations.snapshot.get99thPercentile() * durationFactor

    /**
     * Gets the current 99th percentile populate operation latency in milliseconds
     */
    fun getPopulateLatency() = metrics.populate.snapshot.get99thPercentile() * durationFactor

    /**
     * Resets the optimizer to its initial state, starting the step phase again.
     * This is typically called after a populate phase completes or when workload parameters change.
     */
    fun reset() {
        log.info("Resetting rate limiter optimizer to step phase, starting rate: $stepValue")
        isStepPhase = true
        rateLimiter.rate = stepValue
    }
}
