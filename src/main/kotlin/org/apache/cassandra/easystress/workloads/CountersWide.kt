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
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToLong

class CountersWide : IStressWorkload {
    lateinit var increment: PreparedStatement
    lateinit var selectOne: PreparedStatement
    lateinit var selectAll: PreparedStatement
    lateinit var deleteOne: PreparedStatement

    @WorkloadParameter("Total rows per partition.")
    var rowsPerPartition = 10000

    override fun prepare(session: CqlSession) {
        increment = session.prepare("UPDATE counter_wide SET value = value + 1 WHERE key = ? and cluster = ?")
        selectOne = session.prepare("SELECT * from counter_wide WHERE key = ? AND cluster = ?")
        selectAll = session.prepare("SELECT * from counter_wide WHERE key = ?")
        deleteOne = session.prepare("DELETE from counter_wide WHERE key = ? AND cluster = ?")
    }

    override fun schema(): List<String> =
        listOf(
            """CREATE TABLE IF NOT EXISTS counter_wide (
            | key text,
            | cluster bigint,
            | value counter,
            | primary key(key, cluster))
            """.trimMargin(),
        )

    override fun getRunner(context: StressContext): IStressRunner {
        // for now i'm just going to hardcode this at 10K items
        // later when a profile can accept dynamic parameters i'll make it configurable

        return object : IStressRunner {
            var iterations = 0L

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val clusteringKey = (ThreadLocalRandom.current().nextGaussian() * rowsPerPartition.toDouble()).roundToLong()
                val tmp =
                    increment
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setLong(1, clusteringKey)
                return Operation.Mutation(tmp)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                iterations++

                if (iterations % 2 == 0L) {
                    val clusteringKey = (ThreadLocalRandom.current().nextGaussian() * rowsPerPartition.toDouble()).roundToLong()
                    return Operation.SelectStatement(
                        selectOne
                            .bind()
                            .setString(0, partitionKey.getText())
                            .setLong(1, clusteringKey),
                    )
                }

                return Operation.SelectStatement(
                    selectAll
                        .bind()
                        .setString(0, partitionKey.getText()),
                )
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val clusteringKey = (ThreadLocalRandom.current().nextGaussian() * rowsPerPartition.toDouble()).roundToLong()
                return Operation.Deletion(
                    deleteOne
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setLong(1, clusteringKey),
                )
            }
        }
    }
}
