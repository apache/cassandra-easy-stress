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
import com.github.ajalt.mordant.TermColors
import org.apache.logging.log4j.kotlin.logger
import java.text.DecimalFormat
import java.util.SortedMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleLineConsoleReporter(
    registry: MetricRegistry,
) : ScheduledReporter(
        registry,
        "single-line-console-reporter",
        MetricFilter.ALL,
        TimeUnit.SECONDS,
        TimeUnit.MILLISECONDS,
    ) {
    val logger = logger()
    var lines = 0L

    var opHeaders = listOf("Count", "Latency (p99)", "1min (req/s)")
    var width = mutableMapOf<Int, Int>().withDefault { 0 }

    // initialize all the headers
    // it's ok if this isn't perfect, it just has to work for the first round of headers
    init {

        for ((i, h) in opHeaders.withIndex()) {
            getWidth(i, h) // first pass - writes
            getWidth(i + opHeaders.size, h) // second pass - reads
        }
    }

    val formatter = DecimalFormat("##.##")

    val termColors = TermColors()

    override fun report(
        gauges: SortedMap<String, Gauge<Any>>?,
        counters: SortedMap<String, Counter>?,
        histograms: SortedMap<String, Histogram>?,
        meters: SortedMap<String, Meter>?,
        timers: SortedMap<String, Timer>?,
    ) {
        if (lines % 10L == 0L) {
            printHeader()
        }

        val state = AtomicInteger()

        // this is a little weird, but we should show the same headers for writes & selects
        val queries = listOf(timers!!["mutations"]!!, timers["selects"]!!, timers["deletions"]!!)

        for (queryType in queries) {
            with(queryType) {
                printColumn(count, state.getAndIncrement())

                val duration = convertDuration(snapshot.get99thPercentile())

                printColumn(duration, state.getAndIncrement())
                printColumn(formatter.format(oneMinuteRate), state.getAndIncrement())
            }
            print(" | ")
        }

        val errors = meters!!["errors"]!!
        printColumn(errors.count, state.getAndIncrement())
        printColumn(formatter.format(errors.oneMinuteRate), state.getAndIncrement())

        println()
        lines++
    }

    /*
    Helpers for printing the column with correct spacing
     */
    fun printColumn(
        value: Double,
        index: Int,
    ) {
        // round to 2 decimal places
        val tmp = DecimalFormat("##.##").format(value)

        printColumn(tmp, index)
    }

    fun printColumn(
        value: Long,
        index: Int,
    ) {
        printColumn(value.toString(), index)
    }

    fun printColumn(
        value: Int,
        index: Int,
    ) {
        printColumn(value.toString(), index)
    }

    fun printColumn(
        value: String,
        index: Int,
    ) {
        val width = getWidth(index, value)
        val tmp = value.padStart(width)
        print(tmp)
    }

    fun printHeader() {
        var widthOfEachOperation = 0

        for (i in 0..opHeaders.size) {
            widthOfEachOperation += getWidth(i)
        }

        val paddingEachSide = (widthOfEachOperation - "Writes".length) / 2 - 1

        print(" ".repeat(paddingEachSide))
        print(termColors.blue("Writes"))
        print(" ".repeat(paddingEachSide))

        print(" ".repeat(paddingEachSide))
        print(termColors.blue("Reads"))
        print(" ".repeat(paddingEachSide))

        print(" ".repeat(paddingEachSide))
        print(termColors.blue("Deletes"))
        print(" ".repeat(paddingEachSide))

        print(" ".repeat(6))
        print(termColors.red("Errors"))

        println()
        var i = 0

        for (x in 0..2) {
            for (h in opHeaders) {
                val colWidth = getWidth(i, h)
                val required = colWidth - h.length

                val tmp = " ".repeat(required) + termColors.underline(h)

                print(tmp)
                i++
            }
            print(" | ")
        }

        val errorHeaders = arrayListOf("Count", "1min (errors/s)")
        // TODO: refactor this + the above loop to be a single function
        for (h in errorHeaders) {
            val colWidth = getWidth(i, h)
            val required = colWidth - h.length

            val tmp = " ".repeat(required) + termColors.underline(h)

            print(tmp)
            i++
        }

        println()
    }

    /**
     * Gets the width for a column, resizing the column if necessary
     */
    fun getWidth(
        i: Int,
        value: String = "",
    ): Int {
        val tmp = width.getValue(i)
        if (value.length > tmp) {
            logger.debug("Resizing column[$i] to ${value.length}")
            // give a little extra padding in case the number grows quickly
            width.set(i, value.length + 2)
        }
        return width.getValue(i)
    }
}
