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
package com.rustyrazorblade.easycassstress.generators

import org.apache.logging.log4j.kotlin.logger

data class Field(val table: String, val field: String)

class FieldFactory(private val table: String) {
    fun getField(field: String): Field = Field(table, field)
}

/**
 * Registry for data generators
 * When the original schema is created, the registry will be set up with default generators for each field
 * A generator option can be overridden on the command line as a dynamic flag with field.*
 * The idea here is we should be able to customize the data we increment without custom coding
 * for instance, I could use random(1, 100) to be an int field of 1-100 or a text field of 1-100 characters.
 * book(10, 100) is a random selection of 10-100 words from a bunch of open source licensed books
 * Ideally we have enough here to simulate a lot (call it 90%) of common workloads
 *
 */
class Registry(
    val defaults: MutableMap<Field, FieldGenerator> = mutableMapOf(),
    val overrides: MutableMap<Field, FieldGenerator> = mutableMapOf(),
) {
    companion object {
        val log = logger()

        val functionLoader = FunctionLoader()

        fun create(defaults: MutableMap<Field, FieldGenerator>): Registry {
            return Registry(defaults)
        }

        fun create(): Registry {
            return Registry()
        }
    }

    fun getFunctions(): Iterator<FunctionDescription> = functionLoader.iterator()

    /**
     * Sets the default generator for a table / field pair
     * Not all generators work on all fields
     */
    fun setDefault(
        table: String,
        field: String,
        generator: FieldGenerator,
    ): Registry {
        val f = Field(table, field)
        return this.setDefault(f, generator)
    }

    fun setDefault(
        field: Field,
        generator: FieldGenerator,
    ): Registry {
        defaults[field] = generator
        return this
    }

    /**
     * Overrides the default generator for a table / field pair
     * Not all generators work on all fields
     *
     * @param table table that's affected
     * @param field field that's affected
     */
    fun setOverride(
        table: String,
        field: String,
        generator: FieldGenerator,
    ): Registry {
        val f = Field(table, field)

        return this.setOverride(f, generator)
    }

    fun setOverride(
        field: Field,
        generator: FieldGenerator,
    ): Registry {
        overrides[field] = generator
        return this
    }

    fun setOverride(
        table: String,
        field: String,
        parsedField: ParsedFieldFunction,
    ): Registry {
        val instance = functionLoader.getInstance(parsedField)
        return setOverride(table, field, instance)
    }

    fun getGenerator(
        table: String,
        field: String,
    ): FieldGenerator {
        log.info("Getting generator for $table.$field")
        val tmp = Field(table, field)
        if (tmp in overrides) {
            return overrides[tmp]!!
        }
        return defaults[tmp]!!
    }
}

class FieldNotFoundException(message: String) : Throwable(message)
