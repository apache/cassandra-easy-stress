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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.time.Duration

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
        private const val DEFAULT_DATACENTER = "datacenter1"
        private const val DEFAULT_CASSANDRA_VERSION = "5.0"
        private const val CASSANDRA_PORT = 9042
        private const val TEST_KEYSPACE = "cassandra_easy_stress_tests"

        // Timeout constants
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val CONTROL_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val METADATA_SCHEMA_TIMEOUT_SECONDS = 30L
        private const val CONNECTION_INIT_TIMEOUT_SECONDS = 30L
        private const val KEYSPACE_TIMEOUT_SECONDS = 10L

        // Container startup timeout (in seconds)
        private const val CONTAINER_STARTUP_TIMEOUT_SECONDS = 120L

        /**
         * Configure driver with timeouts suitable for integration testing.
         * These values are optimized for test stability over performance.
         */
        fun createConfigLoader(): DriverConfigLoader =
            DriverConfigLoader.programmaticBuilder()
                .withString(DefaultDriverOption.REQUEST_TIMEOUT, "${REQUEST_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, "${CONTROL_CONNECTION_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, "${METADATA_SCHEMA_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, "${CONNECTION_INIT_TIMEOUT_SECONDS}s")
                .withString(DefaultDriverOption.CONNECTION_SET_KEYSPACE_TIMEOUT, "${KEYSPACE_TIMEOUT_SECONDS}s")
                .build()
    }

    /**
     * Cassandra version to test against.
     * Can be overridden via CASSANDRA_VERSION environment variable.
     */
    private val cassandraVersion: String =
        System.getenv("CASSANDRA_VERSION") ?: DEFAULT_CASSANDRA_VERSION

    /**
     * Testcontainers Cassandra container instance.
     * Built from custom Dockerfiles that enable experimental features.
     */
    private lateinit var cassandraContainer: GenericContainer<*>

    /**
     * Connection details from the running container
     */
    protected lateinit var ip: String
    protected var port: Int = 0
    protected lateinit var localDc: String

    /**
     * The CQL session used by all tests in this test class.
     * Initialized in [setupClass] and closed in [teardownClass].
     */
    protected lateinit var connection: CqlSession

    /**
     * Sets up the test class by starting a Cassandra container
     * and establishing a connection to it.
     *
     * @throws Exception if container cannot start or connection cannot be established
     */
    @BeforeAll
    fun setupClass() {
        logger.info("Setting up test class: starting Cassandra {} container", cassandraVersion)

        try {
            // Build container from custom Dockerfile
            val dockerfilePath = Paths.get("docker/cassandra-$cassandraVersion")
            logger.debug("Building image from {}", dockerfilePath)

            cassandraContainer =
                GenericContainer(
                    ImageFromDockerfile()
                        .withDockerfile(dockerfilePath.resolve("Dockerfile")),
                )
                    .withExposedPorts(CASSANDRA_PORT)
                    .waitingFor(
                        Wait.forLogMessage(".*Startup complete.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_SECONDS)),
                    )
                    .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_SECONDS))

            // Start the container
            logger.info("Starting Cassandra container...")
            cassandraContainer.start()
            logger.info("Cassandra container started successfully")

            // Get connection details from container
            ip = cassandraContainer.host
            port = cassandraContainer.getMappedPort(CASSANDRA_PORT)
            localDc = DEFAULT_DATACENTER

            logger.info("Container connection details: {}:{}, datacenter: {}", ip, port, localDc)

            // Give Cassandra a bit more time to fully initialize
            logger.debug("Waiting for Cassandra to be fully ready...")
            Thread.sleep(5000)
            logger.debug("Wait complete, attempting connection")

            // Establish connection with retry
            var lastException: Exception? = null
            for (attempt in 1..3) {
                try {
                    logger.debug("Connection attempt {}/3", attempt)
                    connection =
                        CqlSession.builder()
                            .addContactPoint(InetSocketAddress(ip, port))
                            .withLocalDatacenter(localDc)
                            .withConfigLoader(createConfigLoader())
                            .build()

                    logger.debug("Connection established successfully")

                    // Verify connection is working by executing a simple query
                    connection.execute("SELECT release_version FROM system.local")
                    logger.debug("Connection verified successfully")

                    // Ensure keyspace doesn't exist before tests
                    cleanupKeyspace()
                    logger.debug("Test keyspace cleaned up")

                    return // Success!
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Connection attempt {} failed: {}", attempt, e.message)
                    if (attempt < 3) {
                        Thread.sleep(5000)
                    }
                }
            }

            // All retries failed
            logger.error("Failed to connect after 3 attempts. Container logs:")
            logger.error(cassandraContainer.logs)
            throw IllegalStateException(
                "Cannot connect to Cassandra $cassandraVersion container at $ip:$port. " +
                    "Container started but Cassandra is not accepting connections.",
                lastException,
            )
        } catch (e: Exception) {
            logger.error("Failed to start Cassandra container", e)
            if (::cassandraContainer.isInitialized) {
                logger.error("Container logs:\n{}", cassandraContainer.logs)
            }
            throw IllegalStateException(
                "Cannot start Cassandra $cassandraVersion container. " +
                    "Ensure Docker is running and the Dockerfile exists at docker/cassandra-$cassandraVersion/Dockerfile.",
                e,
            )
        }
    }

    /**
     * Tears down the test class by closing the Cassandra connection
     * and stopping the container.
     */
    @AfterAll
    fun teardownClass() {
        logger.info("Tearing down test class: closing connection and stopping container")

        try {
            if (::connection.isInitialized && !connection.isClosed) {
                connection.close()
                logger.debug("Connection closed successfully")
            }
        } catch (e: Exception) {
            logger.warn("Error while closing connection", e)
        }

        try {
            if (::cassandraContainer.isInitialized) {
                cassandraContainer.stop()
                logger.info("Cassandra container stopped successfully")
            }
        } catch (e: Exception) {
            logger.warn("Error while stopping container", e)
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
