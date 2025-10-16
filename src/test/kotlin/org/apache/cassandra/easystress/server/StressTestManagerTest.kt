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
package org.apache.cassandra.easystress.server

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StressTestManagerTest {
    @Test
    fun `isRunning returns false initially and true when running`() =
        runTest {
            val manager = StressTestManager()

            // Initially not running
            assertThat(manager.isRunning()).isFalse()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            // Should be running
            assertThat(manager.isRunning()).isTrue()

            // Clean up
            manager.stop()
            assertThat(manager.isRunning()).isFalse()
        }

    @Test
    fun `getCurrentThread returns null initially and thread when running`() =
        runTest {
            val manager = StressTestManager()

            // Initially null
            assertThat(manager.getCurrentThread()).isNull()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            // Should have a thread
            assertThat(manager.getCurrentThread()).isNotNull()
            assertThat(manager.getCurrentThread()?.isAlive).isTrue()

            // Clean up
            manager.stop()
        }

    @Test
    fun `getCurrentCommand returns null initially and command when running`() =
        runTest {
            val manager = StressTestManager()

            // Initially null
            assertThat(manager.getCurrentCommand()).isNull()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                    put("threads", JsonPrimitive(2))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            // Should have a command
            val command = manager.getCurrentCommand()
            assertThat(command).isNotNull()
            assertThat(command?.workload).isEqualTo("KeyValue")
            assertThat(command?.host).isEqualTo("127.0.0.1")
            assertThat(command?.duration).isEqualTo(10)
            assertThat(command?.threads).isEqualTo(2)

            // Clean up
            manager.stop()
        }

    @Test
    fun `getStartTime returns null initially and timestamp when running`() =
        runTest {
            val manager = StressTestManager()

            // Initially null
            assertThat(manager.getStartTime()).isNull()

            val beforeStart = System.currentTimeMillis()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            val afterStart = System.currentTimeMillis()

            // Should have a start time within expected range
            val startTime = manager.getStartTime()
            assertThat(startTime).isNotNull()
            assertThat(startTime).isGreaterThanOrEqualTo(beforeStart)
            assertThat(startTime).isLessThanOrEqualTo(afterStart)

            // Clean up
            manager.stop()
        }

    @Test
    fun `getStatus returns idle initially, running when active, and stopped when stopped`() =
        runTest {
            val manager = StressTestManager()

            // Initially idle
            assertThat(manager.getStatus()).isEqualTo("idle")

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            // Should be running
            assertThat(manager.getStatus()).isEqualTo("running")

            // Stop the test
            manager.getStopTool().handler(CallToolRequest(name = "stop", arguments = EmptyJsonObject))

            // Should be stopped
            assertThat(manager.getStatus()).isEqualTo("stopped")
        }

    @Test
    fun `clear resets all state to initial values`() =
        runTest {
            val manager = StressTestManager()

            // Start a test to populate state
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            manager.getRunTool().handler(request)

            // Verify state is populated
            assertThat(manager.isRunning()).isTrue()
            assertThat(manager.getCurrentThread()).isNotNull()
            assertThat(manager.getCurrentCommand()).isNotNull()
            assertThat(manager.getStartTime()).isNotNull()
            assertThat(manager.getStatus()).isEqualTo("running")

            // Clear all state
            manager.clear()

            // Verify everything is reset
            assertThat(manager.isRunning()).isFalse()
            assertThat(manager.getCurrentThread()).isNull()
            assertThat(manager.getCurrentCommand()).isNull()
            assertThat(manager.getStartTime()).isNull()
            assertThat(manager.getStatus()).isEqualTo("idle")
        }

    @Test
    fun `getRunTool handler starts stress test successfully`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()

            // Create valid JSON arguments with required fields
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("iterations", JsonPrimitive(1))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            val result = runTool.handler(request)

            // Should start successfully (background thread will fail when connecting, but that's expected in unit test)
            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()

            // Clean up - stop the manager to release lock
            manager.stop()
        }

    @Test
    fun `getRunTool handler returns error when test already running`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()

            // Create valid JSON arguments with required fields
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("iterations", JsonPrimitive(1))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)

            // First call should succeed
            val result1 = runTool.handler(request)
            assertThat(result1.isError).isFalse()

            // Second call should fail with "already running" error
            val result2 = runTool.handler(request)
            assertThat(result2.isError).isTrue()
            assertThat(result2.content).isNotEmpty()
            assertThat(result2.content.first().toString()).contains("already running")

            // Clean up - stop the manager to release lock
            manager.stop()
        }

    @Test
    fun `getStopTool handler returns error when no test running`() =
        runTest {
            val manager = StressTestManager()
            val stopTool = manager.getStopTool()

            val request = CallToolRequest(name = "stop", arguments = EmptyJsonObject)
            val result = stopTool.handler(request)

            assertThat(result.isError).isTrue()
            assertThat(result.content).isNotEmpty()
            assertThat(result.content.first().toString()).contains("No stress test is currently running")
        }

    @Test
    fun `getStopTool handler stops running test`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()
            val stopTool = manager.getStopTool()

            // Start a test with minimal iterations
            val runArgs =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("iterations", JsonPrimitive(1))
                }
            val runRequest = CallToolRequest(name = "run", arguments = runArgs)
            runTool.handler(runRequest)

            // Verify test is running
            assertThat(manager.isRunning()).isTrue()

            // Stop the test
            val stopRequest = CallToolRequest(name = "stop", arguments = EmptyJsonObject)
            val result = stopTool.handler(stopRequest)

            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()
            assertThat(result.content.first().toString()).contains("stopped successfully")

            // Verify test is no longer running
            assertThat(manager.isRunning()).isFalse()
            assertThat(manager.getStatus()).isEqualTo("stopped")
        }

    @Test
    fun `getStatusTool handler returns status when no test running`() =
        runTest {
            val manager = StressTestManager()
            val statusTool = manager.getStatusTool()

            val request = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(request)

            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()
        }

    @Test
    fun `getStatusTool handler shows stopped message after stop`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()
            val stopTool = manager.getStopTool()
            val statusTool = manager.getStatusTool()

            // Start a test
            val runArgs =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("iterations", JsonPrimitive(1))
                }
            val runRequest = CallToolRequest(name = "run", arguments = runArgs)
            runTool.handler(runRequest)

            // Stop the test
            val stopRequest = CallToolRequest(name = "stop", arguments = EmptyJsonObject)
            stopTool.handler(stopRequest)

            // Check status shows "stopped" message
            val statusRequest = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(statusRequest)

            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()
            val statusText = result.content.first().toString()
            assertThat(statusText).contains("\"status\":\"stopped\"")
            assertThat(statusText).contains("Last stress test was stopped")
        }

    @Test
    fun `end-to-end test lifecycle - start, stop, start again, complete`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()
            val stopTool = manager.getStopTool()
            val statusTool = manager.getStatusTool()

            // 1. FIRST TEST: Start a test with duration=10 seconds
            val firstTestArgs =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                    put("threads", JsonPrimitive(1))
                }
            val firstRunRequest = CallToolRequest(name = "run", arguments = firstTestArgs)
            val startResult1 = runTool.handler(firstRunRequest)

            // Verify first test started successfully
            assertThat(startResult1.isError).isFalse()
            assertThat(startResult1.content.first().toString()).contains("started successfully")
            assertThat(manager.isRunning()).isTrue()
            assertThat(manager.getStatus()).isEqualTo("running")
            assertThat(manager.getCurrentCommand()).isNotNull()
            assertThat(manager.getCurrentThread()).isNotNull()
            assertThat(manager.getCurrentThread()?.isAlive).isTrue()

            // Give the test a moment to actually start running (use real time, not virtual time)
            withContext(Dispatchers.IO) {
                Thread.sleep(50)
            }

            // 2. STOP FIRST TEST: Stop the running test (or verify it stopped on its own)
            val stopRequest = CallToolRequest(name = "stop", arguments = EmptyJsonObject)
            val stopResult = stopTool.handler(stopRequest)

            // Verify stop was called and manager is no longer running
            // Note: The test might have already failed and stopped on its own, which is fine
            assertThat(manager.isRunning()).isFalse()
            assertThat(manager.getStatus()).isEqualTo("stopped")

            // 3. SECOND TEST: Start another test with shorter duration
            val secondTestArgs =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(2))
                    put("threads", JsonPrimitive(1))
                }
            val secondRunRequest = CallToolRequest(name = "run", arguments = secondTestArgs)
            val startResult2 = runTool.handler(secondRunRequest)

            // Verify second test started successfully
            assertThat(startResult2.isError).isFalse()
            assertThat(startResult2.content.first().toString()).contains("started successfully")
            assertThat(manager.isRunning()).isTrue()
            assertThat(manager.getStatus()).isEqualTo("running")

            val secondTestStartTime = manager.getStartTime()
            assertThat(secondTestStartTime).isNotNull()

            // 4. VERIFY STATUS DURING RUN: Check status while second test is running
            val statusRequest = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val statusResult = statusTool.handler(statusRequest)

            assertThat(statusResult.isError).isFalse()
            val statusText = statusResult.content.first().toString()
            assertThat(statusText).contains("\"status\":\"running\"")
            assertThat(statusText).contains("\"is_running\":true")
            assertThat(statusText).contains("currently running")

            // 5. LET SECOND TEST COMPLETE: Wait for test to finish naturally
            // The test will fail to connect but that's expected - it should still complete the lifecycle
            withContext(Dispatchers.IO) {
                Thread.sleep(3000)
            }

            // Verify test completed or stopped (either is acceptable since connection will fail)
            assertThat(manager.isRunning()).isFalse()
            val finalStatus = manager.getStatus()
            // Accept any of: completed, stopped, or failed (connection failures are expected in unit tests)
            assertThat(finalStatus).matches("^(completed|stopped|failed:.*)$")

            // 6. VERIFY POST-COMPLETION STATUS
            val finalStatusRequest = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val finalStatusResult = statusTool.handler(finalStatusRequest)

            assertThat(finalStatusResult.isError).isFalse()
            val finalStatusText = finalStatusResult.content.first().toString()
            assertThat(finalStatusText).contains("\"is_running\":false")
            assertThat(finalStatusText.contains("Last stress test") || finalStatusText.contains("Status:")).isTrue()

            // Verify we can see the last run time
            assertThat(manager.getStartTime()).isEqualTo(secondTestStartTime)
        }

    @Test
    fun `getStatusTool returns detailed running status with configuration`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()
            val statusTool = manager.getStatusTool()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(10))
                    put("threads", JsonPrimitive(3))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            runTool.handler(request)

            // Check status immediately while still running (before connection fails)
            val statusRequest = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(statusRequest)

            assertThat(result.isError).isFalse()
            val statusText = result.content.first().toString()

            // Verify running state
            assertThat(statusText).contains("\"status\":\"running\"")
            assertThat(statusText).contains("\"is_running\":true")
            assertThat(statusText).contains("currently running")

            // Verify test configuration is included
            assertThat(statusText).contains("\"current_test\"")
            assertThat(statusText).contains("\"workload\":\"KeyValue\"")
            assertThat(statusText).contains("\"host\":\"127.0.0.1\"")
            assertThat(statusText).contains("\"duration\":10")
            assertThat(statusText).contains("\"threads\":3")

            // Verify thread info is included
            assertThat(statusText).contains("\"thread_name\"")
            assertThat(statusText).contains("\"thread_alive\"")

            // Verify timing info is included
            assertThat(statusText).contains("\"start_time\"")
            assertThat(statusText).contains("\"elapsed_seconds\"")

            // Clean up
            manager.stop()
        }

    @Test
    fun `getStatusTool returns idle status when no test has run`() =
        runTest {
            val manager = StressTestManager()
            val statusTool = manager.getStatusTool()

            val request = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(request)

            assertThat(result.isError).isFalse()
            val statusText = result.content.first().toString()

            assertThat(statusText).contains("\"status\":\"idle\"")
            assertThat(statusText).contains("\"is_running\":false")
            assertThat(statusText).contains("No stress test is currently running")
        }

    @Test
    fun `getStatusTool returns completed status after successful test`() =
        runTest {
            val manager = StressTestManager()

            // Manually set status to completed (simulating a successful test)
            manager.setStatus("completed")

            val statusTool = manager.getStatusTool()
            val request = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(request)

            assertThat(result.isError).isFalse()
            val statusText = result.content.first().toString()

            assertThat(statusText).contains("\"status\":\"completed\"")
            assertThat(statusText).contains("\"is_running\":false")
            assertThat(statusText).contains("Last stress test completed successfully")
        }

    @Test
    fun `getStatusTool returns failed status with error message`() =
        runTest {
            val manager = StressTestManager()

            // Manually set status to failed (simulating a failed test)
            manager.setStatus("failed: Connection refused")

            val statusTool = manager.getStatusTool()
            val request = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(request)

            assertThat(result.isError).isFalse()
            val statusText = result.content.first().toString()

            assertThat(statusText).contains("\"status\":\"failed: Connection refused\"")
            assertThat(statusText).contains("\"is_running\":false")
            assertThat(statusText).contains("Last stress test failed")
            assertThat(statusText).contains("Connection refused")
        }

    @Test
    fun `getStatusTool includes last run time after test completes`() =
        runTest {
            val manager = StressTestManager()
            val runTool = manager.getRunTool()
            val statusTool = manager.getStatusTool()

            // Start a test
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("KeyValue"))
                    put("host", JsonPrimitive("127.0.0.1"))
                    put("duration", JsonPrimitive(1))
                }
            val request = CallToolRequest(name = "run", arguments = arguments)
            runTool.handler(request)

            val startTime = manager.getStartTime()

            // Stop the test
            manager.stop()
            manager.setStatus("stopped")

            // Get status after stopping
            val statusRequest = CallToolRequest(name = "status", arguments = EmptyJsonObject)
            val result = statusTool.handler(statusRequest)

            assertThat(result.isError).isFalse()
            val statusText = result.content.first().toString()

            assertThat(statusText).contains("\"is_running\":false")
            assertThat(statusText).contains("\"last_run_time\":$startTime")
        }
}
