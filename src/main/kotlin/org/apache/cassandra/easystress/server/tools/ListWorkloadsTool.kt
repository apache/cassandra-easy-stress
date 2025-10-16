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
package org.apache.cassandra.easystress.server.tools

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.apache.cassandra.easystress.Workload

/**
 * MCP tool to list all available workloads.
 *
 * Returns a list of all registered workload types that can be used with the stress testing framework.
 * Each workload represents a different type of database operation pattern (e.g., key-value, time series).
 *
 * This tool requires no input parameters and returns a JSON array containing:
 * - `workloads`: Array of workload objects, each containing:
 *   - `name`: The workload name (used as identifier in other tools)
 *   - `class`: The fully qualified class name of the workload implementation
 * - `total`: Total number of available workloads
 *
 * Example response:
 * ```json
 * {
 *   "workloads": [
 *     {"name": "keyvalue", "class": "org.apache.cassandra.easystress.profiles.KeyValue"},
 *     {"name": "timeseries", "class": "org.apache.cassandra.easystress.profiles.TimeSeries"}
 *   ],
 *   "total": 2
 * }
 * ```
 *
 * Thread Safety: This tool is thread-safe as it only reads from the immutable workload registry.
 */
val ListWorkloadsTool =
    RegisteredTool(
        tool =
            Tool(
                name = "list_workloads",
                title = null,
                description = "List all available workloads",
                inputSchema = Tool.Input(properties = EmptyJsonObject),
                outputSchema = null,
                annotations = null,
            ),
        handler = ::handleListWorkloads,
    )

private suspend fun handleListWorkloads(request: CallToolRequest): CallToolResult {
    return try {
        val workloads =
            withContext(Dispatchers.IO) {
                Workload.getWorkloads()
            }
        val jsonResponse =
            buildJsonObject {
                putJsonArray("workloads") {
                    for ((name, _) in workloads) {
                        addJsonObject {
                            put("name", name)
                        }
                    }
                }
                put("total", workloads.size)
            }

        CallToolResult(
            content =
                listOf(
                    TextContent(
                        text = jsonResponse.toString(),
                    ),
                ),
        )
    } catch (e: Exception) {
        CallToolResult(
            content =
                listOf(
                    TextContent(
                        text = "Error listing workloads: ${e.message}",
                    ),
                ),
            isError = true,
        )
    }
}
