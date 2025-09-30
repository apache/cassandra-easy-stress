package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue
import org.agrona.concurrent.BackoffIdleStrategy
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Either
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.workloads.Operation
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base type for collectors that have "expensive" work that needs to happen off-thread.  Every call to collect will
 * generate an object, push to a queue, and processed in another worker thread.
 *
 * Implementations must define the writer interface which will be called to do the "real" work for the collector.
 */
abstract class AsyncCollector(
    fileOrDirectory: File,
) : Collector {
    data class Event(
        val op: Operation,
        val result: Either<AsyncResultSet, Throwable>,
        val startNanos: Long,
        val endNanos: Long,
    )

    interface Writer : Closeable {
        fun write(event: Event)
    }

    private val queue = MpscArrayQueue<Event>(Integer.getInteger("cassandra-easy-stress.event_csv_queue_size", 4096))
    private val writer = createWriter(fileOrDirectory)

    @Volatile
    private var running = true
    private val thread = Thread(this::run)
    private val idleStrategy = BackoffIdleStrategy()

    init {
        thread.isDaemon = true
        thread.name = "cassandra-easy-stress event raw log collector"
        thread.start()
    }

    val dropped = AtomicInteger()
    val counter = AtomicInteger()

    abstract fun createWriter(fileOrDirectory: File): Writer

    override fun collect(
        ctx: StressContext,
        op: Operation,
        result: Either<AsyncResultSet, Throwable>,
        startNanos: Long,
        endNanos: Long,
    ) {
        if (!queue.offer(Event(op, result, startNanos, endNanos))) {
            dropped.incrementAndGet()
        }
    }

    private fun run() {
        while (running) {
            try {
                val processed = queue.drain(writer::write)
                counter.addAndGet(processed)
                idleStrategy.idle(processed)
            } catch (t: Throwable) {
                System.err.println("Exception while writing raw logs")
                t.printStackTrace()
                running = false
                return
            }
        }
    }

    override fun close(context: Context) {
        running = false
        thread.join()
        writer.close()
        println("Wrote ${counter.get()} events; Dropped ${dropped.get()} events")
    }
}
