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
package org.apache.cassandra.easystress.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.heartbeat
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.serialization.json.Json
import org.apache.cassandra.easystress.server.StressTestManager
import org.apache.cassandra.easystress.server.tools.FieldsTool
import org.apache.cassandra.easystress.server.tools.ListWorkloadsTool
import org.apache.cassandra.easystress.server.tools.WorkloadInfoTool
import org.apache.logging.log4j.kotlin.logger
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * MCP (Model Context Protocol) server command for cassandra-easy-stress.
 *
 * Starts an HTTP server that exposes stress testing capabilities via the Model Context Protocol,
 * enabling AI assistants and other MCP clients to:
 * - List available workloads
 * - Get workload information and schemas
 * - Run stress tests with custom configurations
 * - Monitor test status and real-time metrics
 * - Stop running tests
 *
 * The server provides:
 * - HTTP endpoint on configurable port (default: 9000)
 * - Server-Sent Events (SSE) for MCP communication
 * - JSON-based tool invocation and responses
 * - Thread-safe execution of concurrent tool calls
 *
 * Available MCP tools:
 * - `list_workloads`: List all available stress test workloads
 * - `info`: Get detailed information about a specific workload
 * - `fields`: List available field generators
 * - `run`: Start a stress test with custom configuration
 * - `status`: Get status and real-time metrics of running tests
 * - `stop`: Stop a running stress test
 *
 * Usage:
 * ```
 * cassandra-easy-stress mcp [-p 9000]
 * ```
 *
 * The server runs indefinitely until interrupted. Use Ctrl+C or call stop() to terminate.
 *
 * @property port TCP port to listen on (default: 9000)
 */
@Parameters(commandDescription = "Start MCP server for tool integration")
class Server : IStressCommand {
    @Parameter(names = ["-p", "--port"], description = "Port to listen on")
    var port: Int = 9000

    private val log = logger()
    private var activeThread: Thread? = null
    private var stressTestManager = StressTestManager()

    /**
     * Starts the MCP server and blocks until it is stopped.
     *
     * The server runs in a dedicated non-daemon thread to keep the application alive.
     * This method blocks indefinitely by joining the server thread, keeping the process
     * running until interrupted (e.g., via Ctrl+C or stop() call).
     */
    override fun execute() {
        log.info { "Starting MCP server on port $port" }
        println("Starting MCP server on port $port")

        // Run the server in a separate thread to isolate coroutines
        activeThread =
            thread(isDaemon = false, name = "MCP Server thread") {
                try {
                    log.info { "MCP server thread started, registering ${tools.size} tools" }
                    getServer().start(wait = true)
                } catch (e: InterruptedException) {
                    log.info { "MCP server thread interrupted, shutting down" }
                } catch (e: Exception) {
                    log.error(e) { "MCP server encountered an error" }
                    println("MCP server error: ${e.message}")
                    println(e)
                    throw e
                }
            }
        activeThread?.join()
        log.info { "MCP server stopped" }
    }

    /**
     * Stops the MCP server by interrupting its thread.
     *
     * This is a graceful shutdown that interrupts the server thread,
     * allowing it to clean up resources before terminating.
     */
    fun stop() {
        log.info { "Stopping MCP server" }
        activeThread?.interrupt()
    }

    private val tools =
        listOf(
            ListWorkloadsTool,
            WorkloadInfoTool,
            FieldsTool,
            stressTestManager.getRunTool(),
            stressTestManager.getStopTool(),
            stressTestManager.getStatusTool(),
        )

    /**
     * Creates and configures the embedded Ktor server.
     *
     * Configures:
     * - CIO engine for async I/O
     * - JSON content negotiation with lenient parsing
     * - Basic HTTP route for health checks
     * - MCP protocol support with SSE transport
     * - Registration of all available tools
     *
     * @return Configured but not yet started embedded server instance
     */
    private fun getServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val server =
            Server(
                serverInfo =
                    Implementation(
                        name = "cassandra-easy-stress",
                        version = "1.0.0",
                    ),
                options =
                    ServerOptions(
                        capabilities =
                            ServerCapabilities(
                                logging = null,
                                tools =
                                    ServerCapabilities
                                        .Tools(
                                            listChanged = null,
                                        ),
                            ),
                    ),
            )

        // Register all tools with the server
        server.addTools(
            tools,
        )

        return embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            routing {
                get("/") {
                    call.respondText("MCP Server is running")
                }
            }

            // MCP server configuration with SSE
            mcp {
                heartbeat {
                    period = 1.seconds
                    event = ServerSentEvent("heartbeat")
                }
                // Register all tools
                server
            }
        }
    }
}
