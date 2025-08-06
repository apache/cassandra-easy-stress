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
package org.apache.cassandra.easystress

import org.apache.logging.log4j.kotlin.logger

fun main(argv: Array<String>) {
    val log = logger("main")

    log.info { "Parsing $argv" }
    val parser = CommandLineParser.parse(argv)

    try {
        parser.execute()
    } catch (e: Exception) {
        log.error { "Crashed with error: " + e.message }
        println(e.message)
        e.printStackTrace()
    } finally {
        // we exit here to kill the console thread otherwise it waits forever.
        // I'm sure a reasonable fix exists, but I don't have time to look into it.
        System.exit(0)
    }
}
