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
import org.apache.logging.log4j.kotlin.logger
import java.util.Timer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayDeque
import kotlin.concurrent.schedule

/**
 * Creates and drops unique tables.
 * Most useful when paired with another workload, to determine if
 * impact of schema operations on running workloads.
 */
class CreateDrop : IStressWorkload {
    @WorkloadParameter("Number of fields in each table.")
    var fields = 1

    @WorkloadParameter("Number of tables to keep active")
    var activeTables = 10

    @WorkloadParameter("Seconds between DDL operations")
    var seconds = 60.0

    var logger = logger()

    override fun prepare(session: CqlSession) {
    }

    override fun schema(): List<String> {
        return listOf()
    }

    override fun getDefaultReadRate() = 0.0

    override fun getRunner(context: StressContext): IStressRunner {
        /**
         * We're going to be managing a bunch of tables here, and each one needs it's own prepared statements
         */
        data class Table(
            val name: String,
            var insert: PreparedStatement,
            var select: PreparedStatement,
            var delete: PreparedStatement,
        )

        var tableCount = AtomicInteger(0)

        var currentTables = ArrayDeque<Table>(activeTables * 2)

        // query fragments
        var fieldList = (0 until fields).map { "f$it" }
        var createFieldsWithType = fieldList.map { "$it text" }.joinToString(",")
        val fieldStr = fieldList.joinToString(", ")
        val placeholders = (0 until fields).map { "?" }.joinToString(", ")

        // whether or not to execute a DDL statement in the next iteration
        val timer = Timer()
        var latch = CountDownLatch(1)

        fun getRandomTable(): Table =
            if (currentTables.size == 1) {
                currentTables.first()
            } else {
                val randomIndex = 1 + kotlin.random.Random.nextInt(currentTables.size - 1)
                currentTables.elementAt(randomIndex)
            }

        logger.info("Scheduling DDL every $seconds seconds")
        timer.schedule(
            0L,
            seconds.toLong() * 1000,
        ) {
            if (currentTables.size < activeTables) {
                val next = tableCount.addAndGet(1)
                val name = "create_drop_${context.thread}_${context.mainArguments.id}_$next"
                try {
                    val query =
                        """CREATE TABLE IF NOT EXISTS $name ( id text, $createFieldsWithType, primary key (id) )"""
                    context.session.execute(query)

                    val insert = "INSERT INTO $name (id, $fieldStr) VALUES (?, $placeholders)"

                    val insertPrepared = context.session.prepare(insert)
                    val selectPrepared = context.session.prepare("SELECT * from $name WHERE id = ?")
                    val deletePrepared = context.session.prepare("DELETE from $name WHERE id = ?")

                    currentTables.addLast(
                        Table(
                            name,
                            insert = insertPrepared,
                            select = selectPrepared,
                            delete = deletePrepared,
                        ),
                    )
                } catch (e: Exception) {
                    logger.error(e)
                }
            } else {
                val name = currentTables.removeFirst()
                context.session.execute("DROP TABLE IF EXISTS ${name.name}")
            }
            latch.countDown()
        }
        latch.await()

        val field = context.registry.getGenerator("create_drop", "f")

        return object : IStressRunner {
            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                // consider removing this to be able to test concurrent schema modifications
                val table = getRandomTable()

                val boundValues = mutableListOf(partitionKey.getText())

                // placeholder for now
                for (i in 0 until fields) {
                    boundValues.add(field.getText())
                }

                // Build bound statement one parameter at a time in v4
                val bound = table.insert.bind()
                bound.setString(0, partitionKey.getText())
                for (i in 0 until fields) {
                    bound.setString(i + 1, field.getText())
                }
                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                return Operation.SelectStatement(
                    getRandomTable().select.bind()
                        .setString(0, partitionKey.getText()),
                )
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                return Operation.Deletion(
                    getRandomTable().delete.bind()
                        .setString(0, partitionKey.getText()),
                )
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        val field = FieldFactory("create_drop")
        return mapOf(
            field.getField("f") to
                Random().apply {
                    min = 10
                    max = 20
                },
        )
    }
}
