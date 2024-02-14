package com.rustyrazorblade.easycassstress

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PartitionKeyGeneratorTest {
    @Test
    fun basicKeyGenerationTest() {
        val p = PartitionKeyGenerator.random("test")
        val tmp = p.generateKey(1000000)
        val pk = tmp.take(1).toList()[0]
        assertThat(pk.getText()).contains("test")
    }

    @Test
    fun sequenceTest() {
        val p = PartitionKeyGenerator.sequence("test")
        val result = p.generateKey(10, 10).first()
        assertThat(result.id).isEqualTo(0L)


    }

    @Test
    fun testRepeatingSequence() {
        val p = PartitionKeyGenerator.sequence("test")
        val data = p.generateKey(10,2).take(5).toList().map { it.id.toInt() }
        assertThat(data).isEqualTo(listOf(0,1,2,0,1))

    }

    @Test
    fun testNormal() {
        val p = PartitionKeyGenerator.normal("test")
        for (x in p.generateKey(1000, 1000)) {

        }

    }
}