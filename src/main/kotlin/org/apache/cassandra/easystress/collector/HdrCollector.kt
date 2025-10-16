package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import org.HdrHistogram.SynchronizedHistogram
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Either
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.workloads.Operation
import java.io.File
import java.io.PrintStream

/**
 * Stores all events into a HdrHistorgram and publishes to 3 files at the end of the run
 */
class HdrCollector(
    val hdrHistogramPrefix: String,
) : Collector {
    // Using a synchronized histogram for now, we may need to change this later if it's a perf bottleneck
    val mutationHistogram = SynchronizedHistogram(2)
    val selectHistogram = SynchronizedHistogram(2)
    val deleteHistogram = SynchronizedHistogram(2)

    override fun collect(
        ctx: StressContext,
        op: Operation,
        result: Either<AsyncResultSet, Throwable>,
        startNanos: Long,
        endNanos: Long,
    ) {
        if (result is Either.Right) return // only success is tracked

        // we log to the HDR histogram and do the callback for mutations
        // might extend this to select, but I can't see a reason for it now
        when (op) {
            is Operation.Mutation, is Operation.DDL -> {
                mutationHistogram.recordValue(endNanos - op.createdAtNanos)
            }
            is Operation.Deletion -> {
                deleteHistogram.recordValue(endNanos - op.createdAtNanos)
            }
            is Operation.SelectStatement -> {
                selectHistogram.recordValue(endNanos - op.createdAtNanos)
            }
            else -> {
                // ignore
            }
        }
    }

    override fun close(ctx: Context) {
        // print out the hdr histograms if requested to 3 separate files
        val pairs =
            listOf(
                Pair(mutationHistogram, "mutations"),
                Pair(selectHistogram, "reads"),
                Pair(deleteHistogram, "deletes"),
            )
        for (entry in pairs) {
            val fp = File(hdrHistogramPrefix + "-" + entry.second + ".txt")
            entry.first.outputPercentileDistribution(PrintStream(fp), 1_000_000.0)
        }
    }
}
