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
package org.apache.cassandra.easystress.generators

import org.apache.logging.log4j.kotlin.logger
import org.reflections.Reflections

data class FunctionDescription(
    val name: String,
    val description: String,
)

class AnnotationMissingException(val name: Class<out FieldGenerator>) : Exception()

/**
 * Finds all the available functions and tracks them by name
 */
class FunctionLoader : Iterable<FunctionDescription> {
    override fun iterator(): Iterator<FunctionDescription> {
        return object : Iterator<FunctionDescription> {
            val iter = map.iterator()

            override fun hasNext() = iter.hasNext()

            override fun next(): FunctionDescription {
                val tmp = iter.next()
                val annotation = tmp.value.getAnnotation(Function::class.java) ?: throw AnnotationMissingException(tmp.value)

                return FunctionDescription(annotation.name, annotation.description)
            }
        }
    }

    class FunctionNotFound(val name: String) : Exception()

    val map: MutableMap<String, Class<out FieldGenerator>> = mutableMapOf()

    init {
        val r = Reflections("org.apache.cassandra.easystress")
        log.debug { "Getting FieldGenerator subtypes" }
        val modules = r.getSubTypesOf(FieldGenerator::class.java)

        modules.forEach {
            log.debug { "Getting annotations for $it" }

            val annotation = it.getAnnotation(Function::class.java) ?: throw AnnotationMissingException(it)

            val name = annotation.name
            map[name] = it
        }
    }

    companion object {
        val log = logger()
    }

    /**
     * Returns an instance of the requested class
     */
    fun getInstance(name: String): FieldGenerator {
        val tmp = map[name] ?: throw FunctionNotFound(name)
        val result = tmp.getDeclaredConstructor().newInstance()
        return result
    }

    /**
     *
     */
    fun getInstance(func: ParsedFieldFunction): FieldGenerator {
        val tmp = getInstance(func.name)
        tmp.setParameters(func.args)
        return tmp
    }
}
