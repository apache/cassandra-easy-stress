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

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import org.apache.cassandra.easystress.workloads.IStressProfile
import org.apache.cassandra.easystress.workloads.IStressRunner
import org.apache.cassandra.easystress.workloads.Operation
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PluginTest {
    lateinit var plugin: Plugin

    @BeforeEach
    fun setPlugin() {
        plugin = Plugin.getPlugins()["Demo"]!!
    }

    class Demo : IStressProfile {
        @WorkloadParameter("Number of rows for each")
        var rows: Int = 100

        @WorkloadParameter("First name of person")
        var name: String = "Jon"

        var notWorkloadParameter: String = "oh nooo"

        override fun prepare(session: CqlSession) = Unit

        override fun schema(): List<String> = listOf()

        override fun getRunner(context: StressContext): IStressRunner {
            return object : IStressRunner {
                override fun getNextMutation(partitionKey: PartitionKey): Operation {
                    val b = mockk<BoundStatement>()
                    return Operation.Mutation(b)
                }

                override fun getNextSelect(partitionKey: PartitionKey): Operation {
                    val b = mockk<BoundStatement>()
                    return Operation.SelectStatement(b)
                }

                override fun getNextDelete(partitionKey: PartitionKey): Operation {
                    val b = mockk<BoundStatement>()
                    return Operation.Deletion(b)
                }
            }
        }
    }

    // simple test, but ¯\_(ツ)_/¯
    // we should have at least 2 plugins
    @Test
    fun testGetPlugins() {
        val tmp = Plugin.getPlugins()
        assertThat(tmp.count()).isGreaterThan(1)
    }

    @Test
    fun testApplyDynamicSettings() {
        val fields =
            mapOf(
                "rows" to "10",
                "name" to "Anthony",
            )

        plugin.applyDynamicSettings(fields)

        val instance = plugin.instance as Demo

        assertThat(instance.rows).isEqualTo(10)
        assertThat(instance.name).isEqualTo("Anthony")
    }

    @Test
    fun testGetProperty() {
        val prop = plugin.getProperty("name")
        assertThat(prop.name).isEqualTo("name")
    }

    @Test
    fun testGetNonexistentPropertyThrowsException() {
        assertThatExceptionOfType(NoSuchElementException::class.java).isThrownBy {
            plugin.getProperty("NOT_A_REAL_PROPERTY_OH_NOES")
        }
    }

    @Test
    fun testGetCustomParams() {
        val params = plugin.getCustomParams()
        println(params)
    }
}
