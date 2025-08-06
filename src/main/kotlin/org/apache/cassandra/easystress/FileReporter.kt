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

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.ScheduledReporter
import com.codahale.metrics.Timer
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.util.Date
import java.util.SortedMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class FileReporter(registry: MetricRegistry, outputFileName: String, command: String) : ScheduledReporter(
    registry,
    "file-reporter",
    MetricFilter.ALL,
    TimeUnit.SECONDS,
    TimeUnit.MILLISECONDS,
) {
    // date 24h time
    // Thu-14Mar19-13.30.00
    private val startTime = Date()

    private val opHeaders = listOf("Count", "Latency (p99)", "1min (req/s)").joinToString(",", postfix = ",")
    private val errorHeaders = listOf("Count", "1min (errors/s)").joinToString(",")

    val outputFile = File(outputFileName)
    val buffer: BufferedWriter

    init {

        buffer =
            if (outputFileName.endsWith(".gz")) {
                GZIPOutputStream(outputFile.outputStream()).bufferedWriter()
            } else {
                outputFile.bufferedWriter()
            }

        buffer.write("# cassandra-easy-stress run at $startTime")
        buffer.newLine()
        buffer.write("# $command")
        buffer.newLine()

        buffer.write(",,Mutations,,,")
        buffer.write("Reads,,,")
        buffer.write("Deletes,,,")
        buffer.write("Errors,")
        buffer.newLine()

        buffer.write("Timestamp, Elapsed Time,")
        buffer.write(opHeaders)
        buffer.write(opHeaders)
        buffer.write(opHeaders)
        buffer.write(errorHeaders)
        buffer.newLine()
    }

    private fun Timer.getMetricsList(): List<Any> {
        val duration = convertDuration(this.snapshot.get99thPercentile())

        return listOf(this.count, duration, this.oneMinuteRate)
    }

    override fun report(
        gauges: SortedMap<String, Gauge<Any>>?,
        counters: SortedMap<String, Counter>?,
        histograms: SortedMap<String, Histogram>?,
        meters: SortedMap<String, Meter>?,
        timers: SortedMap<String, Timer>?,
    ) {
        val timestamp = Instant.now().toString()
        val elapsedTime = Instant.now().minusMillis(startTime.time).toEpochMilli() / 1000

        buffer.write(timestamp + "," + elapsedTime + ",")

        val writeRow =
            timers!!["mutations"]!!
                .getMetricsList()
                .joinToString(",", postfix = ",")

        buffer.write(writeRow)

        val readRow =
            timers["selects"]!!
                .getMetricsList()
                .joinToString(",", postfix = ",")

        buffer.write(readRow)

        val deleteRow =
            timers["deletions"]!!
                .getMetricsList()
                .joinToString(",", postfix = ",")

        buffer.write(deleteRow)

        val errors = meters!!["errors"]!!
        val errorRow =
            listOf(errors.count, errors.oneMinuteRate)
                .joinToString(",", postfix = "\n")

        buffer.write(errorRow)
    }

    override fun stop() {
        buffer.flush()
        buffer.close()
        super.stop()
    }
}
