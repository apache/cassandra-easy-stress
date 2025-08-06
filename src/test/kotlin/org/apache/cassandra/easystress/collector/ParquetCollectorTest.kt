package org.apache.cassandra.easystress.collector

import com.datastax.oss.driver.api.core.CqlSession
import com.google.common.io.Files
import org.apache.cassandra.easystress.Context
import org.apache.cassandra.easystress.Metrics
import org.apache.cassandra.easystress.commands.Run
import org.apache.cassandra.easystress.generators.Registry
import io.mockk.mockk
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
