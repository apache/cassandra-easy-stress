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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.cassandra.easystress.Workload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WorkloadInfoToolTest {
    private fun CallToolResult.textContent(): String = (this.content.first() as TextContent).text!!

    @Test
    fun `should require workload parameter`() {
        val inputSchema = WorkloadInfoTool.tool.inputSchema
        assertThat(inputSchema.required).contains("workload")
    }

    @Test
    fun `should return error for non-existent workload`() =
        runTest {
            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive("nonexistent_workload_xyz"))
                }
            val request = CallToolRequest(name = "info", arguments = arguments)
            val result = WorkloadInfoTool.handler(request)

            assertThat(result.isError).isTrue()
            assertThat(result.textContent()).contains("not found")
        }

    @Test
    fun `should return workload info for valid workload`() =
        runTest {
            // Get the first available workload
            val workloads = Workload.getWorkloads()
            val firstWorkload = workloads.keys.first()

            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive(firstWorkload))
                }
            val request = CallToolRequest(name = "info", arguments = arguments)
            val result = WorkloadInfoTool.handler(request)

            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject

            // Should have all expected fields
            assertThat(jsonResponse.containsKey("name")).isTrue()
            assertThat(jsonResponse.containsKey("class")).isTrue()
            assertThat(jsonResponse.containsKey("schema")).isTrue()
            assertThat(jsonResponse.containsKey("defaultReadRate")).isTrue()
            assertThat(jsonResponse.containsKey("parameters")).isTrue()

            // Verify name matches
            assertThat(jsonResponse["name"]?.jsonPrimitive?.content).isEqualTo(firstWorkload)

            // Schema should be an array
            val schema = jsonResponse["schema"]?.jsonArray
            assertThat(schema).isNotNull

            // Parameters should be an array
            val parameters = jsonResponse["parameters"]?.jsonArray
            assertThat(parameters).isNotNull
        }

    @Test
    fun `should return schema statements`() =
        runTest {
            val workloads = Workload.getWorkloads()
            val firstWorkload = workloads.keys.first()

            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive(firstWorkload))
                }
            val request = CallToolRequest(name = "info", arguments = arguments)
            val result = WorkloadInfoTool.handler(request)

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject
            val schema = jsonResponse["schema"]?.jsonArray

            // Should have at least one schema statement
            assertThat(schema).isNotNull
            assertThat(schema!!.size).isGreaterThan(0)

            // Each schema entry should have a statement field
            val firstStatement = schema.first().jsonObject
            assertThat(firstStatement.containsKey("statement")).isTrue()
        }

    @Test
    fun `should return valid defaultReadRate`() =
        runTest {
            val workloads = Workload.getWorkloads()
            val firstWorkload = workloads.keys.first()

            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive(firstWorkload))
                }
            val request = CallToolRequest(name = "info", arguments = arguments)
            val result = WorkloadInfoTool.handler(request)

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject

            val readRate = jsonResponse["defaultReadRate"]?.jsonPrimitive?.content?.toDouble()
            assertThat(readRate).isNotNull
            assertThat(readRate).isBetween(0.0, 1.0)
        }

    @Test
    fun `should handle parameters field correctly`() =
        runTest {
            val workloads = Workload.getWorkloads()
            val firstWorkload = workloads.keys.first()

            val arguments =
                buildJsonObject {
                    put("workload", JsonPrimitive(firstWorkload))
                }
            val request = CallToolRequest(name = "info", arguments = arguments)
            val result = WorkloadInfoTool.handler(request)

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject
            val parameters = jsonResponse["parameters"]?.jsonArray

            assertThat(parameters).isNotNull

            // If there are parameters, each should have name, description, and type
            if (parameters!!.isNotEmpty()) {
                val firstParam = parameters.first().jsonObject
                assertThat(firstParam.containsKey("name")).isTrue()
                assertThat(firstParam.containsKey("description")).isTrue()
                assertThat(firstParam.containsKey("type")).isTrue()
            }
        }
}
