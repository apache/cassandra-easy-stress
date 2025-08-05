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
package com.rustyrazorblade.easycassstress.workloads

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rustyrazorblade.easycassstress.PartitionKey
import com.rustyrazorblade.easycassstress.RequireDSE
import com.rustyrazorblade.easycassstress.StressContext
import com.rustyrazorblade.easycassstress.WorkloadParameter
import com.rustyrazorblade.easycassstress.generators.Field
import com.rustyrazorblade.easycassstress.generators.FieldGenerator
import com.rustyrazorblade.easycassstress.generators.functions.Book
import com.rustyrazorblade.easycassstress.generators.functions.Random
import java.util.concurrent.ThreadLocalRandom

@RequireDSE
class DSESearch : IStressProfile {
    val table: String = "dse_search"
    val minValueTextSize = 5
    val maxValueTextSize = 10

    lateinit var insert: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var delete: PreparedStatement

    val mapper = jacksonObjectMapper()

    @WorkloadParameter("Enable global queries.")
    var global = false

    @WorkloadParameter(description = "Max rows per partition")
    var rows = 10000

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO $table (key, c, value_text) VALUES (?, ?, ?)")
        select = session.prepare("SELECT key, c, value_text from $table WHERE solr_query = ?")

        delete = session.prepare("DELETE from $table WHERE key = ? and c = ?")
    }

    override fun schema(): List<String> {
        return listOf(
            """
            CREATE TABLE IF NOT EXISTS $table (
                    key text,
                    c int,
                    value_text text,
                    PRIMARY KEY (key, c)
            )
            """.trimIndent(),
            """
            CREATE SEARCH INDEX IF NOT EXISTS ON $table WITH COLUMNS value_text
            """.trimIndent(),
        )
    }

    override fun getRunner(context: StressContext): IStressRunner {
        val value = context.registry.getGenerator(table, "value_text")
        val regex = "[^a-zA-Z0-9]".toRegex()

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class SolrQuery(
            var q: String,
            var fq: String,
        )

        return object : IStressRunner {
            val c_id = ThreadLocalRandom.current()
            val nextRowId: Int get() = c_id.nextInt(0, rows)

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val bound =
                    insert.bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, nextRowId)
                        .setString(2, value.getText())
                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val valueValue =
                    value.getText().substringBeforeLast(" ")
                        .replace(regex, " ")
                        .trim()

                val query =
                    SolrQuery(
                        q = "value_text:($valueValue)",
                        fq = if (!global) "key:${partitionKey.getText()}" else "",
                    )

                val queryString = mapper.writeValueAsString(query)

                val bound =
                    select.bind()
                        .setString(0, queryString)
                return Operation.SelectStatement(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey) =
                Operation.Deletion(
                    delete.bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, nextRowId),
                )
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        // Create fields for the table
        return mapOf(
            Field(table, "value_text") to
                Book().apply {
                    min = minValueTextSize
                    max = maxValueTextSize
                },
            Field(table, "value_int") to
                Random().apply {
                    min = 0
                    max = 100
                },
        )
    }
}
