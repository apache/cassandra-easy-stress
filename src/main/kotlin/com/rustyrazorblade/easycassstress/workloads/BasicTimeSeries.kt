package com.rustyrazorblade.easycassstress.workloads

import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.uuid.Uuids
import java.time.Instant
import com.rustyrazorblade.easycassstress.PartitionKey
import com.rustyrazorblade.easycassstress.StressContext
import com.rustyrazorblade.easycassstress.WorkloadParameter
import com.rustyrazorblade.easycassstress.generators.Field
import com.rustyrazorblade.easycassstress.generators.FieldGenerator
import com.rustyrazorblade.easycassstress.generators.functions.Random
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Create a simple time series use case with some number of partitions
 * TODO make it use TWCS
 */
class BasicTimeSeries : IStressProfile {
    override fun schema(): List<String> {
        val query =
            """
            CREATE TABLE IF NOT EXISTS sensor_data (
            sensor_id text,
            timestamp timeuuid,
            data text,
            primary key(sensor_id, timestamp))
            WITH CLUSTERING ORDER BY (timestamp DESC)
            """.trimIndent()

        return listOf(query)
    }

    lateinit var prepared: PreparedStatement
    lateinit var getPartitionHead: PreparedStatement
    lateinit var delete: PreparedStatement
    // In v4, version checking is done differently
    var isCassandra3OrHigher = true

    @WorkloadParameter("Number of rows to fetch back on SELECT queries")
    var limit = 500

    @WorkloadParameter("Deletion range in seconds. Range tombstones will cover all rows older than the given value.")
    var deleteDepth = 30

    @WorkloadParameter("Insert TTL")
    var ttl = 0

    override fun prepare(session: CqlSession) {
        println("Using a limit of $limit for reads and deleting data older than $deleteDepth seconds (if enabled).")
        // In v4 driver, we assume Cassandra 3 compatibility since we're using the latest driver
        var ttlStr =
            if (ttl > 0) {
                " USING TTL $ttl"
            } else {
                ""
            }

        prepared = session.prepare("INSERT INTO sensor_data (sensor_id, timestamp, data) VALUES (?, ?, ?) $ttlStr")
        getPartitionHead = session.prepare("SELECT * from sensor_data WHERE sensor_id = ? LIMIT ?")
        
        // In v4, we assume Cassandra 3+ compatibility
        delete = session.prepare("DELETE from sensor_data WHERE sensor_id = ? and timestamp < maxTimeuuid(?)")
    }

    /**
     * need to fix custom arguments
     */
    override fun getRunner(context: StressContext): IStressRunner {
        val dataField = context.registry.getGenerator("sensor_data", "data")

        return object : IStressRunner {
            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bound = getPartitionHead.bind()
                    .setString(0, partitionKey.getText())
                    .setInt(1, limit)
                return Operation.SelectStatement(bound)
            }

            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val data = dataField.getText()
                val timestamp = Uuids.timeBased()
                val bound = prepared.bind()
                    .setString(0, partitionKey.getText())
                    .setUuid(1, timestamp)
                    .setString(2, data)
                return Operation.Mutation(bound)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                val deleteTime = LocalDateTime.now().minusSeconds(deleteDepth.toLong())
                val bound = delete.bind()
                    .setString(0, partitionKey.getText())
                    .setInstant(1, deleteTime.toInstant(java.time.ZoneOffset.UTC))
                return Operation.Deletion(bound)
            }
        }
    }

    override fun getFieldGenerators(): Map<Field, FieldGenerator> {
        return mapOf(
            Field("sensor_data", "data") to
                Random().apply {
                    min = 100
                    max = 200
                },
        )
    }
}
