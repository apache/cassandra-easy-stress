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
import com.datastax.oss.driver.api.core.uuid.Uuids
import org.apache.cassandra.easystress.PartitionKey
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.WorkloadParameter
import org.apache.cassandra.easystress.generators.Field
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.Random

/**
 * Create a simple time series use case with some number of partitions
 * TODO make it use TWCS
 */
class UdtTimeSeries : IStressWorkload {
    override fun schema(): List<String> {
        val queryUdt =
            """
            CREATE TYPE IF NOT EXISTS sensor_data_details (
              data1 text,
              data2 text,
              data3 text
            )
            """.trimIndent()

        val queryTable =
            """
            CREATE TABLE IF NOT EXISTS sensor_data_udt (
            sensor_id text,
            timestamp timeuuid,
            data frozen<sensor_data_details>,
            primary key(sensor_id, timestamp))
            WITH CLUSTERING ORDER BY (timestamp DESC)
            """.trimIndent()

        return listOf(queryUdt, queryTable)
    }

    lateinit var insert: PreparedStatement
    lateinit var getPartitionHead: PreparedStatement
    lateinit var deletePartitionHead: PreparedStatement

    @WorkloadParameter("Limit select to N rows.")
    var limit = 500

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO sensor_data_udt (sensor_id, timestamp, data) VALUES (?, ?, ?)")
        getPartitionHead = session.prepare("SELECT * from sensor_data_udt WHERE sensor_id = ? LIMIT ?")
        deletePartitionHead = session.prepare("DELETE from sensor_data_udt WHERE sensor_id = ?")
    }

    /**
     * need to fix custom arguments
     */
    override fun getRunner(context: StressContext): IStressRunner {
        val dataField = context.registry.getGenerator("sensor_data", "data")

        return object : IStressRunner {
            val keyspace = context.session.getKeyspace().orElse(null) ?: throw RuntimeException("No keyspace selected")
            val udt =
                context.session
                    .getMetadata()
                    .getKeyspace(keyspace)
                    .flatMap {
                        it.getUserDefinedType("sensor_data_details")
                    }.orElseThrow { RuntimeException("UDT not found") }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound =
                    getPartitionHead
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, limit)
                return Operation.SelectStatement(bound)
            }

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val data = dataField.getText()
                val chunks = data.chunked(data.length / 3)
                val udtValue =
                    udt
                        .newValue()
                        .setString("data1", chunks[0])
                        .setString("data2", chunks[1])
                        .setString("data3", chunks[2])
                val timestamp = Uuids.timeBased()
                val bound =
                    insert
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setUuid(1, timestamp)
                        .setUdtValue(2, udtValue)
                return Operation.Mutation(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val bound =
                    deletePartitionHead
                        .bind()
                        .setString(0, partitionKey.getText())
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> =
        mapOf(
            Field("sensor_data", "data") to
                Random().apply {
                    min = 100
                    max = 200
                },
        )
}
