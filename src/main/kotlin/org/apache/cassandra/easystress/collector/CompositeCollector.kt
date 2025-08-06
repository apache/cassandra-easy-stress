package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Either
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.workloads.Operation

/**
 * Groups a list of collectors together and will call each in the same order.
 */
class CompositeCollector(
    private vararg val collectors: Collector,
) : Collector {
    override fun collect(
        ctx: StressContext,
        op: Operation,
        result: Either<AsyncResultSet, Throwable>,
        startNanos: Long,
        endNanos: Long,
    ) {
        for (c in collectors) {
            c.collect(ctx, op, result, startNanos, endNanos)
        }
    }

    override fun close(context: Context) {
        for (c in collectors) {
            c.close(context)
        }
    }
}
