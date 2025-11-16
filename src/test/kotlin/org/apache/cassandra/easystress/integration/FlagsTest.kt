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
package org.apache.cassandra.easystress.integration

import org.apache.cassandra.easystress.commands.Run
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Simple tests for various flags that don't required dedicated testing
 */
class FlagsTest : CassandraTestBase() {
    var keyvalue = Run("placeholder")

    @BeforeEach
    fun resetRunners() {
        cleanupKeyspace()
        keyvalue =
            Run("placeholder").apply {
                workload = "KeyValue"
                iterations = 100
                host = ip
                cqlPort = port
                dc = localDc
                useOptimizer = false
                prometheusPort = 0
                replication = "{'class': 'SimpleStrategy', 'replication_factor':1 }"
            }
    }

    @Test
    fun csvTest() {
        keyvalue
            .apply {
                csvFile = "test.csv"
            }.execute()
    }
}
