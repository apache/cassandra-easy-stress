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
package org.apache.cassandra.easystress.workloads

import org.apache.cassandra.easystress.Workload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class VectorSearchTest {
    @Test
    fun `schema should reflect configured dimensions and similarity`() {
        val workload =
            VectorSearch().apply {
                dimensions = 768
                similarityFunction = "EUCLIDEAN"
            }

        val schema = workload.schema()
        assertThat(schema).hasSize(2)

        // Check table schema
        assertThat(schema[0]).contains("vector<float, 768>")

        // Check index schema
        assertThat(schema[1]).contains("USING 'sai'")
        assertThat(schema[1]).contains("'similarity_function': 'EUCLIDEAN'")
    }

    @Test
    fun `test dynamic workload parameters`() {
        val workload = Workload.getWorkloads()["VectorSearch"]!!

        workload.applyDynamicSettings(mapOf("dimensions" to "1024", "limit" to "50"))

        val instance = workload.instance as VectorSearch
        assertThat(instance.dimensions).isEqualTo(1024)
        assertThat(instance.limit).isEqualTo(50)
    }

    @Test
    fun `convertToFloatArray should handle float arrays`() {
        val floatData: Array<FloatArray> =
            arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
            )

        val result = VectorSearch.convertToFloatArray(floatData)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(1.0f, 2.0f, 3.0f)
        assertThat(result[1]).containsExactly(4.0f, 5.0f, 6.0f)
    }

    @Test
    fun `convertToFloatArray should handle double arrays`() {
        val doubleData: Array<DoubleArray> =
            arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
            )

        val result = VectorSearch.convertToFloatArray(doubleData)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(1.0f, 2.0f, 3.0f)
        assertThat(result[1]).containsExactly(4.0f, 5.0f, 6.0f)
    }

    @Test
    fun `convertToFloatArray should throw on unsupported types`() {
        val stringData = "not an array"

        assertThatThrownBy { VectorSearch.convertToFloatArray(stringData) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unsupported data format")
    }

    @Test
    fun `convertToIntArray should handle int arrays`() {
        val intData: Array<IntArray> =
            arrayOf(
                intArrayOf(1, 2, 3),
                intArrayOf(4, 5, 6),
            )

        val result = VectorSearch.convertToIntArray(intData)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(1, 2, 3)
        assertThat(result[1]).containsExactly(4, 5, 6)
    }

    @Test
    fun `convertToIntArray should handle long arrays`() {
        val longData: Array<LongArray> =
            arrayOf(
                longArrayOf(1L, 2L, 3L),
                longArrayOf(4L, 5L, 6L),
            )

        val result = VectorSearch.convertToIntArray(longData)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(1, 2, 3)
        assertThat(result[1]).containsExactly(4, 5, 6)
    }
}
