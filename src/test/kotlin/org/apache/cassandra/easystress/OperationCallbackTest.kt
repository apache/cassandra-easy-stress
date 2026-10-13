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

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.cassandra.easystress.workloads.IStressRunner
import org.apache.cassandra.easystress.workloads.Operation
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture

class OperationCallbackTest {
    private val context = mockk<StressContext>(relaxed = true)
    private val runner = mockk<IStressRunner>(relaxed = true)
    private val op = Operation.SelectStatement(mockk<BoundStatement>())

    private fun page(next: CompletableFuture<AsyncResultSet>?): AsyncResultSet =
        mockk {
            every { hasMorePages() } returns (next != null)
            if (next != null) {
                every { fetchNextPage() } returns next
            }
        }

    private fun callback(paginate: Boolean) = OperationCallback(context, runner, op, System.nanoTime(), false, paginate)

    @Test
    fun `fetches each page once and completes after the last page`() {
        val thirdFuture = CompletableFuture<AsyncResultSet>()
        val third = page(null)
        val second = page(thirdFuture)
        val first = page(CompletableFuture.completedFuture(second))

        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            callback(paginate = true).accept(first, null)
        }

        verify(exactly = 1) { first.fetchNextPage() }
        verify(exactly = 1) { second.fetchNextPage() }
        // the last page has not arrived, so the operation must not be complete yet
        verify(exactly = 0) { context.collect(any(), any(), any(), any()) }

        thirdFuture.complete(third)

        verify(exactly = 1) { context.collect(op, match { it is Either.Left }, any(), any()) }
        verify(exactly = 1) { context.timer(op, false) }
    }

    @Test
    fun `records an error when a page fetch fails`() {
        val failure = RuntimeException("page fetch failed")
        val first = page(CompletableFuture.failedFuture(failure))

        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            callback(paginate = true).accept(first, null)
        }

        verify(exactly = 1) { context.metrics.errors.mark() }
        verify(exactly = 1) {
            context.collect(op, match { it is Either.Right && it.value === failure }, any(), any())
        }
        verify(exactly = 0) { context.timer(any(), any()) }
    }

    @Test
    fun `does not fetch more pages when pagination is off`() {
        val first = page(CompletableFuture.completedFuture(page(null)))

        callback(paginate = false).accept(first, null)

        verify(exactly = 0) { first.fetchNextPage() }
        verify(exactly = 1) { context.collect(op, match { it is Either.Left }, any(), any()) }
    }
}
