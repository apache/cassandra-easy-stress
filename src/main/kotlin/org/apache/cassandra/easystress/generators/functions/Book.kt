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
    name = "book",
    description = "Picks random sections of books.",
)
class Book : FieldGenerator {
    var min: Int = 20
    var max: Int = 50

    override fun setParameters(params: List<String>) {
        min = params[0].toInt()
        max = params[1].toInt()
    }

    override fun getDescription() =
        """
        Uses random sections of open books to provide real world text data.
        """.trimIndent()

    companion object {
        fun create(
            min: Int,
            max: Int,
        ): Book {
            val b = Book()
            b.setParameters(arrayListOf(min.toString(), max.toString()))
            return b
        }
    }

    // all the content from books will go here
    val content = mutableListOf<String>()

    init {

        val files = listOf("alice.txt", "moby-dick.txt", "war.txt")
        for (f in files) {
            val tmp = this::class.java.getResource("/books/$f").readText()
            val splitContent = tmp.split("\\s+".toRegex())
            content.addAll(splitContent)
        }
    }

    override fun getText(): String {
        // first get the length
        val length = ThreadLocalRandom.current().nextInt(min, max)
        val start = ThreadLocalRandom.current().nextInt(0, content.size - length)

        return content.subList(start, start + length).joinToString(" ")
    }
}
