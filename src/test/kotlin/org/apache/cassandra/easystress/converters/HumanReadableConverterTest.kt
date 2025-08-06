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
package org.apache.cassandra.easystress.converters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HumanReadableConverterTest {
    lateinit var converter: HumanReadableConverter

    @BeforeEach
    fun setUp() {
        converter = HumanReadableConverter()
    }

    @Test
    fun convert() {
        assertThat(converter.convert("5k")).isEqualTo(5000L)
        assertThat(converter.convert("500")).isEqualTo(500L)
        assertThat(converter.convert("5m")).isEqualTo(5000000L)
        assertThat(converter.convert("5b")).isEqualTo(5000000000L)
    }
}
