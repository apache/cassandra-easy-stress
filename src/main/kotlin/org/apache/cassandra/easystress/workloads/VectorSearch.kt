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
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.data.CqlVector
import io.jhdf.HdfFile
import org.apache.cassandra.easystress.MinimumVersion
import org.apache.cassandra.easystress.PartitionKey
import org.apache.cassandra.easystress.StressContext
import org.apache.cassandra.easystress.WorkloadParameter
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Workload for benchmarking Vector Search (Cassandra 5.0+).
 * Supports both synthetic random vectors and realistic datasets via HDF5.
 *
 * ## Memory Considerations
 * When using HDF5 datasets, vectors are loaded entirely into memory.
 * Example memory requirements:
 * - SIFT-1M (1M vectors × 128 dims × 4 bytes) ≈ 512 MB
 * - GloVe-1M (1M vectors × 200 dims × 4 bytes) ≈ 800 MB
 * - Deep1B subset (10M vectors × 96 dims × 4 bytes) ≈ 3.8 GB
 *
 * Ensure sufficient heap space with -Xmx JVM flag.
 *
 * ## Ground Truth / Recall Calculation
 * When using standard ANN benchmark datasets (SIFT, GloVe, etc.) that include
 * ground truth neighbors, enable recall calculation to measure search quality:
 * - Set `calculateRecall=true`
 * - Ensure your HDF5 file contains a 'neighbors' dataset (or configure `neighborsDataset`)
 * - Recall@K is logged periodically based on `recallLogInterval`
 */
@MinimumVersion("5.0")
class VectorSearch : IStressWorkload {
    @WorkloadParameter(description = "Vector dimensions. Default 1536 (OpenAI).")
    var dimensions = 1536

    @WorkloadParameter(description = "Similarity function: COSINE, EUCLIDEAN, or DOT_PRODUCT.")
    var similarityFunction = "COSINE"

    @WorkloadParameter(description = "ANN search limit (TOP K).")
    var limit = 10

    @WorkloadParameter(description = "Path to HDF5 dataset file (e.g., glove.hdf5). If empty, uses random vectors.")
    var datasetPath: String = ""

    @WorkloadParameter(description = "Name of the HDF5 dataset for training data (inserts). Default 'train'.")
    var trainDataset = "train"

    @WorkloadParameter(description = "Name of the HDF5 dataset for query data (selects). Default 'test'.")
    var queryDataset = "test"

    @WorkloadParameter(description = "Name of the HDF5 dataset for ground truth neighbors. Default 'neighbors'.")
    var neighborsDataset = "neighbors"

    @WorkloadParameter(description = "Enable recall calculation (requires ground truth in HDF5). Default false.")
    var calculateRecall = false

    @WorkloadParameter(description = "How often to log recall summary (every N queries). Default 1000.")
    var recallLogInterval = 1000

    lateinit var insert: PreparedStatement
    lateinit var select: PreparedStatement
    lateinit var delete: PreparedStatement

    private lateinit var trainVectors: Array<FloatArray>
    private lateinit var queryVectors: Array<FloatArray>
    private lateinit var groundTruth: Array<IntArray>
    private var hdf5Loaded = false
    private var hasGroundTruth = false

    // Recall tracking
    private val queryCount = AtomicLong(0)
    private val totalRecall = AtomicLong(0)
    private val minRecall = AtomicInteger(Int.MAX_VALUE)
    private val maxRecall = AtomicInteger(Int.MIN_VALUE)

    // Sequential counter for inserting training vectors in order
    private val insertCounter = AtomicLong(0)

    // Track which training indices have been inserted (for recall calculation)
    private val insertedIndices = ConcurrentHashMap.newKeySet<Int>()

    val log = logger()

    override fun prepare(session: CqlSession) {
        val validFunctions = listOf("COSINE", "EUCLIDEAN", "DOT_PRODUCT")
        require(similarityFunction.uppercase() in validFunctions) {
            "similarityFunction must be one of: $validFunctions (got: $similarityFunction)"
        }

        // Validate limit ('K' in top 1 <= K <= 1000)
        require(limit in 1..1000) {
            "limit must be between 1 and 1000 (got: $limit)"
        }

        log.info { "Preparing VectorSearch workload. Dimensions: $dimensions, Similarity: $similarityFunction" }

        if (datasetPath.isNotEmpty() && !hdf5Loaded) {
            loadHdf5Data()
        }

        insert = session.prepare("INSERT INTO vector_test (id, val) VALUES (?, ?)")
        select = session.prepare("SELECT id FROM vector_test ORDER BY val ANN OF ? LIMIT ?")
        delete = session.prepare("DELETE FROM vector_test WHERE id = ?")
    }

    private fun loadHdf5Data() {
        val file = File(datasetPath)
        if (!file.exists()) {
            throw IllegalArgumentException("Dataset file not found: $datasetPath")
        }

        log.info { "Loading vectors from HDF5: $datasetPath" }
        HdfFile(Paths.get(datasetPath)).use { hdf ->
            // Inserts
            val trainData = hdf.getDatasetByPath(trainDataset).data
            trainVectors = convertToFloatArray(trainData)
            log.info { "Loaded ${trainVectors.size} training vectors." }

            // Selects
            val queryData = hdf.getDatasetByPath(queryDataset).data
            queryVectors = convertToFloatArray(queryData)
            log.info { "Loaded ${queryVectors.size} query vectors." }

            // Validate dimensions
            if (trainVectors.isNotEmpty() && trainVectors[0].size != dimensions) {
                log.warn {
                    "Dataset dimensions (${trainVectors[0].size}) do not match configured dimensions ($dimensions). Updating."
                }
                dimensions = trainVectors[0].size
            }

            // If recall calculation is enabled, ground truth values are required
            if (calculateRecall) {
                try {
                    val neighborsData = hdf.getDatasetByPath(neighborsDataset).data
                    groundTruth = convertToIntArray(neighborsData)
                    hasGroundTruth = true
                    log.info { "Loaded ground truth with ${groundTruth.size} query neighbors." }
                } catch (e: Exception) {
                    log.warn { "Ground truth dataset '$neighborsDataset' not found. Recall calculation disabled." }
                    calculateRecall = false
                }
            }

            hdf5Loaded = true
        }
    }

    override fun schema(): List<String> {
        if (datasetPath.isNotEmpty() && !hdf5Loaded) {
            loadHdf5Data()
        }

        return listOf(
            """
            CREATE TABLE IF NOT EXISTS vector_test (
                id text PRIMARY KEY,
                val vector<float, $dimensions>
            )
            """.trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS ann_index ON vector_test(val)
            USING 'sai'
            WITH OPTIONS = {'similarity_function': '$similarityFunction'}
            """.trimIndent(),
        )
    }

    override fun getDefaultReadRate(): Double = 0.5

    override fun getRunner(context: StressContext): IStressRunner {
        return object : IStressRunner {
            override fun getNextMutation(partitionKey: PartitionKey): Operation {
                val vector: CqlVector<Float>
                val id: String

                if (hdf5Loaded) {
                    val trainIdx = (insertCounter.getAndIncrement() % trainVectors.size).toInt()
                    vector = CqlVector.newInstance(trainVectors[trainIdx].toList())
                    id = trainIdx.toString()
                    insertedIndices.add(trainIdx)
                } else {
                    vector = generateRandomVector(dimensions)
                    id = partitionKey.getText()
                }

                val bound =
                    insert
                        .bind()
                        .setString(0, id)
                        .setVector(1, vector, Float::class.javaObjectType)

                return Operation.Mutation(bound)
            }

            override fun getNextSelect(partitionKey: PartitionKey): Operation {
                val vector: CqlVector<Float>
                var queryIdx: Int? = null

                if (hdf5Loaded) {
                    queryIdx = ThreadLocalRandom.current().nextInt(queryVectors.size)
                    vector = CqlVector.newInstance(queryVectors[queryIdx].toList())
                } else {
                    vector = generateRandomVector(dimensions)
                }

                val bound =
                    select
                        .bind()
                        .setVector(0, vector, Float::class.javaObjectType)
                        .setInt(1, limit)

                // Pass query index as callback payload for recall calculation
                val payload =
                    if (calculateRecall && hasGroundTruth && queryIdx != null) {
                        RecallPayload(queryIdx)
                    } else {
                        null
                    }

                return Operation.SelectStatement(bound, payload)
            }

            override fun getNextDelete(partitionKey: PartitionKey): Operation {
                // No need to track deletes for recall calculation since we use training indices as IDs
                val bound = delete.bind().setString(0, partitionKey.getText())
                return Operation.Deletion(bound)
            }

            override fun onSuccess(
                op: Operation.SelectStatement,
                result: AsyncResultSet?,
            ) {
                if (result == null) return
                val payload = op.callbackPayload as? RecallPayload ?: return

                // Parse returned IDs as training indices
                val returnedIndices = mutableSetOf<Int>()
                for (row in result.currentPage()) {
                    val id = row.getString("id") ?: continue
                    id.toIntOrNull()?.let { returnedIndices.add(it) }
                }

                // take top K from ground truth for recall calculation
                val truthIndices = groundTruth[payload.queryIndex].take(limit).toSet()

                // Only count neighbors that have actually been inserted
                val relevantTruth = truthIndices.intersect(insertedIndices)

                // number of relevant items successfully retrieved
                val hits = returnedIndices.intersect(truthIndices).size
                val denominator = minOf(limit, relevantTruth.size).coerceAtLeast(1)
                val recall = hits.toDouble() / denominator

                val recallFixed = (recall * 10000).toInt()
                totalRecall.addAndGet(recallFixed.toLong())
                minRecall.updateAndGet { min -> minOf(min, recallFixed) }
                maxRecall.updateAndGet { max -> maxOf(max, recallFixed) }

                val count = queryCount.incrementAndGet()

                // Log periodic summary
                if (count % recallLogInterval == 0L) {
                    val avgRecall = totalRecall.get().toDouble() / count / 10000
                    val minR = minRecall.get().toDouble() / 10000
                    val maxR = maxRecall.get().toDouble() / 10000
                    log.info {
                        "Recall@$limit after $count queries: avg=%.3f, min=%.2f, max=%.2f (indexed: ${insertedIndices.size})".format(
                            avgRecall,
                            minR,
                            maxR,
                        )
                    }
                }
            }
        }
    }

    private fun generateRandomVector(dim: Int): CqlVector<Float> {
        val rand = ThreadLocalRandom.current()
        val list = ArrayList<Float>(dim)
        for (i in 0 until dim) {
            list.add(rand.nextFloat())
        }
        return CqlVector.newInstance(list)
    }

    private data class RecallPayload(
        val queryIndex: Int,
    )

    companion object {
        /**
         * Converts HDF5 numeric array data to Array<FloatArray>.
         * Handles both float[][] and double[][] formats commonly found in vector datasets.
         */
        fun convertToFloatArray(data: Any): Array<FloatArray> {
            return when (data) {
                is Array<*> -> {
                    if (data.isArrayOf<FloatArray>()) {
                        @Suppress("UNCHECKED_CAST")
                        return data as Array<FloatArray>
                    }
                    if (data.isArrayOf<DoubleArray>()) {
                        @Suppress("UNCHECKED_CAST")
                        val doubleArray = data as Array<DoubleArray>
                        return Array(doubleArray.size) { i ->
                            FloatArray(doubleArray[i].size) { j -> doubleArray[i][j].toFloat() }
                        }
                    }
                    throw IllegalArgumentException("Unsupported array type in HDF5: ${data::class.java}")
                }

                else -> throw IllegalArgumentException("Unsupported data format in HDF5: ${data::class.java}")
            }
        }

        /**
         * Converts HDF5 integer array data to Array<IntArray>.
         * Used for ground truth neighbor indices.
         */
        fun convertToIntArray(data: Any): Array<IntArray> {
            return when (data) {
                is Array<*> -> {
                    if (data.isArrayOf<IntArray>()) {
                        @Suppress("UNCHECKED_CAST")
                        return data as Array<IntArray>
                    }
                    if (data.isArrayOf<LongArray>()) {
                        @Suppress("UNCHECKED_CAST")
                        val longArray = data as Array<LongArray>
                        return Array(longArray.size) { i ->
                            IntArray(longArray[i].size) { j -> longArray[i][j].toInt() }
                        }
                    }
                    throw IllegalArgumentException("Unsupported array type for neighbors: ${data::class.java}")
                }

                else -> throw IllegalArgumentException("Unsupported data format for neighbors: ${data::class.java}")
            }
        }
    }
}
