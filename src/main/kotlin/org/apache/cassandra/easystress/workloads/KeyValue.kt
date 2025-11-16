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
import org.apache.cassandra.easystress.generators.Field
import org.apache.cassandra.easystress.generators.FieldFactory
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.Random

class KeyValue : IStressWorkload {
    lateinit var insert: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var delete: PreparedStatement

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO keyvalue (key, value) VALUES (?, ?)")
        select = session.prepare("SELECT * from keyvalue WHERE key = ?")
        delete = session.prepare("DELETE from keyvalue WHERE key = ?")
    }

    override fun schema(): List<String> {
        val table =
            """
            CREATE TABLE IF NOT EXISTS keyvalue (
            key text PRIMARY KEY,
            value text
            )
            """.trimIndent()
        return listOf(table)
    }

    override fun getDefaultReadRate(): Double = 0.5

    override fun getRunner(context: StressContext): IStressRunner {
        val value = context.registry.getGenerator("keyvalue", "value")

        return object : IStressRunner {
            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound = select.bind().setString(0, partitionKey.getText())
                return Operation.SelectStatement(bound)
            }

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val data = value.getText()
                val bound =
                    insert
                        .bind()
                        .setString(0, partitionKey.getText())
                        .setString(1, data)

                return Operation.Mutation(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val bound = delete.bind().setString(0, partitionKey.getText())
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        val kv = FieldFactory("keyvalue")
        return mapOf(
            kv.getField("value") to
                Random().apply {
                    min = 100
                    max = 200
                },
        )
    }
}
