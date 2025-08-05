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
package com.rustyrazorblade.easycassstress.integration

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.net.InetSocketAddress

/**
 * Base class for Cassandra integration tests
 * Provides common setup logic for connection handling
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CassandraTestBase {
    companion object {
        // Connection parameters with environment variable fallbacks
        val ip = System.getenv("CASSANDRA_EASY_STRESS_CASSANDRA_IP") ?: "127.0.0.1"
        val localDc = System.getenv("CASSANDRA_EASY_STRESS_DATACENTER") ?: "datacenter1"

        // Configure driver with reasonable timeouts for tests
        val configLoader =
            DriverConfigLoader.programmaticBuilder()
                .withString(DefaultDriverOption.REQUEST_TIMEOUT, "30s")
                .withString(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, "10s")
                .withString(DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, "30s")
                .withString(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, "30s")
                .withString(DefaultDriverOption.CONNECTION_SET_KEYSPACE_TIMEOUT, "10s")
                .build()
    }

    // Instance-level connection for each test class
    protected lateinit var connection: CqlSession

    @BeforeAll
    fun setupClass() {
        // Create connection for this test class
        println("Connecting to Cassandra at $ip using datacenter $localDc")
        connection = CqlSession.builder()
            .addContactPoint(InetSocketAddress(ip, 9042))
            .withLocalDatacenter(localDc)
            .withConfigLoader(configLoader)
            .build()

        // Ensure keyspace doesn't exist before tests
        connection.execute("DROP KEYSPACE IF EXISTS easy_cass_stress")
    }

    @AfterAll
    fun teardownClass() {
        // Clean up resources after tests
        connection.close()
    }

    // Cleanup before each test case
    fun cleanupKeyspace() {
        connection.execute("DROP KEYSPACE IF EXISTS easy_cass_stress")
    }
}
