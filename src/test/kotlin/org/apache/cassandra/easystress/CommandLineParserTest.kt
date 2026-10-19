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
package org.apache.cassandra.easystress

import com.datastax.oss.driver.api.core.ConsistencyLevel
import org.apache.cassandra.easystress.commands.Run
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class CommandLineParserTest {
    @Test
    fun testBasicParser() {
        val args = arrayOf("run", "BasicTimeSeries")
        val result = CommandLineParser.parse(args)
        assertThat(result.getParsedCommand()).isEqualToIgnoringCase("run")
        assertThat(result.getCommandInstance()).isInstanceOf(Run::class.java)
    }

    @Test
    fun testSerialConsistencyLevelParsing() {
        val args = arrayOf("run", "BasicTimeSeries", "--cl", "QUORUM", "--scl", "SERIAL")
        val result = CommandLineParser.parse(args)

        assertThat(result.getParsedCommand()).isEqualToIgnoringCase("run")
        assertThat(result.getCommandInstance()).isInstanceOf(Run::class.java)

        val runCommand = result.getCommandInstance() as Run
        assertThat(runCommand.consistencyLevel).isEqualTo(ConsistencyLevel.QUORUM)
        assertThat(runCommand.serialConsistencyLevel).isEqualTo(ConsistencyLevel.SERIAL)
    }

    @Test
    fun testDefaultProtocolCompression() {
        val args = arrayOf("run", "BasicTimeSeries")
        val result = CommandLineParser.parse(args)
        val runCommand = result.getCommandInstance() as Run
        assertThat(runCommand.protocolCompression).isEqualTo("lz4")
    }

    @ParameterizedTest
    @CsvSource(
        "--protocol-compression, lz4, lz4",
        "--protocol-compression, none, none",
    )
    fun testProtocolCompressionParsing(
        flag: String,
        value: String,
        expected: String,
    ) {
        val args = arrayOf("run", "BasicTimeSeries", flag, value)
        val result = CommandLineParser.parse(args)
        val runCommand = result.getCommandInstance() as Run
        assertThat(runCommand.protocolCompression).isEqualTo(expected)
    }

    @Test
    fun testInvalidProtocolCompressionFails() {
        val args = arrayOf("run", "BasicTimeSeries", "--protocol-compression", "snappy")
        val result = CommandLineParser.parse(args)
        val runCommand = result.getCommandInstance() as Run
        assertThrows<IllegalArgumentException> {
            runCommand.execute()
        }
    }
}
