package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Either
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.workloads.Operation

/**
 * When an operation completes (success or failure) this interface is called showing the state at that moment.  This
 * interface is part of the "hot" path and as such implementations should respect that and should push expensive work
 * outside the thread calling this.
 */
interface Collector {
    fun collect(
        ctx: StressContext,
        op: Operation,
        result: Either<AsyncResultSet, Throwable>,
        startNanos: Long,
        endNanos: Long,
    )

    fun close(context: Context) {
    }
}
