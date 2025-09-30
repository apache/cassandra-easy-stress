package org.apache.cassandra.easystress.workloads

import com.datastax.oss.driver.api.core.CqlSession
import org.apache.cassandra.easystress.PartitionKey
import org.apache.cassandra.easystress.RequireAccord
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.WorkloadParameter

enum class Impl {
    /**
     * Lightweight Transactions - uses Cassandra's native conditional updates with IF EXISTS
     * to ensure atomicity of counter operations.
     */
    LWT,

    /**
     * Accord Transaction Protocol - uses Cassandra's newer transaction protocol designed
     * to handle distributed transactions with multiple key support and more flexible logic.
     */
    ACCORD,
}

/**
 * A stress profile that tests distributed counters using different transaction algorithms in Apache Cassandra.
 * This workload simulates concurrent increments of counter values using either Lightweight Transactions (LWT)
 * or Accord transaction protocol, allowing performance comparison between these transaction mechanisms.
 *
 * The test helps evaluate the consistency and performance characteristics of distributed counter operations
 * under high concurrency conditions.
 *
 * Example:
 * ```
 * # Run with LWT implementation (default)
 * cassandra-easy-stress run TxnCounter
 *
 * # Run with Accord implementation
 * cassandra-easy-stress run TxnCounter --workload.impl=ACCORD
 *
 * # Specify explicit reads with Accord implementation
 * cassandra-easy-stress run TxnCounter --workload.impl=ACCORD --workload.accordAutoRead=false
 *
 * # Add a custom postfix to table names
 * cassandra-easy-stress run TxnCounter --workload.postfix=test_run1
 * ```
 */
@RequireAccord
class TxnCounter : IStressWorkload {
    @WorkloadParameter("Which type of transaction system to use")
    var impl = Impl.LWT

    @WorkloadParameter("Be implicit or explicit about the update read; false means be explicit, true means be implicit")
    var accordAutoRead: Boolean = true

    @WorkloadParameter("A postfix added to the table name")
    var postfix: String = ""

    private fun name() = "tx_${impl.name.lowercase()}_counter${if (postfix.isEmpty()) "" else "_$postfix"}"

    override fun schema(): List<String> =
        listOf(
            """
            CREATE TABLE IF NOT EXISTS ${name()} (
                id TEXT PRIMARY KEY,
                value INT
            )
            ${if (impl == Impl.ACCORD) "WITH transactional_mode='full'" else ""}
            """.trimIndent(),
        )

    override fun prepare(session: CqlSession) {}

    override fun getRunner(context: StressContext): IStressRunner =
        when (impl) {
            Impl.LWT -> lwtRunner(context)
            Impl.ACCORD -> accordRunner(context)
        }

    private fun lwtRunner(context: StressContext): IStressRunner =
        object : IStressRunner {
            val prepareInsert =
                context.session.prepare(
                    """
                    INSERT INTO ${name()} (id, value) VALUES (?, ?) IF NOT EXISTS
                    """.trimIndent(),
                )
            val update =
                context.session.prepare(
                    """
                    UPDATE ${name()}
                    SET value = value + 1
                    WHERE id = ?
                    IF EXISTS
                    """.trimIndent(),
                )
            val select =
                context.session.prepare(
                    """
                    SELECT *
                    FROM ${name()}
                    WHERE id = ?
                    """.trimIndent(),
                )

            override fun getNextMutation(partitionKey: PartitionKey): Operation = Operation.Mutation(update.bind(partitionKey.getText()))

            override fun getNextPopulate(partitionKey: PartitionKey): Operation =
                Operation.Mutation(prepareInsert.bind(partitionKey.getText(), 0))

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val bind = select.bind(partitionKey.getText())
                bind.serialConsistencyLevel = context.mainArguments.serialConsistencyLevel
                return Operation.SelectStatement(bind)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                TODO("Counter test does not support delete")
            }
        }

    private fun accordRunner(context: StressContext): IStressRunner =
        object : IStressRunner {
            val prepareInsert =
                context.session.prepare(
                    """
                    BEGIN TRANSACTION
                        LET a = (SELECT * FROM ${name()} WHERE id = ?);
                        IF a IS NULL THEN
                            INSERT INTO ${name()} (id, value)
                            VALUES (?, 0);
                        END IF
                    COMMIT TRANSACTION
                    """.trimIndent(),
                )
            val update =
                context.session.prepare(
                    if (accordAutoRead) {
                        """
                        BEGIN TRANSACTION
                            UPDATE ${name()}
                              SET value += 1
                              WHERE id = ?;
                        COMMIT TRANSACTION
                        """.trimIndent()
                    } else {
                        """
                        BEGIN TRANSACTION
                            LET a = (SELECT * FROM ${name()} WHERE id = ?);
                            IF a IS NOT NULL THEN
                                UPDATE ${name()}
                                  SET value = a.value + 1
                                  WHERE id = ?;
                            END IF
                        COMMIT TRANSACTION
                        """.trimIndent()
                    },
                )
            val select =
                context.session.prepare(
                    """
                    BEGIN TRANSACTION
                        SELECT *
                          FROM ${name()}
                          WHERE id = ?;
                    COMMIT TRANSACTION
                    """.trimIndent(),
                )

            override fun getNextMutation(partitionKey: PartitionKey): Operation =
                Operation.Mutation(
                    if (accordAutoRead) {
                        update.bind(partitionKey.getText())
                    } else {
                        update.bind(partitionKey.getText(), partitionKey.getText())
                    },
                )

            override fun getNextPopulate(partitionKey: PartitionKey): Operation =
                Operation.Mutation(prepareInsert.bind(partitionKey.getText(), partitionKey.getText()))

            override fun getNextSelect(partitionKey: PartitionKey): Operation =
                Operation.SelectStatement(select.bind(partitionKey.getText()))

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                TODO("Counter test does not support delete")
            }
        }
}
