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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture

// a pagination hang must fail the test rather than block the build
@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PaginationTest {
    private fun page(next: CompletableFuture<AsyncResultSet>?): AsyncResultSet =
        mockk {
            every { hasMorePages() } returns (next != null)
            if (next != null) {
                every { fetchNextPage() } returns next
            }
        }

    @Test
    fun `fetches each page once and completes with the last page`() {
        val thirdFuture = CompletableFuture<AsyncResultSet>()
        val third = page(null)
        val second = page(thirdFuture)
        val first = page(CompletableFuture.completedFuture(second))

        val all = first.fetchAllPages().toCompletableFuture()

        verify(exactly = 1) { first.fetchNextPage() }
        verify(exactly = 1) { second.fetchNextPage() }
        // the last page has not arrived yet
        assertThat(all).isNotDone

        thirdFuture.complete(third)

        assertThat(all).isCompletedWithValue(third)
    }

    @Test
    fun `completes a single page without fetching`() {
        val only = page(null)

        assertThat(only.fetchAllPages().toCompletableFuture()).isCompletedWithValue(only)
    }

    @Test
    fun `fails when a page fetch fails`() {
        val failure = RuntimeException("page fetch failed")
        val first = page(CompletableFuture.failedFuture(failure))

        val all = first.fetchAllPages().toCompletableFuture()

        assertThat(all).isCompletedExceptionally
        assertThat(catchThrowable { all.join() }.cause).isSameAs(failure)
    }
}
