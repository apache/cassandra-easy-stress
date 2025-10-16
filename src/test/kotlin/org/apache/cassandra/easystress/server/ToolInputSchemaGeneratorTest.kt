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

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.Parameter
import com.datastax.oss.driver.api.core.ConsistencyLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.apache.cassandra.easystress.commands.IStressCommand
import org.apache.cassandra.easystress.commands.Run
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolInputSchemaGeneratorTest {
    // Test command class for basic testing
    class TestCommand : IStressCommand {
        @Parameter(names = ["--host"], description = "Host to connect to")
        var host = "localhost"

        @Parameter(names = ["-p", "--port"], description = "Port number", required = true)
        var port = 8080

        @Parameter(names = ["--verbose", "-v"], description = "Enable verbose output")
        var verbose = false

        @DynamicParameter(names = ["--field."], description = "Dynamic fields")
        var fields = mutableMapOf<String, String>()

        override fun execute() {
            // Test command - no-op
        }
    }

    @Test
    fun `should work with Run command`() {
        val generator = ToolInputSchemaGenerator(Run::class)
        val toolInput = generator.generateToolInput()

        val propertiesJson = Json.parseToJsonElement(toolInput.properties.toString()).jsonObject

        // Check for some known fields from Run command
        assertThat(propertiesJson.containsKey("host")).isTrue()
        assertThat(propertiesJson.containsKey("workload")).isTrue()
        assertThat(propertiesJson.containsKey("duration")).isTrue()

        // Workload should be required
        assertThat(toolInput.required).contains("workload")

        // Check dynamic fields
        assertThat(propertiesJson.containsKey("fields")).isTrue()

        // Test that Run command can be deserialized with all fields correctly set,
        // including duration which should be a numeric value in seconds
        val runJson =
            buildJsonObject {
                put("workload", JsonPrimitive("KeyValue"))
                put("host", JsonPrimitive("127.0.0.1"))
                put("duration", JsonPrimitive(1200))
                put("rate", JsonPrimitive(5000))
            }
        val runCommand = Json.decodeFromJsonElement(Run.serializer(), runJson)

        assertThat(runCommand.workload).isEqualTo("KeyValue")
        assertThat(runCommand.host).isEqualTo("127.0.0.1")
        assertThat(runCommand.duration).describedAs("Duration should be set in seconds").isEqualTo(1200)
        assertThat(runCommand.rate).isEqualTo(5000)
    }

    // Edge case test command with nullable types, collections, and enums
    @Serializable
    class EdgeCaseCommand : IStressCommand {
        @Parameter(names = ["--nullable-string"], description = "A nullable string parameter")
        var nullableString: String? = null

        @Parameter(names = ["--string-list"], description = "A list of strings")
        var stringList: List<String> = emptyList()

        @Parameter(names = ["--string-set"], description = "A set of strings")
        var stringSet: Set<String> = emptySet()

        @Parameter(names = ["--string-array"], description = "An array of strings")
        var stringArray: Array<String> = emptyArray()

        @Parameter(names = ["--int-list"], description = "A list of integers")
        var intList: List<Int> = emptyList()

        @Parameter(names = ["--map"], description = "A map of string to string")
        var map: Map<String, String> = emptyMap()

        override fun execute() {
            // Test command - no-op
        }
    }

    @Test
    fun `should handle ConsistencyLevel enum serialization`() {
        val runJson =
            buildJsonObject {
                put("workload", JsonPrimitive("KeyValue"))
                put("consistencyLevel", JsonPrimitive("QUORUM"))
                put("serialConsistencyLevel", JsonPrimitive("LOCAL_SERIAL"))
            }

        val runCommand = Json.decodeFromJsonElement(Run.serializer(), runJson)

        assertThat(runCommand.consistencyLevel).isEqualTo(ConsistencyLevel.QUORUM)
        assertThat(runCommand.serialConsistencyLevel).isEqualTo(ConsistencyLevel.LOCAL_SERIAL)
    }

    @Test
    fun `should deserialize collections from JSON`() {
        // Create a JSON with array values
        val edgeCaseJson =
            buildJsonObject {
                put(
                    "stringList",
                    buildJsonArray {
                        add(JsonPrimitive("one"))
                        add(JsonPrimitive("two"))
                        add(JsonPrimitive("three"))
                    },
                )
                put(
                    "intList",
                    buildJsonArray {
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(2))
                        add(JsonPrimitive(3))
                    },
                )
            }

        // Note: EdgeCaseCommand is @Serializable so it can be deserialized from JSON
        val command = Json.decodeFromJsonElement(EdgeCaseCommand.serializer(), edgeCaseJson)

        assertThat(command.stringList).containsExactly("one", "two", "three")
        assertThat(command.intList).containsExactly(1, 2, 3)
    }

    @Test
    fun `should deserialize dynamic fields parameter from JSON`() {
        // Test that the fields map (dynamic parameter) can be properly deserialized
        val runJson =
            buildJsonObject {
                put("workload", JsonPrimitive("KeyValue"))
                put("host", JsonPrimitive("127.0.0.1"))
                put(
                    "fields",
                    buildJsonObject {
                        put("keyvalue.id", JsonPrimitive("uuid()"))
                        put("keyvalue.value", JsonPrimitive("book(100,200)"))
                        put("users.name", JsonPrimitive("firstname()"))
                    },
                )
            }

        val runCommand = Json.decodeFromJsonElement(Run.serializer(), runJson)

        assertThat(runCommand.workload).isEqualTo("KeyValue")
        assertThat(runCommand.host).isEqualTo("127.0.0.1")
        assertThat(runCommand.fields).isNotNull
        assertThat(runCommand.fields).hasSize(3)
        assertThat(runCommand.fields).containsEntry("keyvalue.id", "uuid()")
        assertThat(runCommand.fields).containsEntry("keyvalue.value", "book(100,200)")
        assertThat(runCommand.fields).containsEntry("users.name", "firstname()")
    }

    @Test
    fun `should deserialize empty fields map from JSON`() {
        val runJson =
            buildJsonObject {
                put("workload", JsonPrimitive("KeyValue"))
                put("host", JsonPrimitive("127.0.0.1"))
                put("fields", buildJsonObject {})
            }

        val runCommand = Json.decodeFromJsonElement(Run.serializer(), runJson)

        assertThat(runCommand.fields).isNotNull
        assertThat(runCommand.fields).isEmpty()
    }

    @Test
    fun `should handle missing fields parameter in JSON`() {
        val runJson =
            buildJsonObject {
                put("workload", JsonPrimitive("KeyValue"))
                put("host", JsonPrimitive("127.0.0.1"))
            }

        val runCommand = Json.decodeFromJsonElement(Run.serializer(), runJson)

        assertThat(runCommand.fields).isNotNull
        assertThat(runCommand.fields).isEmpty()
    }
}
