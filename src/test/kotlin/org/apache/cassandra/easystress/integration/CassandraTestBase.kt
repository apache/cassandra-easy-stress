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
package org.apache.cassandra.easystress.integration

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Base class for Cassandra integration tests.
 *
 * Provides common setup logic for connection handling, including:
 * - Automatic connection management with configurable timeouts
 * - Environment-based configuration for IP and datacenter
 * - Keyspace cleanup utilities for test isolation
 *
 * @property connection The CqlSession instance shared by all tests in the class
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CassandraTestBase {
    companion object {
        private val logger = LoggerFactory.getLogger(CassandraTestBase::class.java)

        // Configuration constants
        private const val DEFAULT_CASSANDRA_IP = "127.0.0.1"
        private const val DEFAULT_DATACENTER = "datacenter1"
        private const val DEFAULT_PORT = 9042
        private const val TEST_KEYSPACE = "easy_cass_stress"

        // Timeout constants
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val CONTROL_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val METADATA_SCHEMA_TIMEOUT_SECONDS = 30L
        private const val CONNECTION_INIT_TIMEOUT_SECONDS = 30L
        private const val KEYSPACE_TIMEOUT_SECONDS = 10L

        // Connection parameters with environment variable fallbacks
        val ip = System.getenv("CASSANDRA_EASY_STRESS_CASSANDRA_IP") ?: DEFAULT_CASSANDRA_IP
        val localDc = System.getenv("CASSANDRA_EASY_STRESS_DATACENTER") ?: DEFAULT_DATACENTER

        init {
            // Validate configuration on class load
            require(ip.isNotBlank()) { "Cassandra IP address cannot be blank" }
            require(localDc.isNotBlank()) { "Datacenter name cannot be blank" }
            logger.info("Test configuration: IP=$ip, Datacenter=$localDc")
        }

        /**
         * Configure driver with timeouts suitable for integration testing.
         * These values are optimized for test stability over performance.
         */
        val configLoader: DriverConfigLoader =
            DriverConfigLoader.programmaticBuilder()
                .withString(DefaultDriverOption.REQUEST_TIMEOUT, "${REQUEST_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, "${CONTROL_CONNECTION_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, "${METADATA_SCHEMA_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, "${CONNECTION_INIT_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONNECTION_SET_KEYSPACE_TIMEOUT, "${KEYSPACE_TIMEOUT_SECONDS}s")
                .build()
    }

    /**
     * The CQL session used by all tests in this test class.
     * Initialized in [setupClass] and closed in [teardownClass].
     */
    protected lateinit var connection: CqlSession

    /**
     * Sets up the test class by establishing a connection to Cassandra
     * and ensuring a clean state.
     *
     * @throws Exception if connection cannot be established
     */
    @BeforeAll
    fun setupClass() {
        logger.info("Setting up test class: connecting to Cassandra at {}:{} using datacenter {}", ip, DEFAULT_PORT, localDc)

        try {
            connection =
                CqlSession.builder()
                    .addContactPoint(InetSocketAddress(ip, DEFAULT_PORT))
                    .withLocalDatacenter(localDc)
                    .withConfigLoader(configLoader)
                    .build()

            logger.debug("Connection established successfully")

            // Verify connection is working by executing a simple query
            connection.execute("SELECT release_version FROM system.local")
            logger.debug("Connection verified successfully")

            // Ensure keyspace doesn't exist before tests
            cleanupKeyspace()
            logger.debug("Test keyspace cleaned up")
        } catch (e: Exception) {
            logger.error("Failed to establish connection to Cassandra", e)
            throw IllegalStateException(
                "Cannot connect to Cassandra at $ip:$DEFAULT_PORT. " +
                    "Ensure Cassandra is running and accessible.",
                e,
            )
        }
    }

    /**
     * Tears down the test class by closing the Cassandra connection.
     */
    @AfterAll
    fun teardownClass() {
        logger.info("Tearing down test class: closing Cassandra connection")

        try {
            if (::connection.isInitialized && !connection.isClosed) {
                connection.close()
                logger.debug("Connection closed successfully")
            }
        } catch (e: Exception) {
            logger.warn("Error while closing connection", e)
        }
    }

    /**
     * Removes the test keyspace to ensure test isolation.
     * This method should be called before each test to ensure a clean state.
     */
    protected fun cleanupKeyspace() {
        logger.debug("Cleaning up test keyspace: {}", TEST_KEYSPACE)
        connection.execute("DROP KEYSPACE IF EXISTS $TEST_KEYSPACE")
    }

    /**
     * Utility method to check if the test keyspace exists.
     *
     * @return true if the keyspace exists, false otherwise
     */
    protected fun keyspaceExists(): Boolean {
        val result =
            connection.execute(
                "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = ?",
                TEST_KEYSPACE,
            )
        return result.one() != null
    }

    /**
     * Utility method to get the current Cassandra version.
     *
     * @return the Cassandra release version string
     */
    protected fun getCassandraVersion(): String {
        val result = connection.execute("SELECT release_version FROM system.local")
        return result.one()?.getString("release_version") ?: "unknown"
    }
}
