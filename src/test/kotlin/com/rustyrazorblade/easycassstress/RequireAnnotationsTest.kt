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
package com.rustyrazorblade.easycassstress

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequireAnnotationsTest {
    private lateinit var allPlugins: Map<String, Plugin>

    @BeforeEach
    fun setup() {
        allPlugins = Plugin.getPlugins()
    }

    @Test
    fun testGetPluginsForTestingFiltersCorrectly() {
        val testingPlugins = Plugin.getPluginsForTesting()

        assertThat(System.getenv("TEST_DSE")).isNull()
        assertThat(System.getenv("TEST_MVS")).isNull()

        // Verify DSESearch, MaterializedViews, TxnCounter exist in all plugins
        assertThat(allPlugins).containsKey("DSESearch")
        assertThat(allPlugins).containsKey("MaterializedViews")
        assertThat(allPlugins).containsKey("TxnCounter")

        assertThat(testingPlugins).doesNotContainKey("MaterializedViews")
        assertThat(testingPlugins).doesNotContainKey("DSESearch")
        assertThat(testingPlugins).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testDSEPluginFilteredWithoutEnvVar() {
        // Test with empty environment
        val emptyEnv = emptyMap<String, String>()
        val testingPlugins = Plugin.getPluginsForTesting(emptyEnv)

        // DSESearch should be filtered out
        assertThat(testingPlugins).doesNotContainKey("DSESearch")

        // Other non-annotated plugins should still be present
        assertThat(testingPlugins).containsKey("BasicTimeSeries")
        assertThat(testingPlugins).containsKey("KeyValue")
    }

    @Test
    fun testDSEPluginIncludedWithEnvVar() {
        // Test with TEST_DSE set
        val envWithDSE = mapOf("TEST_DSE" to "1")
        val testingPlugins = Plugin.getPluginsForTesting(envWithDSE)

        // DSESearch should be included
        assertThat(testingPlugins).containsKey("DSESearch")

        // But MaterializedViews and TxnCounter should still be filtered out
        assertThat(testingPlugins).doesNotContainKey("MaterializedViews")
        assertThat(testingPlugins).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testMVsPluginFilteredWithoutEnvVar() {
        // Test with empty environment
        val emptyEnv = emptyMap<String, String>()
        val testingPlugins = Plugin.getPluginsForTesting(emptyEnv)

        // MaterializedViews should be filtered out
        assertThat(testingPlugins).doesNotContainKey("MaterializedViews")

        // Other non-annotated plugins should still be present
        assertThat(testingPlugins).containsKey("BasicTimeSeries")
        assertThat(testingPlugins).containsKey("KeyValue")
    }

    @Test
    fun testMVsPluginIncludedWithEnvVar() {
        // Test with TEST_MVS set
        val envWithMVs = mapOf("TEST_MVS" to "1")
        val testingPlugins = Plugin.getPluginsForTesting(envWithMVs)

        // MaterializedViews should be included
        assertThat(testingPlugins).containsKey("MaterializedViews")

        // But DSESearch and TxnCounter should still be filtered out
        assertThat(testingPlugins).doesNotContainKey("DSESearch")
        assertThat(testingPlugins).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testAccordPluginFilteredWithoutEnvVar() {
        // Test with empty environment
        val emptyEnv = emptyMap<String, String>()
        val testingPlugins = Plugin.getPluginsForTesting(emptyEnv)

        // TxnCounter should be filtered out
        assertThat(testingPlugins).doesNotContainKey("TxnCounter")

        // Other non-annotated plugins should still be present
        assertThat(testingPlugins).containsKey("BasicTimeSeries")
        assertThat(testingPlugins).containsKey("KeyValue")
    }

    @Test
    fun testAccordPluginIncludedWithEnvVar() {
        // Test with TEST_ACCORD set to "1"
        val envWithAccord = mapOf("TEST_ACCORD" to "1")
        val testingPlugins = Plugin.getPluginsForTesting(envWithAccord)

        // TxnCounter should be included
        assertThat(testingPlugins).containsKey("TxnCounter")

        // But DSESearch and MaterializedViews should still be filtered out
        assertThat(testingPlugins).doesNotContainKey("DSESearch")
        assertThat(testingPlugins).doesNotContainKey("MaterializedViews")
    }

    @Test
    fun testAccordRequiresSpecificValue() {
        // Test that TEST_ACCORD requires value "1", not just any value
        val envWithWrongAccord = mapOf("TEST_ACCORD" to "true")
        val testingPlugins = Plugin.getPluginsForTesting(envWithWrongAccord)

        // TxnCounter should be filtered out because TEST_ACCORD != "1"
        assertThat(testingPlugins).doesNotContainKey("TxnCounter")
    }

    @Test
    fun testMultipleEnvVarsSet() {
        // Test with all environment variables set
        val envWithAll =
            mapOf(
                "TEST_DSE" to "1",
                "TEST_MVS" to "1",
                "TEST_ACCORD" to "1",
            )
        val testingPlugins = Plugin.getPluginsForTesting(envWithAll)

        // All annotated plugins should be included
        assertThat(testingPlugins).containsKey("DSESearch")
        assertThat(testingPlugins).containsKey("MaterializedViews")
        assertThat(testingPlugins).containsKey("TxnCounter")

        // And all other plugins should still be present
        assertThat(testingPlugins).containsKey("BasicTimeSeries")
        assertThat(testingPlugins).containsKey("KeyValue")
    }

    @Test
    fun testPluginAnnotations() {
        // Verify the correct annotations are present on the plugins
        val dsePlugin = allPlugins["DSESearch"]
        val mvPlugin = allPlugins["MaterializedViews"]
        val txnPlugin = allPlugins["TxnCounter"]

        assertThat(dsePlugin).isNotNull
        assertThat(dsePlugin!!.cls.isAnnotationPresent(RequireDSE::class.java)).isTrue

        assertThat(mvPlugin).isNotNull
        assertThat(mvPlugin!!.cls.isAnnotationPresent(RequireMVs::class.java)).isTrue

        assertThat(txnPlugin).isNotNull
        assertThat(txnPlugin!!.cls.isAnnotationPresent(RequireAccord::class.java)).isTrue
    }
}
