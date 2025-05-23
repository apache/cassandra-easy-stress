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
package com.rustyrazorblade.easycassstress

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycassstress.commands.Fields
import com.rustyrazorblade.easycassstress.commands.IStressCommand
import com.rustyrazorblade.easycassstress.commands.Info
import com.rustyrazorblade.easycassstress.commands.ListCommand
import com.rustyrazorblade.easycassstress.commands.Run

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}

class CommandLineParser(
    val jCommander: JCommander,
    val commands: Map<String, IStressCommand>,
) {
    companion object {
        fun parse(arguments: Array<String>): CommandLineParser {
            // JCommander set up
            val jcommander = JCommander.newBuilder().programName("cassandra-easy-stress")
            val args = MainArgs()

            // needed to get help
            jcommander.addObject(args)
            // subcommands

            val commands =
                mapOf(
                    "run" to Run(arguments.joinToString(" ")),
                    "info" to Info(),
                    "list" to ListCommand(),
                    "fields" to Fields(),
                )

            for (x in commands.entries) {
                jcommander.addCommand(x.key, x.value)
            }

            val jc = jcommander.build()
            jc.parse(*arguments)

            if (jc.parsedCommand == null) {
                jc.usage()
                System.exit(0)
            }
            return CommandLineParser(jc, commands)
        }
    }

    fun execute() {
        getCommandInstance().execute()
    }

    fun getParsedCommand(): String {
        return jCommander.parsedCommand
    }

    fun getCommandInstance(): IStressCommand {
        return commands[getParsedCommand()]!!
    }
}
