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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FieldsToolTest {
    private fun CallToolResult.textContent(): String = (this.content.first() as TextContent).text!!

    @Test
    fun `should list available field generators`() =
        runTest {
            val request = CallToolRequest(name = "fields", arguments = EmptyJsonObject)
            val result = FieldsTool.handler(request)

            assertThat(result.isError).isFalse()
            assertThat(result.content).isNotEmpty()

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject

            // Should have generators array and total count
            assertThat(jsonResponse.containsKey("generators")).isTrue()
            assertThat(jsonResponse.containsKey("total")).isTrue()

            val generators = jsonResponse["generators"]?.jsonArray
            assertThat(generators).isNotNull
            assertThat(generators!!.size).isGreaterThan(0)

            // Total should match array size
            val total = jsonResponse["total"]?.jsonPrimitive?.content?.toInt()
            assertThat(total).isEqualTo(generators.size)
        }

    @Test
    fun `should include name and description for each generator`() =
        runTest {
            val request = CallToolRequest(name = "fields", arguments = EmptyJsonObject)
            val result = FieldsTool.handler(request)

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject
            val generators = jsonResponse["generators"]?.jsonArray

            assertThat(generators).isNotNull
            assertThat(generators!!.size).isGreaterThan(0)

            // Each generator should have name and description
            generators.forEach { generator ->
                val gen = generator.jsonObject
                assertThat(gen.containsKey("name")).isTrue()
                assertThat(gen.containsKey("description")).isTrue()

                val name = gen["name"]?.jsonPrimitive?.content
                val description = gen["description"]?.jsonPrimitive?.content

                assertThat(name).isNotNull
                assertThat(name).isNotEmpty()
                assertThat(description).isNotNull
                assertThat(description).isNotEmpty()
            }
        }

    @Test
    fun `should return consistent results across multiple calls`() =
        runTest {
            val request = CallToolRequest(name = "fields", arguments = EmptyJsonObject)

            val result1 = FieldsTool.handler(request)
            val result2 = FieldsTool.handler(request)

            val response1 = result1.textContent()
            val response2 = result2.textContent()

            // Results should be identical
            assertThat(response1).isEqualTo(response2)
        }

    @Test
    fun `should include common field generators`() =
        runTest {
            val request = CallToolRequest(name = "fields", arguments = EmptyJsonObject)
            val result = FieldsTool.handler(request)

            val jsonResponse = Json.parseToJsonElement(result.textContent()).jsonObject
            val generators = jsonResponse["generators"]?.jsonArray

            val generatorNames =
                generators?.map {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                }

            // Should have at least some generators
            assertThat(generatorNames).isNotNull
            assertThat(generatorNames).isNotEmpty()

            // All names should be unique
            val uniqueNames = generatorNames?.toSet()
            assertThat(uniqueNames?.size).isEqualTo(generatorNames?.size)
        }
}
