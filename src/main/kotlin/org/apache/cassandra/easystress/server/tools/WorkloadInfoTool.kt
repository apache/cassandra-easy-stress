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
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.apache.cassandra.easystress.Workload
import org.apache.logging.log4j.kotlin.logger

/**
 * MCP tool to get detailed information about a specific workload.
 *
 * Provides comprehensive metadata about a workload including its schema, default settings,
 * and configurable parameters. This information helps users understand what a workload does
 * and how to configure it.
 *
 * Input parameters:
 * - `workload` (required): Name of the workload to query (e.g., "keyvalue", "timeseries")
 *
 * Returns a JSON object containing:
 * - `name`: Workload name
 * - `class`: Fully qualified class name
 * - `schema`: Array of CQL schema statements (CREATE TABLE, etc.)
 * - `defaultReadRate`: Default read/write ratio (0.0 = all writes, 1.0 = all reads)
 * - `parameters`: Array of custom parameters specific to this workload, each with:
 *   - `name`: Parameter name
 *   - `description`: Parameter description
 *   - `type`: Parameter type
 *
 * Example response:
 * ```json
 * {
 *   "name": "keyvalue",
 *   "class": "org.apache.cassandra.easystress.profiles.KeyValue",
 *   "schema": [{"statement": "CREATE TABLE ..."}],
 *   "defaultReadRate": 0.5,
 *   "parameters": [
 *     {"name": "keysize", "description": "Size of keys in bytes", "type": "integer"}
 *   ]
 * }
 * ```
 *
 * Thread Safety: This tool is thread-safe as it only reads from the immutable workload registry.
 */
private val logger = logger("WorkloadInfoTool")

@Serializable
private data class WorkloadInfoRequest(
    val workload: String,
)

val WorkloadInfoTool =
    RegisteredTool(
        tool =
            Tool(
                name = "info",
                title = null,
                description = "Get details of a specific workload",
                inputSchema =
                    Tool.Input(
                        properties =
                            buildJsonObject {
                                putJsonObject("workload") {
                                    put("type", "string")
                                    put("description", "Name of the workload to get information about")
                                }
                            },
                        required = listOf("workload"),
                    ),
                outputSchema = null,
                annotations = null,
            ),
        handler = ::handleWorkloadInfo,
    )

private suspend fun handleWorkloadInfo(request: CallToolRequest): CallToolResult {
    return try {
        val workloadName = Json.decodeFromJsonElement<WorkloadInfoRequest>(request.arguments).workload

        logger.info { "Info request for $workloadName" }

        data class WorkloadData(
            val name: String,
            val className: String,
            val schema: List<String>,
            val defaultReadRate: Double,
            val parameters: List<org.apache.cassandra.easystress.Workload.WorkloadParameterType>,
        )

        val workloadData =
            withContext(Dispatchers.IO) {
                val workloads = Workload.getWorkloads()
                val workload =
                    workloads[workloadName]
                        ?: throw IllegalArgumentException("Workload '$workloadName' not found")

                WorkloadData(
                    name = workload.name,
                    className = workload.cls.name,
                    schema = workload.instance.schema().toList(),
                    defaultReadRate = workload.instance.getDefaultReadRate(),
                    parameters = workload.getCustomParams().toList(),
                )
            }

        val jsonResponse =
            buildJsonObject {
                put("name", workloadData.name)
                put("class", workloadData.className)
                putJsonArray("schema") {
                    for (statement in workloadData.schema) {
                        addJsonObject { put("statement", statement) }
                    }
                }
                put("defaultReadRate", workloadData.defaultReadRate)
                putJsonArray("parameters") {
                    for (param in workloadData.parameters) {
                        addJsonObject {
                            put("name", param.name)
                            put("description", param.description)
                            put("type", param.type)
                        }
                    }
                }
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
                        text = "Error getting workload info: ${e.message}",
                    ),
                ),
            isError = true,
        )
    }
}
