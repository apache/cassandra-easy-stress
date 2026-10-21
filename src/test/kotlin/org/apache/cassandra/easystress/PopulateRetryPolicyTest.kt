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
import com.datastax.oss.driver.api.core.retry.RetryDecision
import com.datastax.oss.driver.api.core.servererrors.OverloadedException
import com.datastax.oss.driver.api.core.servererrors.WriteType
import com.datastax.oss.driver.api.core.session.Request
import io.mockk.mockk
import org.apache.cassandra.easystress.PopulateRetryPolicy.Companion.MAX_RETRIES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@Suppress("DEPRECATION")
class PopulateRetryPolicyTest {
    companion object {
        private val policy = PopulateRetryPolicy(null, PopulateRetryPolicy.POPULATE_PROFILE)
        private val request = mockk<Request>()
        private val cl = ConsistencyLevel.LOCAL_QUORUM

        /**
         * Every failure the driver can hand a retry policy, as a function of the retry count.
         */
        @JvmStatic
        fun failures(): List<Arguments> =
            listOf<Pair<String, (Int) -> RetryDecision>>(
                "read timeout" to { n -> policy.onReadTimeout(request, cl, 2, 1, false, n) },
                "write timeout" to { n -> policy.onWriteTimeout(request, cl, WriteType.SIMPLE, 2, 1, n) },
                "batch write timeout" to { n -> policy.onWriteTimeout(request, cl, WriteType.UNLOGGED_BATCH, 2, 1, n) },
                "counter write timeout" to { n -> policy.onWriteTimeout(request, cl, WriteType.COUNTER, 2, 1, n) },
                "unavailable" to { n -> policy.onUnavailable(request, cl, 2, 1, n) },
                "aborted request" to { n -> policy.onRequestAborted(request, IllegalStateException("closed"), n) },
                "overloaded" to { n -> policy.onErrorResponse(request, mockk<OverloadedException>(), n) },
            ).map { (name, decide) -> Arguments.of(name, decide) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    fun retriesOnTheNextNodeUntilTheCap(
        name: String,
        decide: (Int) -> RetryDecision,
    ) {
        for (retryCount in 0 until MAX_RETRIES) {
            assertThat(decide(retryCount)).`as`("$name, retry $retryCount").isEqualTo(RetryDecision.RETRY_NEXT)
        }
        assertThat(decide(MAX_RETRIES)).`as`(name).isEqualTo(RetryDecision.RETHROW)
    }
}
