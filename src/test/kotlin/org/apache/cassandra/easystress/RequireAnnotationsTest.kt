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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequireAnnotationsTest {
    private lateinit var allWorkloads: Map<String, Workload>

    @BeforeEach
    fun setup() {
        allWorkloads = Workload.getWorkloads()
    }

    @Test
    fun testGetWorkloadsForTestingFiltersCorrectly() {
        // Explicitly set environment to test expected behavior
        val env = mapOf("CASSANDRA_VERSION" to "5.0")
        val testingWorkloads = Workload.getWorkloadsForTesting(env)

        // Verify DSESearch, MaterializedViews, TxnCounter exist in all workloads
        assertThat(allWorkloads).containsKey("DSESearch")
        assertThat(allWorkloads).containsKey("MaterializedViews")
        assertThat(allWorkloads).containsKey("TxnCounter")

        // MaterializedViews and SAI should be included (version is 5.0)
        // DSESearch and TxnCounter should be filtered out
        assertThat(testingWorkloads).containsKey("MaterializedViews")
        assertThat(testingWorkloads).doesNotContainKey("DSESearch")
        assertThat(testingWorkloads).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testDSEWorkloadsFilteredWithoutEnvVar() {
        // Test with empty environment
        val emptyEnv = emptyMap<String, String>()
        val testingWorkloads = Workload.getWorkloadsForTesting(emptyEnv)

        // DSESearch should be filtered out
        assertThat(testingWorkloads).doesNotContainKey("DSESearch")

        // Other non-annotated workloads should still be present
        assertThat(testingWorkloads).containsKey("BasicTimeSeries")
        assertThat(testingWorkloads).containsKey("KeyValue")
    }

    @Test
    fun testDSEWorkloadIncludedWithEnvVar() {
        // Test with TEST_DSE set
        val envWithDSE = mapOf("TEST_DSE" to "1")
        val testingWorkloads = Workload.getWorkloadsForTesting(envWithDSE)

        // DSESearch should be included
        assertThat(testingWorkloads).containsKey("DSESearch")

        // MaterializedViews should be included (default version is 5.0)
        // TxnCounter should still be filtered out
        assertThat(testingWorkloads).containsKey("MaterializedViews")
        assertThat(testingWorkloads).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testMaterializedViewsFilteredOnOlderVersions() {
        // Test with Cassandra 4.1
        val envWith41 = mapOf("CASSANDRA_VERSION" to "4.1")
        val workloads = Workload.getWorkloadsForTesting(envWith41)

        // MaterializedViews should be filtered out on 4.1
        assertThat(workloads).doesNotContainKey("MaterializedViews")

        // Other non-annotated workloads should still be present
        assertThat(workloads).containsKey("BasicTimeSeries")
        assertThat(workloads).containsKey("KeyValue")
    }

    @Test
    fun testMaterializedViewsIncludedOn50() {
        // Test with Cassandra 5.0
        val envWith50 = mapOf("CASSANDRA_VERSION" to "5.0")
        val workloads = Workload.getWorkloadsForTesting(envWith50)

        // MaterializedViews should be included on 5.0
        assertThat(workloads).containsKey("MaterializedViews")

        // But DSESearch and TxnCounter should still be filtered out
        assertThat(workloads).doesNotContainKey("DSESearch")
        assertThat(workloads).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testAccordWorkloadFilteredWithoutEnvVar() {
        // Test with empty environment
        val emptyEnv = emptyMap<String, String>()
        val workloads = Workload.getWorkloadsForTesting(emptyEnv)

        // TxnCounter should be filtered out
        assertThat(workloads).doesNotContainKey("TxnCounter")

        // Other non-annotated workloads should still be present
        assertThat(workloads).containsKey("BasicTimeSeries")
        assertThat(workloads).containsKey("KeyValue")
    }

    @Test
    fun testAccordWorkloadIncludedWithEnvVar() {
        // Test with TEST_ACCORD set to "1"
        val envWithAccord = mapOf("TEST_ACCORD" to "1")
        val workloads = Workload.getWorkloadsForTesting(envWithAccord)

        // TxnCounter should be included
        assertThat(workloads).containsKey("TxnCounter")

        // MaterializedViews should be included (default version is 5.0)
        // DSESearch should still be filtered out
        assertThat(workloads).doesNotContainKey("DSESearch")
        assertThat(workloads).containsKey("MaterializedViews")
    }

    @Test
    fun testAccordRequiresSpecificValue() {
        // Test that TEST_ACCORD requires value "1", not just any value
        val envWithWrongAccord = mapOf("TEST_ACCORD" to "true")
        val workloads = Workload.getWorkloadsForTesting(envWithWrongAccord)

        // TxnCounter should be filtered out because TEST_ACCORD != "1"
        assertThat(workloads).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testMultipleEnvVarsSet() {
        // Test with all environment variables set
        val envWithAll =
            mapOf(
                "TEST_DSE" to "1",
                "TEST_ACCORD" to "1",
            )
        val workloads = Workload.getWorkloadsForTesting(envWithAll)

        // All annotated workloads should be included
        assertThat(workloads).containsKey("DSESearch")
        assertThat(workloads).containsKey("MaterializedViews") // Included via MinimumVersion
        assertThat(workloads).containsKey("TxnCounter")

        // And all other workloads should still be present
        assertThat(workloads).containsKey("BasicTimeSeries")
        assertThat(workloads).containsKey("KeyValue")
    }

    @Test
    fun testWorkloadAnnotations() {
        // Verify the correct annotations are present on the workloads
        val dseWorkload = allWorkloads["DSESearch"]
        val mvWorkload = allWorkloads["MaterializedViews"]
        val txnWorkload = allWorkloads["TxnCounter"]
        val saiWorkload = allWorkloads["SAI"]

        assertThat(dseWorkload).isNotNull
        assertThat(dseWorkload!!.cls.isAnnotationPresent(RequireDSE::class.java)).isTrue

        assertThat(mvWorkload).isNotNull
        assertThat(mvWorkload!!.cls.isAnnotationPresent(MinimumVersion::class.java)).isTrue
        assertThat(mvWorkload.cls.getAnnotation(MinimumVersion::class.java).version).isEqualTo("5.0")

        assertThat(txnWorkload).isNotNull
        assertThat(txnWorkload!!.cls.isAnnotationPresent(RequireAccord::class.java)).isTrue

        assertThat(saiWorkload).isNotNull
        assertThat(saiWorkload!!.cls.isAnnotationPresent(MinimumVersion::class.java)).isTrue
        assertThat(saiWorkload.cls.getAnnotation(MinimumVersion::class.java).version).isEqualTo("5.0")
    }
}
