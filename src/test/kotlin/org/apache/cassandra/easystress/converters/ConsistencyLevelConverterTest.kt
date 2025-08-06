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

import com.datastax.oss.driver.api.core.ConsistencyLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ConsistencyLevelConverterTest {
    lateinit var converter: ConsistencyLevelConverter

    @BeforeEach
    fun setUp() {
        converter = ConsistencyLevelConverter()
    }

    @Test
    fun convert() {
        assertThat(converter.convert("LOCAL_ONE")).isEqualTo(ConsistencyLevel.LOCAL_ONE)
        assertThat(converter.convert("LOCAL_QUORUM")).isEqualTo(ConsistencyLevel.LOCAL_QUORUM)
    }

    @Test
    fun convertAndFail() {
        assertFailsWith<java.lang.IllegalArgumentException> { val cl = converter.convert("LOCAL") }
    }
}
