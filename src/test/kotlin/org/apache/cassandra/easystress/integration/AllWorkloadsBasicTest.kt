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

import org.apache.cassandra.easystress.Workload
import org.apache.cassandra.easystress.commands.Run
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Retention(AnnotationRetention.RUNTIME)
@MethodSource("getWorkloads")
annotation class AllWorkloads

/**
 * This test grabs every workload and ensures it can run against localhost
 * Next step is to start up a docker container with Cassandra
 * Baby steps.
 */
class AllWorkloadsBasicTest : CassandraTestBase() {
    lateinit var run: Run

    /**
     * Annotate a test with @AllWorkloads
     */
    companion object {
        @JvmStatic
        fun getWorkloads() =
            Workload.getWorkloadsForTesting().values.filter {
                it.name != "Demo"
            }
    }

    @BeforeEach
    fun setupTest() {
        cleanupKeyspace()
        run = Run("placeholder")
    }

    /**
     * This test is configured to run against a local instance
     * using the datacenter name from our base class.
     */
    @AllWorkloads
    @ParameterizedTest(name = "run test {0}")
    fun runEachTest(workload: Workload) {
        run
            .apply {
                host = ip
                cqlPort = port
                this.workload = workload.name
                duration = 10
                rate = 50L
                partitionCount = 1000
                prometheusPort = 0
                threads = 2
                useOptimizer = false
                replication = "{'class': 'SimpleStrategy', 'replication_factor':1 }"
                dc = localDc // Use the datacenter from the base class
            }.execute()
    }
}
