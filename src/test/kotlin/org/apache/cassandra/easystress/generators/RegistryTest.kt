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

import org.apache.cassandra.easystress.generators.functions.Random
import org.apache.cassandra.easystress.generators.functions.USCities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RegistryTest {
    lateinit var registry: Registry

    @BeforeEach
    fun setUp() {
        registry =
            Registry
                .create()
                .setDefault("test", "city", USCities())
                .setDefault(
                    "test",
                    "age",
                    Random().apply {
                        min = 10
                        max = 100
                    },
                )
    }

    @Test
    fun getOverriddenTypeTest() {
        assertThat(registry.getGenerator("test", "city")).isInstanceOf(USCities::class.java)
        registry.setOverride(
            "test",
            "city",
            Random().apply {
                min = 10
                max = 100
            },
        )

        assertThat(registry.getGenerator("test", "city")).isInstanceOf(Random::class.java)
    }
}
