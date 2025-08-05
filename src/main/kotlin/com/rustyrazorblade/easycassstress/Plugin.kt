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
package com.rustyrazorblade.easycassstress

import com.rustyrazorblade.easycassstress.workloads.IStressProfile
import org.apache.logging.log4j.kotlin.logger
import org.reflections.Reflections
import java.util.Optional
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType

/**
 * Wrapper for Stress Profile Plugins
 * Anything found in the class path will be returned.
 * TODO: Add a caching layer to prevent absurdly slow
 * reflection time
 */

data class Plugin(
    val name: String,
    val cls: Class<out IStressProfile>,
    val instance: IStressProfile,
) {
    data class WorkloadParameterType(
        val name: String,
        val description: String,
        val type: String,
    )

    override fun toString() = name

    companion object {
        val log = logger()

        fun getPlugins(): Map<String, Plugin> {
            val r = Reflections("com.rustyrazorblade.easycassstress")
            val modules = r.getSubTypesOf(IStressProfile::class.java)

            var result = sortedMapOf<String, Plugin>()

            for (m in modules) {
                val instance = m.getConstructor().newInstance()
//                val args = instance.getArguments()
                val tmp = Plugin(m.simpleName, m, instance)
                result[m.simpleName] = tmp
            }

            return result
        }

        /**
         * Returns plugins suitable for testing, filtering out those requiring
         * special environments unless corresponding environment variables are set.
         */
        fun getPluginsForTesting(envVars: Map<String, String> = System.getenv()): Map<String, Plugin> {
            val allPlugins = getPlugins()
            val testDSE = envVars["TEST_DSE"] == "1"
            val testMVs = envVars["TEST_MVS"] == "1"
            val testAccord = envVars["TEST_ACCORD"] == "1"

            return allPlugins.filterValues { plugin ->
                // Check if the plugin class has @RequireDSE, @RequireMVs, or @RequireAccord annotations
                val requiresDSE = plugin.cls.isAnnotationPresent(RequireDSE::class.java)
                val requiresMVs = plugin.cls.isAnnotationPresent(RequireMVs::class.java)
                val requiresAccord = plugin.cls.isAnnotationPresent(RequireAccord::class.java)

                // Include the plugin if:
                // - It doesn't require DSE, MVs, or Accord, OR
                // - It requires DSE AND TEST_DSE is set, OR
                // - It requires MVs AND TEST_MVS is set, OR
                // - It requires Accord AND TEST_ACCORD is set to "1"
                (!requiresDSE || testDSE) && (!requiresMVs || testMVs) && (!requiresAccord || testAccord)
            }
        }
    }

    /**
     * Takes the parameters passed in via the dynamic --workload. flag
     * and assigns the values to the instance
     */
    fun applyDynamicSettings(workloadParameters: Map<String, String>) {
        for ((key, value) in workloadParameters) {
            var prop = getProperty(key) as KMutableProperty<*>
            val annotation = prop.findAnnotation<WorkloadParameter>()
            log.debug("Annotation for $key found: $annotation")

            // Int
            if (prop.returnType.isSubtypeOf(Int::class.createType())) {
                log.debug("Found the type, we have an int, setting the value")
                prop.setter.call(instance, value.toInt())
                continue
            }

            // String
            if (prop.returnType.isSubtypeOf(String::class.createType())) {
                log.debug("Found the type, we have a String, setting the value")
                prop.setter.call(instance, value)
                continue
            }

            // Boolean
            if (prop.returnType.isSubtypeOf(Boolean::class.createType())) {
                log.debug("Found the type, we have a Boolean, setting the value")
                prop.setter.call(instance, value.toBoolean())
                continue
            }

            if (prop.returnType.isSubtypeOf(Float::class.createType())) {
                log.debug("Found the type, we have a Boolean, setting the value")
                prop.setter.call(instance, value.toFloat())
                continue
            }

            if (prop.returnType.isSubtypeOf(Double::class.createType())) {
                log.debug("Found the type, we have a Boolean, setting the value")
                prop.setter.call(instance, value.toDouble())
                continue
            }

            // Handle Enum types
            if ((prop.returnType.javaType as Class<*>).isEnum) {
                log.debug("Found the type, we have an Enum, setting the value")
                val enumClass = (prop.returnType.classifier as kotlin.reflect.KClass<*>).java
                val enumValue =
                    enumClass.enumConstants.first {
                        (it as Enum<*>).name.equals(value, ignoreCase = true)
                    }
                prop.setter.call(instance, enumValue)
                continue
            }
        }
    }

    fun getProperty(name: String) =
        instance::class
            .declaredMemberProperties
            .filter { it.name == name }
            .first()

    fun getAnnotation(field: KProperty1<out IStressProfile, Any?>): Optional<Annotation> {
        val tmp = field.annotations.filter { it is WorkloadParameter }

        return if (tmp.size == 1) {
            Optional.of(tmp.first())
        } else {
            Optional.empty()
        }
    }

    /**
     * Returns the name and description
     * This code is a bit hairy...
     */
    fun getCustomParams(): List<WorkloadParameterType> {
        val result = mutableListOf<WorkloadParameterType>()

        for (prop in instance::class.declaredMemberProperties) {
            (prop.annotations.firstOrNull { it.annotationClass == WorkloadParameter::class } as? WorkloadParameter)?.run {
                result.add(WorkloadParameterType(prop.name, description, prop.returnType.toString()))
            }
        }
        return result
    }
}
