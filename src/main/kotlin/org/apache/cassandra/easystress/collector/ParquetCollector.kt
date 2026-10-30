package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.google.common.base.Throwables
import org.apache.cassandra.easystress.Either
import org.apache.hadoop.fs.Path
import org.apache.parquet.column.ParquetProperties
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.SimpleGroupFactory
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.example.ExampleParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.schema.MessageTypeParser
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Publishes all the requests to a Apache Parquet file to allow analytical tools to process the raw data rather than
 * having to rely solely on metrics (which are sampled).
 */
class ParquetCollector(
    fileOrDirectory: File,
    byteStreamSplit: Boolean = false,
) : AsyncCollector(
        fileOrDirectory,
        { ParquetTableWriter(if (it.isDirectory) File(it, "rawlog.parquet") else it, byteStreamSplit) },
    ) {
    /**
     * @param byteStreamSplit use BYTE_STREAM_SPLIT for the `*_duration_ns` columns. Smaller files, but readers must
     * support BYTE_STREAM_SPLIT on INT64 (e.g. DuckDB >= 2.0, pyarrow >= 16; not Spark's vectorized reader).
     */
    class ParquetTableWriter(
        file: File,
        byteStreamSplit: Boolean = false,
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
            // Defaults are UNCOMPRESSED + PARQUET_1_0 (PLAIN int64), which for long runs produces multi-GB files.
            // ZSTD + PARQUET_2_0 (DELTA_BINARY_PACKED for the time/duration columns) is ~3x smaller with the same
            // schema and values.
            val builder =
                ExampleParquetWriter
                    .builder(path)
                    .withType(schema)
                    .withCompressionCodec(CompressionCodecName.ZSTD)
                    .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_2_0)
                    .withDictionaryEncoding(true)
                    // Large pages let the dictionary hold more distinct stack traces before falling back to plain.
                    .withPageSize(PAGE_SIZE_BYTES)
                    .withDictionaryPageSize(PAGE_SIZE_BYTES)
            // start times are near-monotonic and durations near-unique, so skip the dictionary and go straight to
            // DELTA_BINARY_PACKED (or BYTE_STREAM_SPLIT for durations, when enabled).
            INT64_COLUMNS.forEach { builder.withDictionaryEncoding(it, false) }
            if (byteStreamSplit) {
                DURATION_COLUMNS.forEach { builder.withByteStreamSplitEncoding(it, true) }
            }
            writer = builder.build()
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
        private const val PAGE_SIZE_BYTES = 8 * 1024 * 1024
        private val DURATION_COLUMNS = listOf("service_duration_ns", "request_duration_ns")
        private val INT64_COLUMNS = listOf("service_start_time_ms", "request_start_time_ms") + DURATION_COLUMNS

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
