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
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.Random

class Sets : IStressWorkload {
    lateinit var insert: PreparedStatement
    lateinit var update: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var deleteElement: PreparedStatement

    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO sets (key, values) VALUES (?, ?)")
        update = session.prepare("UPDATE sets SET values = values + ? WHERE key = ?")
        select = session.prepare("SELECT * from sets WHERE key = ?")
        deleteElement = session.prepare("UPDATE sets SET values = values - ? WHERE key = ?")
    }

    override fun schema(): List<String> {
        return listOf(
            """
            CREATE TABLE IF NOT EXISTS sets (
            |key text primary key,
            |values set<text>
            |)
            """.trimMargin(),
        )
    }

    override fun getRunner(context: StressContext): IStressRunner {
        val payload = context.registry.getGenerator("sets", "values")

        return object : IStressRunner {
            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val value = payload.getText()
                val valueSet = java.util.HashSet<String>()
                valueSet.add(value)

                // Create a simple statement for now - we'll use direct binding
                // The driver v4 has different ways of setting collections
                val bound = update.bind()
                bound.set(0, valueSet, java.util.HashSet::class.java)
                bound.setString(1, partitionKey.getText())

                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound =
                    select.bind()
                        .setString(0, partitionKey.getText())
                return Operation.SelectStatement(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val valueSet = java.util.HashSet<String>()
                valueSet.add(partitionKey.getText())

                // Create a simple statement for now - we'll use direct binding
                val bound = deleteElement.bind()
                bound.set(0, valueSet, java.util.HashSet::class.java)
                bound.setString(1, partitionKey.getText())
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        return mapOf(
            Field("sets", "values") to
                Random().apply {
                    min = 6
                    max = 16
                },
        )
    }
}
