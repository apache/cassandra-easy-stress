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
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.apache.cassandra.easystress.commands.IStressCommand
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Generates MCP Tool.Input schemas from IStressCommand classes.
 *
 * Directly reads @Parameter and @DynamicParameter annotations using Kotlin reflection
 * and converts them to MCP Tool.Input format. No intermediate data structures.
 *
 * Usage:
 * ```kotlin
 * val generator = ToolInputSchemaGenerator(Run::class)
 * val toolInput = generator.generateToolInput()
 * ```
 */
class ToolInputSchemaGenerator(
    private val commandClass: KClass<out IStressCommand>,
) {
    fun generateToolInput(): Tool.Input {
        val commandInstance = createInstance()
        val requiredFields = mutableListOf<String>()

        val properties =
            buildJsonObject {
                commandClass.memberProperties.forEach { property ->
                    property.javaField?.let { field ->
                        // Handle @Parameter annotation
                        field.getAnnotation(Parameter::class.java)?.let { param ->
                            put(
                                property.name,
                                buildJsonObject {
                                    put("type", mapTypeToJsonSchema(field.type))
                                    put("description", param.description)

                                    // Get default value from instance
                                    try {
                                        property.getter.call(commandInstance)?.let { defaultValue ->
                                            put("default", defaultValue.toString())
                                        }
                                    } catch (e: Exception) {
                                        // Ignore if we can't get default value
                                    }

                                    // Add parameter names
                                    putJsonArray("parameterNames") {
                                        param.names.forEach { add(JsonPrimitive(it)) }
                                    }
                                },
                            )

                            if (param.required) {
                                requiredFields.add(property.name)
                            }
                        }

                        // Handle @DynamicParameter annotation
                        field.getAnnotation(DynamicParameter::class.java)?.let { param ->
                            put(
                                property.name,
                                buildJsonObject {
                                    put("type", "object")
                                    put("description", param.description)
                                    putJsonArray("parameterNames") {
                                        param.names.forEach { add(JsonPrimitive(it)) }
                                    }
                                },
                            )

                            if (param.required) {
                                requiredFields.add(property.name)
                            }
                        }
                    }
                }
            }

        return Tool.Input(
            properties = properties,
            required = requiredFields,
        )
    }

    private fun createInstance(): IStressCommand {
        return try {
            // Find the primary constructor (not the synthetic serialization constructor)
            // The serialization constructor has a SerializationConstructorMarker parameter
            val constructor =
                commandClass.constructors.first { constructor ->
                    constructor.parameters.none { param ->
                        param.type.toString().contains("SerializationConstructorMarker")
                    }
                }
            constructor.callBy(emptyMap())
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot instantiate ${commandClass.simpleName}: ${e.message}", e)
        }
    }

    private fun mapTypeToJsonSchema(type: Class<*>): String =
        when {
            type == String::class.java -> "string"
            type == Int::class.javaPrimitiveType || type == Integer::class.java -> "integer"
            type == Long::class.javaPrimitiveType || type == java.lang.Long::class.java -> "integer"
            type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java -> "boolean"
            type == Double::class.javaPrimitiveType || type == java.lang.Double::class.java -> "number"
            type == Float::class.javaPrimitiveType || type == java.lang.Float::class.java -> "number"
            Map::class.java.isAssignableFrom(type) -> "object"
            List::class.java.isAssignableFrom(type) || Set::class.java.isAssignableFrom(type) -> "array"
            type.isArray -> "array"
            else -> "string"
        }
}
