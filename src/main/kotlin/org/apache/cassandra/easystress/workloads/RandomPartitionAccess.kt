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
import org.apache.cassandra.easystress.PartitionKey
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.WorkloadParameter
import org.apache.cassandra.easystress.generators.Field
import org.apache.cassandra.easystress.generators.FieldFactory
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.Random
import java.util.concurrent.ThreadLocalRandom

class RandomPartitionAccess : IStressProfile {
    @WorkloadParameter(description = "Number of rows per partition, defaults to 100")
    var rows = 100

    @WorkloadParameter("Select random row or the entire partition.  Acceptable values: row, partition")
    var select = "row"

    @WorkloadParameter("Delete random row or the entire partition.  Acceptable values: row, partition")
    var delete = "row"

    lateinit var insertQuery: PreparedStatement
    lateinit var selectQuery: PreparedStatement
    lateinit var deleteQuery: PreparedStatement

    override fun prepare(session: CqlSession) {
        insertQuery = session.prepare("INSERT INTO random_access (partition_id, row_id, value) values (?, ?, ?)")

        selectQuery =
            when (select) {
                "partition" -> {
                    println("Preparing full partition reads")
                    session.prepare("SELECT * from random_access WHERE partition_id = ?")
                }
                "row" -> {
                    println("Preparing single row reads")
                    session.prepare("SELECT * from random_access WHERE partition_id = ? and row_id = ?")
                }
                else ->
                    throw RuntimeException("select must be row or partition.")
            }

        deleteQuery =
            when (delete) {
                "partition" -> {
                    println("Preparing full partition deletes")
                    session.prepare("DELETE FROM random_access WHERE partition_id = ?")
                }
                "row" -> {
                    println("Preparing single row deletes")
                    session.prepare("DELETE FROM random_access WHERE partition_id = ? and row_id = ?")
                }
                else ->
                    throw RuntimeException("select must be row or partition.")
            }
    }

    override fun schema(): List<String> {
        return listOf(
            """CREATE TABLE IF NOT EXISTS random_access (
                           partition_id text,
                           row_id int,
                           value text,
                           primary key (partition_id, row_id)
                        )""",
        )
    }

    override fun getRunner(context: StressContext): IStressRunner {
        println("Using $rows rows per partition")

        return object : IStressRunner {
            val value = context.registry.getGenerator("random_access", "value")
            val random = ThreadLocalRandom.current()

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val rowId = random.nextInt(0, rows)
                val bound =
                    insertQuery.bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, rowId)
                        .setString(2, value.getText())
                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound =
                    when (select) {
                        "partition" ->
                            selectQuery.bind()
                                .setString(0, partitionKey.getText())
                        "row" -> {
                            val rowId = random.nextInt(0, rows)
                            selectQuery.bind()
                                .setString(0, partitionKey.getText())
                                .setInt(1, rowId)
                        }
                        else -> throw RuntimeException("not even sure how you got here")
                    }
                return Operation.SelectStatement(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val bound =
                    when (delete) {
                        "partition" ->
                            deleteQuery.bind()
                                .setString(0, partitionKey.getText())
                        "row" -> {
                            val rowId = random.nextInt(0, rows)
                            deleteQuery.bind()
                                .setString(0, partitionKey.getText())
                                .setInt(1, rowId)
                        }
                        else -> throw RuntimeException("not even sure how you got here")
                    }
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        val ra = FieldFactory("random_access")
        return mapOf(
            ra.getField("value") to
                Random().apply {
                    min = 100
                    max = 200
                },
        )
    }
}
