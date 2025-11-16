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
import org.apache.cassandra.easystress.generators.Registry

/**
 * MCP tool to list all available field generators.
 *
 * Field generators are functions that produce random or patterned data for populating
 * database fields during stress testing. They include generators for common data types
 * like strings, integers, UUIDs, timestamps, and more complex patterns.
 *
 * This tool requires no input parameters and returns a JSON array containing:
 * - `generators`: Array of generator objects, each containing:
 *   - `name`: Generator function name (e.g., "uuid", "timestamp", "random_string")
 *   - `description`: Description of what the generator produces
 * - `total`: Total number of available generators
 *
 * Example response:
 * ```json
 * {
 *   "generators": [
 *     {"name": "uuid", "description": "Generates random UUIDs"},
 *     {"name": "timestamp", "description": "Generates current timestamp"},
 *     {"name": "random_string", "description": "Generates random strings"}
 *   ],
 *   "total": 3
 * }
 * ```
 *
 * Thread Safety: This tool is thread-safe as it only reads from the immutable generator registry.
 */
val FieldsTool =
    RegisteredTool(
        tool =
            Tool(
                name = "fields",
                title = null,
                description = "List all available field generators",
                inputSchema = Tool.Input(properties = EmptyJsonObject),
                outputSchema = null,
                annotations = null,
            ),
        handler = ::handleFields,
    )

private suspend fun handleFields(request: CallToolRequest): CallToolResult =
    try {
        val functions =
            withContext(Dispatchers.IO) {
                val registry = Registry.create()
                registry.getFunctions().asSequence().toList()
            }
        val jsonResponse =
            buildJsonObject {
                putJsonArray("generators") {
                    for (func in functions) {
                        addJsonObject {
                            put("name", func.name)
                            put("description", func.description)
                        }
                    }
                }
                put("total", functions.size)
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
                        text = "Error listing generators: ${e.message}",
                    ),
                ),
            isError = true,
        )
    }
