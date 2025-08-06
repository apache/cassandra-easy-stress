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
package org.apache.cassandra.easystress.converters

import com.beust.jcommander.IStringConverter
import java.time.Duration

class HumanReadableTimeConverter : IStringConverter<Long> {
    override fun convert(value: String?): Long {
        var duration = Duration.ofMinutes(0)

        val valueCharSequence = value!!.subSequence(0, value.length)
        /**
         * The duration is passed in via the value variable. It could contain multiple time values e.g. "1d 2h 3m 4s".
         * Parse the string using the following process:
         * 1. Find all occurrences of of an integer with a time unit and iterate through the matches. Note we need
         *      to convert the value from a String to CharSequence so we can pass it to findAll.
         * 2. Iterate through the matched values. Add to the duration based on the units of each value.
         */
        Regex("(?<num>\\d+)(?<str>[dhms])")
            .findAll(valueCharSequence)
            .forEach {
                val quantity = it.groups["num"]!!.value.toLong()
                when (it.groups["str"]!!.value) {
                    "d" -> duration = duration.plusDays(quantity)
                    "h" -> duration = duration.plusHours(quantity)
                    "m" -> duration = duration.plusMinutes(quantity)
                    "s" -> duration = duration.plusSeconds(quantity)
                }
            }

        if (duration.isZero) {
            throw IllegalArgumentException("Value $value resulted in 0 time duration")
        }

        return duration.toMinutes()
    }
}
