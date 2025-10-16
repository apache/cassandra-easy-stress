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
import kotlin.test.assertFailsWith

internal class HumanReadableTimeConverterTest {
    lateinit var converter: HumanReadableTimeConverter

    @BeforeEach
    fun setUp() {
        converter = HumanReadableTimeConverter()
    }

    @Test
    fun convert() {
        assertThat(converter.convert("15m")).isEqualTo(900) // 15 * 60 = 900 seconds
        assertThat(converter.convert("1h")).isEqualTo(3600) // 60 * 60 = 3600 seconds
        assertThat(converter.convert("3h")).isEqualTo(10800) // 3 * 60 * 60 = 10800 seconds
        assertThat(converter.convert("1d 1h")).isEqualTo(90000) // (24 * 60 * 60) + (60 * 60) = 90000 seconds
        assertThat(converter.convert("1h 5m")).isEqualTo(3900) // (60 * 60) + (5 * 60) = 3900 seconds
        assertThat(converter.convert("3m 120s")).isEqualTo(300) // (3 * 60) + 120 = 300 seconds
        assertThat(converter.convert("10m 1d 59s 2h")).isEqualTo(94259) // (10 * 60) + (24 * 60 * 60) + 59 + (2 * 60 * 60) = 94259 seconds
        assertThat(converter.convert("1d2h3m")).isEqualTo(93780) // (24 * 60 * 60) + (2 * 60 * 60) + (3 * 60) = 93780 seconds
    }

    @Test
    fun convertAndFail() {
        assertFailsWith<java.lang.IllegalArgumentException> { val cl = converter.convert("BLAh") }
    }
}
