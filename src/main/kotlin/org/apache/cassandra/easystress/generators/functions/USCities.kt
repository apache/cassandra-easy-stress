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
package org.apache.cassandra.easystress.generators.functions

import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.Function
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.toList

data class City(
    val name: String,
    val stateShort: String,
    val stateFull: String,
    val cityAlias: String,
) {
    override fun toString(): String = "$name, $stateShort"
}

@Function(
    name = "uscities",
    description = "US Cities",
)
class USCities : FieldGenerator {
    private val cities: List<City>
    private val size: Int

    init {

        val reader = this.javaClass.getResourceAsStream("/us_cities_states_counties.csv").bufferedReader()
        cities =
            reader
                .lines()
                .skip(1)
                .map { it.split("|") }
                .filter { it.size > 4 }
                .map { City(it[0], it[1], it[2], it[3]) }
                .toList()
        size = cities.count()
    }

    override fun setParameters(params: List<String>) {
    }

    override fun getText(): String {
        val tmp = ThreadLocalRandom.current().nextInt(0, size)
        return cities[tmp].toString()
    }

    override fun getDescription() =
        """
        Random US cities.
        """.trimIndent()
}
