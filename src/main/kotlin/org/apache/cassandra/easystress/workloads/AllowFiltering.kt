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

class AllowFiltering : IStressWorkload {
    @WorkloadParameter(description = "Number of rows per partition")
    var rows = 100

    @WorkloadParameter(description = "Max Value of the value field.  Lower values will return more results.")
    var maxValue = 100

    lateinit var insert: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var delete: PreparedStatement

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO allow_filtering (partition_id, row_id, value, payload) values (?, ?, ?, ?)")
        select = session.prepare("SELECT * from allow_filtering WHERE partition_id = ? and value = ? ALLOW FILTERING")
        delete = session.prepare("DELETE from allow_filtering WHERE partition_id = ? and row_id = ?")
    }

    override fun schema(): List<String> =
        listOf(
            """CREATE TABLE IF NOT EXISTS allow_filtering (
            |partition_id text,
            |row_id int,
            |value int,
            |payload text,
            |primary key (partition_id, row_id)
            |) 
            """.trimMargin(),
        )

    override fun getRunner(context: StressContext): IStressRunner {
        val payload = context.registry.getGenerator("allow_filtering", "payload")
        val random = ThreadLocalRandom.current()

        return object : IStressRunner {
            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val rowId = random.nextInt(0, rows)
                val value = random.nextInt(0, maxValue)

                val bound =
                    insert
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, rowId)
                        .setInt(2, value)
                        .setString(3, payload.getText())
                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val value = random.nextInt(0, maxValue)
                val bound =
                    select
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, value)
                return Operation.SelectStatement(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val rowId = random.nextInt(0, rows)
                val bound =
                    delete
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, rowId)
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        val af = FieldFactory("allow_filtering")
        return mapOf(
            af.getField("payload") to
                Random().apply {
                    min = 0
                    max = 1
                },
        )
    }
}
