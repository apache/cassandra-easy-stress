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
package org.apache.cassandra.easystress.commands

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RunKeyspaceCqlTest {
    private val base =
        "CREATE KEYSPACE\n IF NOT EXISTS ks\n" +
            " WITH replication = {'class': 'SimpleStrategy', 'replication_factor':3 }"

    @Test
    fun `keyspace cql omits replication_type by default`() {
        val run = Run().apply { keyspace = "ks" }

        assertThat(run.createKeyspaceCql())
            .isEqualTo(base)
    }

    @Test
    fun `keyspace cql adds tracked replication_type when mutation tracking enabled`() {
        val run =
            Run().apply {
                keyspace = "ks"
                mutationTracking = true
            }

        assertThat(run.createKeyspaceCql())
            .isEqualTo("$base\n AND replication_type = 'tracked'")
    }
}
