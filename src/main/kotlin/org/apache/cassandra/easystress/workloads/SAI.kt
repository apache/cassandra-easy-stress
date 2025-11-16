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
package org.apache.cassandra.easystress.workloads

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.apache.cassandra.easystress.MinimumVersion
import org.apache.cassandra.easystress.PartitionKey
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.WorkloadParameter
import org.apache.cassandra.easystress.generators.Field
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.LastName
import org.apache.cassandra.easystress.generators.functions.Random
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.ThreadLocalRandom

/**
 * Executes a SAI workload with queries restricted to a single partition,
 * which is the primary workload targeted by SAI indexes.
 *
 * SAI (Storage Attached Index) is available in Cassandra 5.0+
 */

const val TABLE: String = "sai"
const val MIN_VALUE_TEXT_SIZE = 1
const val MAX_VALUE_TEXT_SIZE = 2

@MinimumVersion("5.0")
class SAI : IStressWorkload {
    @WorkloadParameter(description = "Operator to use for SAI queries, defaults to equality = search.")
    var intCompare = "="

    @WorkloadParameter(description = "Logic operator combining multiple predicates.  Not yet supported.")
    var operator = "AND"

    @WorkloadParameter(description = "Max rows per partition")
    var rows = 10000

    @WorkloadParameter(description = "Enable global queries with true to query the entire cluster.")
    var global = false

    @WorkloadParameter(description = "Fields to index, comma separated")
    var indexFields = "value_int,value_text"

    @WorkloadParameter(description = "Fields to search, comma separated")
    var searchFields = "value_text"

    @WorkloadParameter(description = "Limit count.")
    var limit = 0

    lateinit var insert: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var delete: PreparedStatement

    // mutable sets are backed by a LinkedHashSet, so we can preserve order
    lateinit var indexFieldsSet: Set<String>
    lateinit var searchFieldsSet: Set<String>

    val log = logger()

    override fun prepare(session: CqlSession) {
        println("Preparing workload with global=$global")

        indexFieldsSet = indexFields.split("\\s*,\\s*".toRegex()).toSet()
        searchFieldsSet = searchFields.split("\\s*,\\s*".toRegex()).toSet()

        insert = session.prepare("INSERT INTO $TABLE (partition_id, c_id, value_text, value_int) VALUES (?, ?, ?, ?)")
        // todo make the operator configurable with a workload parameter

        val parts = mutableListOf<String>()

        // if we're doing a global query we skip the partition key
        if (!global) {
            parts.add("partition_id = ?")
        }

        if (searchFieldsSet.contains("value_text")) {
            parts.add("value_text = ?")
        }
        if (searchFieldsSet.contains("value_int")) {
            parts.add("value_int $intCompare ?")
        }

        val limitClause = if (limit > 0) " LIMIT $limit " else ""
        val selectQuery = "SELECT * from $TABLE WHERE " + parts.joinToString(" $operator ") + limitClause
        println("Preparing $selectQuery")
        select = session.prepare(selectQuery)

        delete = session.prepare("DELETE from $TABLE WHERE partition_id = ? AND c_id = ?")
    }

    override fun schema(): List<String> {
        val result =
            mutableListOf(
                """
                CREATE TABLE IF NOT EXISTS $TABLE (
                    partition_id text,
                    c_id int,
                    value_text text,
                    value_int int,
                    primary key (partition_id, c_id)
                )
                """.trimIndent(),
            )
        if (indexFields.contains("value_text")) {
            result.add("CREATE INDEX IF NOT EXISTS ON $TABLE (value_text) USING 'sai'")
        }
        if (indexFields.contains("value_int")) {
            result.add("CREATE INDEX IF NOT EXISTS ON $TABLE (value_int) USING 'sai'")
        }
        return result
    }

    override fun getRunner(context: StressContext): IStressRunner {
        return object : IStressRunner {
            val c_id = ThreadLocalRandom.current()

            // use nextRowId
            val nextRowId: Int get() = c_id.nextInt(0, rows)

            // generator for the value field
            val value_text = context.registry.getGenerator(TABLE, "value_text")
            val value_int = context.registry.getGenerator(TABLE, "value_int")

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val bound =
                    insert
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, nextRowId)
                        .setString(2, value_text.getText())
                        .setInt(3, value_int.getInt())
                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                // first create a bind statement
                var bound = select.bind()
                var index = 0

                if (!global) {
                    bound = bound.setString(index++, partitionKey.getText())
                }

                if (searchFieldsSet.contains("value_text")) {
                    bound = bound.setString(index++, value_text.getText())
                }

                if (searchFieldsSet.contains("value_int")) {
                    bound = bound.setInt(index, value_int.getInt())
                }

                return Operation.SelectStatement(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey) =
                Operation.Deletion(
                    delete
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, nextRowId),
                )
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> =
        mapOf(
            Field(TABLE, "value_text") to LastName(),
            Field(TABLE, "value_int") to
                Random().apply {
                    min = 0
                    max = 10000
                },
        )
}
