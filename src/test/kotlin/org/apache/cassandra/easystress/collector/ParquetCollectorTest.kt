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
import org.apache.parquet.hadoop.util.HadoopInputFile
import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

    @Test
    fun usesZstdAndV2Encodings() {
        val file = tempDir!!.resolve("rawlog.parquet").toFile()
        val op = Operation.SelectStatement(mockk<BoundStatement>())
        val writer = ParquetCollector.ParquetTableWriter(file)
        val rows = 100
        for (i in 0 until rows) {
            writer.write(
                AsyncCollector.Event(
                    op,
                    Either.Left(mockk<AsyncResultSet>()),
                    op.createdAtNanos + i,
                    op.createdAtNanos + 1_000 + i * 2L,
                ),
            )
        }
        writer.close()

        val path =
            org.apache.hadoop.fs
                .Path(file.toURI())
        val conf = Configuration()
        ParquetFileReader.open(HadoopInputFile.fromPath(path, conf)).use { reader ->
            val columns = reader.footer.blocks.flatMap { it.columns }
            Assertions.assertThat(columns).isNotEmpty
            Assertions.assertThat(columns.map { it.codec }).containsOnly(CompressionCodecName.ZSTD)
            val int64Encodings =
                columns
                    .filter { it.path.toDotString().endsWith("_ms") || it.path.toDotString().endsWith("_ns") }
                    .flatMap { it.encodings }
            Assertions.assertThat(int64Encodings).contains(Encoding.DELTA_BINARY_PACKED)
        }

        // semantics must not change: same columns, same values
        val read = mutableListOf<Group>()
        ParquetReader.builder(GroupReadSupport(), path).withConf(conf).build().use { reader ->
            while (true) {
                read.add(reader.read() ?: break)
            }
        }
        Assertions.assertThat(read).hasSize(rows)
        read.forEachIndexed { i, g ->
            Assertions.assertThat(g.getString("operation", 0)).isEqualTo("Select")
            Assertions.assertThat(g.getBoolean("success", 0)).isTrue
            Assertions.assertThat(g.getString("failure_reason", 0)).isEmpty()
            Assertions.assertThat(g.getLong("request_start_time_ms", 0)).isEqualTo(op.createdAtMillis)
            Assertions.assertThat(g.getLong("request_duration_ns", 0)).isEqualTo(1_000 + i * 2L)
            Assertions.assertThat(g.getLong("service_duration_ns", 0)).isEqualTo(1_000 + i * 2L - i)
        }
    }

    companion object {
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
