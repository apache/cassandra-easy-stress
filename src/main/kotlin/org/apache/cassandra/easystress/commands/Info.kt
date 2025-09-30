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
package org.apache.cassandra.easystress.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import org.apache.cassandra.easystress.Workload

@Parameters(commandDescription = "Get details of a specific workload.")
class Info : IStressCommand {
    @Parameter(required = true)
    var profile = ""

    override fun execute() {
        // Description
        // Schema
        // Field options
        val workload = Workload.getWorkloads().get(profile)!!

        for (cql in workload.instance.schema()) {
            println(cql)
        }

        println("Default read rate: ${workload.instance.getDefaultReadRate()} (override with -r)\n")

        val params = workload.getCustomParams()

        if (params.size > 0) {
            println("Dynamic workload parameters (override with --workload.name=X)\n")
            // TODO: Show dynamic parameters

            val cols = arrayOf(0, 0, 0)
            cols[0] = params.map { it.name.length }.maxOrNull() ?: 0 + 1
            cols[1] = params.map { it.description.length }.maxOrNull() ?: 0 + 1
            cols[2] = params.map { it.type.length }.maxOrNull() ?: 0 + 1

            with(TermColors()) {
                println(
                    "${underline(
                        "Name".padEnd(cols[0]),
                    )} | ${underline("Description".padEnd(cols[1]))} | ${underline("Type".padEnd(cols[2]))}",
                )
            }

            for (row in params) {
                println("${row.name.padEnd(cols[0])} | ${row.description.padEnd(cols[1])} | ${row.type.padEnd(cols[2])}")
            }
        } else {
            println("No dynamic workload parameters.")
        }
    }
}
