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
package org.apache.cassandra.easystress.converters

import com.beust.jcommander.IStringConverter
import com.datastax.oss.driver.api.core.ConsistencyLevel

class ConsistencyLevelConverter : IStringConverter<ConsistencyLevel> {
    override fun convert(value: String?): ConsistencyLevel {
        // In driver v4, valueOf is not directly accessible, using a safer approach
        return when (value?.uppercase()) {
            "ANY" -> ConsistencyLevel.ANY
            "ONE" -> ConsistencyLevel.ONE
            "TWO" -> ConsistencyLevel.TWO
            "THREE" -> ConsistencyLevel.THREE
            "QUORUM" -> ConsistencyLevel.QUORUM
            "ALL" -> ConsistencyLevel.ALL
            "LOCAL_ONE" -> ConsistencyLevel.LOCAL_ONE
            "LOCAL_QUORUM" -> ConsistencyLevel.LOCAL_QUORUM
            "EACH_QUORUM" -> ConsistencyLevel.EACH_QUORUM
            "LOCAL_SERIAL" -> ConsistencyLevel.LOCAL_SERIAL
            "SERIAL" -> ConsistencyLevel.SERIAL
            else -> throw IllegalArgumentException("Unknown consistency level: $value")
        }
    }
}
