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

/**
 * Accepts a human readable row count, or "all" (or -1, as MCP passes it) to populate every partition once.
 *
 * "all" can't be resolved here because it depends on --partitions and --populate.batch,
 * so it becomes [ALL] and Run resolves it.
 */
class PopulateRowsConverter : IStringConverter<Long> {
    companion object {
        const val ALL = -1L
    }

    // HumanReadableConverter drops the sign, so -1 is matched before it gets there.
    override fun convert(value: String?): Long =
        if (value.equals("all", ignoreCase = true) || value?.trim() == ALL.toString()) {
            ALL
        } else {
            HumanReadableConverter().convert(value)
        }
}
