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
import org.apache.cassandra.easystress.generators.Field
import org.apache.cassandra.easystress.generators.FieldFactory
import org.apache.cassandra.easystress.generators.FieldGenerator
import org.apache.cassandra.easystress.generators.functions.FirstName
import org.apache.cassandra.easystress.generators.functions.LastName
import org.apache.cassandra.easystress.generators.functions.USCities
import java.util.concurrent.ThreadLocalRandom

@MinimumVersion("5.0")
class MaterializedViews : IStressWorkload {
    override fun prepare(session: CqlSession) {
        insert = session.prepare("INSERT INTO person (name, age, city) values (?, ?, ?)")
        selectBase = session.prepare("SELECT * FROM person WHERE name = ?")
        selectByAge = session.prepare("SELECT * FROM person_by_age WHERE age = ?")
        selectByCity = session.prepare("SELECT * FROM person_by_city WHERE city = ?")
        deleteBase = session.prepare("DELETE FROM person WHERE name = ?")
    }

    override fun schema(): List<String> =
        listOf(
            """CREATE TABLE IF NOT EXISTS person
                        | (name text, age int, city text, primary key(name))
            """.trimMargin(),
            """CREATE MATERIALIZED VIEW IF NOT EXISTS person_by_age AS
                            |SELECT age, name, city FROM person
                            |WHERE age IS NOT NULL AND name IS NOT NULL
                            |PRIMARY KEY (age, name)
            """.trimMargin(),
            """CREATE MATERIALIZED VIEW IF NOT EXISTS person_by_city AS
                            |SELECT city, name, age FROM person
                            |WHERE city IS NOT NULL AND name IS NOT NULL
                            |PRIMARY KEY (city, name) 
            """.trimMargin(),
        )

    override fun getRunner(context: StressContext): IStressRunner {
        return object : IStressRunner {
            var selectCount = 0L

            val cities = context.registry.getGenerator("person", "city")

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val num = ThreadLocalRandom.current().nextInt(1, 110)
                return Operation.Mutation(
                    insert.bind()
                        .setString(0, partitionKey.getText())
                        .setInt(1, num)
                        .setString(2, cities.getText()),
                )
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val num = ThreadLocalRandom.current().nextInt(1, 110)
                val result =
                    when (selectCount % 2L) {
                        0L ->
                            Operation.SelectStatement(
                                selectByAge.bind()
                                    .setInt(0, num),
                            )
                        else ->
                            Operation.SelectStatement(
                                selectByCity.bind()
                                    .setString(0, "test"),
                            )
                    }
                selectCount++
                return result
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                return Operation.Deletion(
                    deleteBase.bind()
                        .setString(0, partitionKey.getText()),
                )
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        val person = FieldFactory("person")
        return mapOf(
            person.getField("firstname") to FirstName(),
            person.getField("lastname") to LastName(),
            person.getField("city") to USCities(),
        )
    }

    lateinit var insert: PreparedStatement
    lateinit var selectBase: PreparedStatement
    lateinit var selectByAge: PreparedStatement
    lateinit var selectByCity: PreparedStatement
    lateinit var deleteBase: PreparedStatement
}
