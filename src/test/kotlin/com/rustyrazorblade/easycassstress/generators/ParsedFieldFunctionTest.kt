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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ParsedFieldFunctionTest {
    @Test
    fun noArgumentParseTest() {
        val parsed = ParsedFieldFunction("book()")
        assertThat(parsed.name).isEqualTo("book")
        assertThat(parsed.args).hasSize(0)
    }

    @Test
    fun singleArgumentTest() {
        val parsed = ParsedFieldFunction("random(10)")
        assertThat(parsed.name).isEqualTo("random")
        assertThat(parsed.args).hasSize(1)
        assertThat(parsed.args[0]).isEqualTo("10")
    }

    @Test
    fun emptyFieldArgumentParseTest() {
        val args = ParsedFieldFunction.parseArguments("")
        assertThat(args).hasSize(0)
    }
}
