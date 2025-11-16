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

@Function(
    name = "firstname",
    description = "First names.",
)
class FirstName : FieldGenerator {
    override fun setParameters(params: List<String>) {
        // nothing to do here
    }

    override fun getDescription() =
        """
        Uses common first names, both male and female.
        """.trimIndent()

    val names = mutableListOf<String>()

    init {

        for (s in arrayListOf("female", "male")) {
            val tmp =
                this::class.java
                    .getResource("/names/female.txt")
                    .readText()
                    .split("\n")
                    .map { it.split(" ").first() }
            names.addAll(tmp)
        }
    }

    override fun getText(): String {
        val element = ThreadLocalRandom.current().nextInt(0, names.size)
        return names[element]
    }
}
