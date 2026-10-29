package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.google.common.io.Files
import io.mockk.mockk
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Either
import org.apache.cassandra.easystress.Metrics
import org.apache.cassandra.easystress.commands.Run
import org.apache.cassandra.easystress.generators.Registry
import org.apache.cassandra.easystress.workloads.Operation
import org.apache.hadoop.conf.Configuration
import org.apache.parquet.column.Encoding
import org.apache.parquet.example.data.Group
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.hadoop.example.GroupReadSupport
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.hadoop.metadata.ParquetMetadata
import org.apache.parquet.hadoop.util.HadoopInputFile
import org.apache.parquet.schema.MessageTypeParser
import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Path

internal class ParquetCollectorTest {
    @TempDir
    var tempDir: Path? = null

    @Test
    fun nonExistingDirectory() {
        val fileOrDirectory = tempDir!!.resolve("doesnotexist").resolve("noreallydoesnotexist").toFile()
        val c = ParquetCollector(fileOrDirectory)
        c.close(ctx)

        Assertions.assertThat(fileOrDirectory).exists().isFile
    }

    @Test
    fun existingDirectory() {
        val expected = tempDir!!.resolve("rawlog.parquet").toFile()
        val c = ParquetCollector(tempDir!!.toFile())
        c.close(ctx)

        Assertions.assertThat(expected).exists().isFile
    }

    @Test
    fun existingFile() {
        val expected = tempDir!!.resolve("rawlog.parquet").toFile()
        val unexpectedBytes = "some text".toByteArray()
        Files.write(unexpectedBytes, expected)
        val c = ParquetCollector(expected)
        c.close(ctx)

        Assertions.assertThat(expected).exists().isFile.doesNotHave(
            object : Condition<File>() {
                override fun matches(value: File?): Boolean = value?.let { Files.toByteArray(it).contentEquals(unexpectedBytes) } ?: false
            },
        )
    }

    private fun writeEvents(
        file: File,
        byteStreamSplit: Boolean,
    ): Operation {
        val op = Operation.SelectStatement(mockk<BoundStatement>())
        val writer = ParquetCollector.ParquetTableWriter(file, byteStreamSplit)
        for (i in 0 until ROWS) {
            val result: Either<AsyncResultSet, Throwable> =
                if (i % 10 == 9) Either.Right(IllegalStateException("boom $i")) else Either.Left(mockk<AsyncResultSet>())
            writer.write(AsyncCollector.Event(op, result, op.createdAtNanos + i, op.createdAtNanos + 1_000 + i * 2L))
        }
        writer.close()
        return op
    }

    private fun hadoopPath(file: File) =
        org.apache.hadoop.fs
            .Path(file.toURI())

    private fun footer(file: File): ParquetMetadata =
        ParquetFileReader.open(HadoopInputFile.fromPath(hadoopPath(file), Configuration())).use { it.footer }

    private fun encodingsByColumn(footer: ParquetMetadata): Map<String, Set<Encoding>> =
        footer.blocks
            .flatMap { it.columns }
            .groupBy { it.path.toDotString() }
            .mapValues { (_, chunks) -> chunks.flatMap { it.encodings }.toSet() }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun roundTrip(byteStreamSplit: Boolean) {
        val file = tempDir!!.resolve("rawlog.parquet").toFile()
        val op = writeEvents(file, byteStreamSplit)

        val read = mutableListOf<Group>()
        ParquetReader.builder(GroupReadSupport(), hadoopPath(file)).withConf(Configuration()).build().use { reader ->
            while (true) {
                read.add(reader.read() ?: break)
            }
        }
        Assertions.assertThat(read).hasSize(ROWS)
        read.forEachIndexed { i, g ->
            val failed = i % 10 == 9
            Assertions.assertThat(g.getString("operation", 0)).isEqualTo("Select")
            Assertions.assertThat(g.getBoolean("success", 0)).isEqualTo(!failed)
            if (failed) {
                Assertions.assertThat(g.getString("failure_reason", 0)).isEqualTo("IllegalStateException")
                Assertions.assertThat(g.getString("failure_stacktrace", 0)).contains("IllegalStateException: boom $i")
            } else {
                Assertions.assertThat(g.getString("failure_reason", 0)).isEmpty()
                Assertions.assertThat(g.getString("failure_stacktrace", 0)).isEmpty()
            }
            Assertions.assertThat(g.getLong("request_start_time_ms", 0)).isEqualTo(op.createdAtMillis)
            Assertions.assertThat(g.getLong("request_duration_ns", 0)).isEqualTo(1_000 + i * 2L)
            Assertions.assertThat(g.getLong("service_duration_ns", 0)).isEqualTo(1_000 + i * 2L - i)
        }
    }

    @Test
    fun defaultEncodings() {
        val file = tempDir!!.resolve("rawlog.parquet").toFile()
        writeEvents(file, byteStreamSplit = false)

        val footer = footer(file)
        Assertions.assertThat(footer.blocks.flatMap { it.columns }.map { it.codec }).containsOnly(CompressionCodecName.ZSTD)
        val encodings = encodingsByColumn(footer)
        for (col in INT64_COLUMNS) {
            Assertions.assertThat(encodings[col]).describedAs(col).contains(Encoding.DELTA_BINARY_PACKED)
        }
        Assertions.assertThat(encodings.values.flatten()).doesNotContain(Encoding.BYTE_STREAM_SPLIT)
    }

    @Test
    fun byteStreamSplitEncodings() {
        val file = tempDir!!.resolve("rawlog.parquet").toFile()
        writeEvents(file, byteStreamSplit = true)

        val encodings = encodingsByColumn(footer(file))
        for (col in listOf("service_duration_ns", "request_duration_ns")) {
            Assertions.assertThat(encodings[col]).describedAs(col).contains(Encoding.BYTE_STREAM_SPLIT)
        }
        for (col in listOf("service_start_time_ms", "request_start_time_ms")) {
            Assertions.assertThat(encodings[col]).describedAs(col).contains(Encoding.DELTA_BINARY_PACKED)
        }
    }

    /** The output must stay readable by consumers of the released schema. */
    @Test
    fun schemaMatchesReleasedSchema() {
        val released =
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
        val file = tempDir!!.resolve("rawlog.parquet").toFile()
        writeEvents(file, byteStreamSplit = false)
        Assertions.assertThat(footer(file).fileMetaData.schema).isEqualTo(released)
    }

    companion object {
        private const val ROWS = 100
        private val INT64_COLUMNS = listOf("service_start_time_ms", "service_duration_ns", "request_start_time_ms", "request_duration_ns")

        val ctx =
            Context(
                mockk<CqlSession>(),
                mockk<Run>(),
                mockk<Metrics>(),
                mockk<Registry>(),
                null,
                mockk<Collector>(),
            )
    }
}
