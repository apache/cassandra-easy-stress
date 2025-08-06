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

import com.google.common.annotations.VisibleForTesting
import org.apache.logging.log4j.kotlin.logger
import java.util.Timer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max

/**
 * ThroughputTracker provides a customizable short-term throughput monitoring solution
 * using a circular buffer to track operation counts over time.
 *
 * This class captures metrics at regular intervals and calculates throughput based
 * on the difference between oldest and newest measurements in the buffer.
 */
class ThroughputTracker(
    // The number of samples to keep in the buffer
    private val windowSize: Int = 10,
    // Function that returns the current operation count
    private val countSupplier: () -> Long,
    // Sampling interval in milliseconds
    private val sampleIntervalMs: Long = 1000,
    startTime: Long = System.currentTimeMillis(),
) {
    // Circular buffer to store timestamp and count pairs [timestamp, count]
    private val buffer = Array(windowSize) { longArrayOf(0, 0) }

    // Track current position in the circular buffer
    // The first value we're going to set will be the initial timestamp
    // so we set the currentIndex to 1, bc we skip 0
    private val currentIndex = AtomicInteger(1)

    // Timer for periodic sampling
    private var timer = Timer("ThroughputTracker", true)

    // Flag to check if the tracker has been started
    @Volatile
    private var started = false

    @Volatile
    private var stopped = false

    // Flag to check if we have a full window of data
    @Volatile
    private var bufferFilled = false

    init {
        // Validate inputs
        require(windowSize > 1) { "Window size must be greater than 1" }
        require(sampleIntervalMs > 0) { "Sample interval must be positive" }

        initializeBuffer(startTime)
    }

    private fun initializeBuffer(startTime: Long) {
        // Initialize index 0 with the current timestamp and count=0
        buffer[0][0] = startTime
        buffer[0][1] = 0
    }

    companion object {
        var log = logger()
    }

    /**
     * Starts the throughput tracking process.
     * Begins periodic sampling of the supplied count.
     *
     * @return This instance for method chaining
     */
    fun start(): ThroughputTracker {
        if (stopped) {
            throw IllegalStateException()
        }
        if (started) return this

        synchronized(this) {
            if (!started) {
                // Schedule the sampling task
                timer = createTimer()

                started = true
            }
        }

        return this
    }

    private fun createTimer() =
        fixedRateTimer(
            "Throughput Tracker",
            daemon = true,
            initialDelay = 0,
            period = sampleIntervalMs,
        ) {
            sample()
        }

    /**
     * Stops the throughput tracking process.
     */
    fun stop() {
        synchronized(this) {
            timer.cancel()
            timer.purge()
            stopped = true
        }
    }

    /**
     * Resets the tracker to its initial state.
     * This clears all collected data and resets internal state variables.
     *
     * @param startTime The timestamp to use for initialization (default is current time)
     * @return This instance for method chaining
     */
    fun reset(startTime: Long = System.currentTimeMillis()): ThroughputTracker {
        synchronized(this) {
            if (started) {
                timer.cancel()
                timer.purge()
            }

            // Reset state
            started = false
            stopped = false
            bufferFilled = false
            currentIndex.set(1)

            // Reset buffer
            initializeBuffer(startTime)

            // Create a new timer
            timer = createTimer()
        }
        return this
    }

    /**
     *
     */
    fun sample() = sample(System.currentTimeMillis())

    /**
     * Captures a single sample of the current count and timestamp.
     * This method is automatically called by the timer but can also
     * be called manually if needed. Thread-safe.
     * Explicitly accepts a timestamp for testing purposes
     */
    @VisibleForTesting
    fun sample(timestamp: Long) {
        // Get the current timestamp and count atomically
        val count = countSupplier()
        val index = currentIndex.get()

        // Update the buffer with the current readings
        synchronized(this) {
            // Store current timestamp and count
            buffer[index][0] = timestamp
            buffer[index][1] = count

            // Advance to next position in circular buffer
            val nextIndex = (index + 1) % windowSize
            if (nextIndex == 0) {
                bufferFilled = true // We've gone through the entire buffer once
            }

            currentIndex.set(nextIndex)
        }
    }

    /**
     * Calculates and returns the current throughput based on the sliding window
     *
     * @return The calculated throughput or 0.0 if insufficient data is available.
     */
    fun getCurrentThroughput(): Double {
        // We need at least two entries to calculate throughput
        if (currentIndex.get() == 0 && !bufferFilled) {
            return 0.0
        }

        // Find the oldest and newest entries
        val curr = currentIndex.get()

        // The oldest entry is just after the current pointer in the buffer
        // if buffer is filled, otherwise it's the first entry (0)
        val oldestIdx = if (bufferFilled) curr else 0
        val newestIdx = (curr - 1 + windowSize) % windowSize

        val oldestTime = buffer[oldestIdx][0]
        val oldestCount = buffer[oldestIdx][1]
        val newestTime = buffer[newestIdx][0]
        val newestCount = buffer[newestIdx][1]

        // Sanity checks
        if (oldestTime == 0L || newestTime == 0L || oldestTime >= newestTime) {
            return 0.0
        }

        // Calculate throughput between oldest and newest
        val timeDiffMs = max(1, newestTime - oldestTime)
        val countDiff = max(0, newestCount - oldestCount)

        return countDiff * 1000.0 / timeDiffMs // Convert to operations per second
    }

    /**
     * Returns the size of the time window currently being used for measurement.
     * This is the time difference between oldest and newest measurements.
     *
     * @return Time window size in milliseconds, or 0 if insufficient data
     */
    fun getWindowSizeMs(): Long {
        if (!started) return 0

        // We need at least two entries to calculate window size
        if (currentIndex.get() <= 1 && !bufferFilled) {
            return 0
        }

        // Find the oldest and newest entries
        val curr = currentIndex.get()

        // The oldest entry is just after the current pointer in the buffer
        // if buffer is filled, otherwise it's the first entry (0)
        val oldestIdx = if (bufferFilled) curr else 0
        val newestIdx = (curr - 1 + windowSize) % windowSize

        val oldestTime = buffer[oldestIdx][0]
        val newestTime = buffer[newestIdx][0]

        // Sanity checks
        if (oldestTime == 0L || newestTime == 0L || oldestTime >= newestTime) {
            return 0
        }

        return max(1, newestTime - oldestTime)
    }

    @VisibleForTesting
    fun getSample(index: Int) = Sample(buffer[index][0], buffer[index][1])

    data class Sample(val time: Long, val value: Long)
}
