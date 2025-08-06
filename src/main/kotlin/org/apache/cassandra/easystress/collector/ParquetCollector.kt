package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.google.common.base.Throwables
import org.apache.cassandra.easystress.Either
import org.apache.hadoop.fs.Path
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.SimpleGroupFactory
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.example.ExampleParquetWriter
import org.apache.parquet.schema.MessageTypeParser
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Publishes all the requests to a Apache Parquet file to allow analytical tools to process the raw data rather than
 * having to rely solely on metrics (which are sampled).
 */
class ParquetCollector(
    fileOrDirectory: File,
) : AsyncCollector(fileOrDirectory) {
    override fun createWriter(fileOrDirectory: File): Writer =
        ParquetTableWriter(if (fileOrDirectory.isDirectory) File(fileOrDirectory, "rawlog.parquet") else fileOrDirectory)

    class ParquetTableWriter(
        file: File,
    ) : Writer {
        private val writer: ParquetWriter<Group>
        private val groupFactory: SimpleGroupFactory

        init {
            file.delete()
            val path = Path("file://${file.absolutePath}")
            println("Parquet Log path $path")
            val schema =
                MessageTypeParser.parseMessageType(
                    "message example {\n" +
                        "  required binary operation (UTF8);\n" +
                        "  required boolean success;\n" +
                        "  required binary failure_reason (UTF8);\n" +
                        "  required binary failure_stacktrace (UTF8);\n" +
                        "  required int64 service_start_time_ms;\n" +
                        "  required int64 service_duration_ns;\n" +
                        "  required int64 request_start_time_ms;\n" +
                        "  required int64 request_duration_ns;\n" +
                        "}",
                )
            groupFactory = SimpleGroupFactory(schema)
            writer = ExampleParquetWriter.builder(path).withType(schema).build()
        }

        override fun write(event: Event) {
            val requestStartNanos = event.op.createdAtNanos
            val requestStartMillis = event.op.createdAtMillis
            val requestDurationNanos = event.endNanos - requestStartNanos

            val serviceStartNanos = event.startNanos
            val serviceStartMillis = requestStartMillis + TimeUnit.NANOSECONDS.toMillis(serviceStartNanos - requestStartNanos)
            val serviceDurationNanos = event.endNanos - serviceStartNanos
            val group =
                groupFactory
                    .newGroup()
                    .append("operation", op(event))
                    .append("success", event.result is Either.Left)
                    .append("failure_reason", reasonName(event.result))
                    .append("failure_stacktrace", reasonStackTrace(event.result))
                    .append("service_start_time_ms", serviceStartMillis)
                    .append("service_duration_ns", serviceDurationNanos)
                    .append("request_start_time_ms", requestStartMillis)
                    .append("request_duration_ns", requestDurationNanos)
            writer.write(group)
        }

        override fun close() {
            writer.close()
        }
    }

    companion object {
        private fun op(event: Event) =
            event.op.javaClass.simpleName
                .replace("Statement", "")

        private fun reasonName(result: Either<AsyncResultSet, Throwable>) =
            if (result is Either.Right) {
                result.value.javaClass.simpleName
            } else {
                ""
            }

        private fun reasonStackTrace(result: Either<AsyncResultSet, Throwable>) =
            if (result is Either.Right) {
                Throwables.getStackTraceAsString(result.value)
            } else {
                ""
            }
    }
}
