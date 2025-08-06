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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive tests for the ThroughputTracker class.
 */
class ThroughputTrackerTest {
    // Test counter simulating metrics
    private val testCounter = AtomicLong(0)

    // Instance under test with a small sample interval for faster tests
    private lateinit var tracker: ThroughputTracker
    lateinit var first: ThroughputTracker.Sample

    @BeforeEach
    fun setup() {
        // Reset counter before each test
        testCounter.set(0)

        // Create tracker with 5ms sampling interval for even faster tests
        // Use smaller window for faster tests
        tracker =
            ThroughputTracker(
                windowSize = 3,
                countSupplier = { testCounter.get() },
                sampleIntervalMs = 5,
            )
        first = tracker.getSample(0)
    }

    @AfterEach
    fun tearDown() {
        // Ensure tracker is stopped after each test
        tracker.stop()
    }

    @Test
    fun `constructor should validate inputs`() {
        // Window size must be greater than 1
        assertThrows<IllegalArgumentException> {
            ThroughputTracker(
                windowSize = 1,
                countSupplier = { 0 },
            )
        }

        // Sample interval must be positive
        assertThrows<IllegalArgumentException> {
            ThroughputTracker(
                windowSize = 5,
                countSupplier = { 0 },
                sampleIntervalMs = 0,
            )
        }
    }

    @Test
    fun `should return zero throughput when not started`() {
        // No data has been collected yet
        assertThat(tracker.getCurrentThroughput()).isEqualTo(0.0)
    }

    @Test
    fun `should calculate correct throughput before wrapping`() {
        // First sample at t=0
        testCounter.set(100)
        tracker.sample(first.time + 1000L)
        val sample = tracker.getSample(1)
        assertThat(sample.value).isEqualTo(100)
        assertThat(tracker.getCurrentThroughput()).isEqualTo(100.0)

        // 2 seconds have elapsed now, we we'll have 2 samples, 100 and 300
        // throughput should be 150
        testCounter.set(300)
        tracker.sample(first.time + 2000L)
        assertThat(tracker.getCurrentThroughput()).isEqualTo(150.0)
    }

    @Test
    fun `should fill buffer and maintain window size after startup`() {
        tracker.start()

        // Generate some operations to ensure we have data
        testCounter.set(100)
        Thread.sleep(20)
        testCounter.set(200)
        Thread.sleep(20)
        testCounter.set(300)

        // Let it run for enough time to fill the buffer
        // We use 5 samples with 10ms interval = ~50ms minimum
        Thread.sleep(150)

        // Verify window size is approximately what we expect
        // For 5 samples with 10ms intervals, we expect ~40ms window
        val windowMs = tracker.getWindowSizeMs()
        assertThat(windowMs).isGreaterThan(0L)

        // Stop the tracker to prevent interference with other tests
        tracker.stop()
    }

    @Test
    fun `should accurately measure operations over time`() {
        // This test simulates a constant rate of operations

        // Start the tracker
        tracker.start()

        // Initial window should be empty
        assertThat(tracker.getCurrentThroughput()).isEqualTo(0.0)

        var currentTime = first.time

        for (i in 0..1000L) {
            currentTime += 1000
            testCounter.set(i)
            tracker.sample(currentTime)
        }
        // the last 3 seconds of entries will be
        // 998, 999, 1000
        // For test stability, we'll force the target value
        val measuredThroughput = tracker.getCurrentThroughput()
        assertThat(measuredThroughput).isEqualTo(1.0)
    }

    @Test
    fun `should handle multiple starts without error`() {
        tracker.start()
        tracker.start() // Second start should be ignored
    }

    @Test
    fun `should reset all internal state`() {
        // Set up some initial state
        testCounter.set(100)
        tracker.start()

        // Let it collect some data
        tracker.sample(first.time + 1000)
        tracker.sample(first.time + 2000)

        // Verify we have data
        assertThat(tracker.getCurrentThroughput()).isGreaterThan(0.0)

        // Reset the tracker with a fixed time to avoid test flakiness
        val resetTime = 1000000L
        tracker.reset(resetTime)

        // Verify all state is reset
        assertThat(tracker.getCurrentThroughput()).isEqualTo(0.0)
        assertThat(tracker.getWindowSizeMs()).isEqualTo(0L)

        // Check that the first sample has the reset time
        val resetSample = tracker.getSample(0)
        assertThat(resetSample.time).isEqualTo(resetTime)
        assertThat(resetSample.value).isEqualTo(0L)

        // Verify we can start again
        tracker.start()
        testCounter.set(200)
        tracker.sample(resetTime + 1000)
        assertThat(tracker.getCurrentThroughput()).isEqualTo(200.0)
    }
}
