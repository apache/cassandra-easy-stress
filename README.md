# cassandra-easy-stress: A workload centric stress tool and framework designed for ease of use.

This project is a work in progress.

cassandra-easy-stress is a configuration-based tool for doing benchmarks and testing simple data models for Apache Cassandra. 
Unfortunately, it can be challenging to configure a workload. There are fairly common data models and workloads seen on Apache Cassandra.  
This tool aims to provide a means of executing configurable, pre-defined profiles.

Full docs are here: https://apache.github.io/cassandra-easy-stress/

# Installation

The easiest way to get started on Linux is to use system packages.
Instructions for installation can be found here: https://apache.github.io/cassandra-easy-stress/#_installation


# Building

Clone this repo, then build with gradle:

    git clone https://github.com/apache/cassandra-easy-stress.git
    cd cassandra-easy-stress
    ./gradlew shadowJar

Use the shell script wrapper to start and get help:

    bin/cassandra-easy-stress -h

# Examples

Time series workload with a billion operations:

    bin/cassandra-easy-stress run BasicTimeSeries -i 1B

Key value workload with a million operations across 5k partitions, 50:50 read:write ratio:

    bin/cassandra-easy-stress run KeyValue -i 1M -p 5k -r .5


Time series workload, using TWCS:

    bin/cassandra-easy-stress run BasicTimeSeries -i 10M --compaction "{'class':'TimeWindowCompactionStrategy', 'compaction_window_size': 1, 'compaction_window_unit': 'DAYS'}"

Time series workload with a run lasting 1h and 30mins:

    bin/cassandra-easy-stress run BasicTimeSeries -d "1h30m"

Time series workload with Cassandra Authentication enabled:

    bin/cassandra-easy-stress run BasicTimeSeries -d '30m' -U '<username>' -P '<password>'
    **Note**: The quotes are mandatory around the username/password
    if they contain special chararacters, which is pretty common for password

# Generating docs

Docs are served out of /docs and can be rebuild using `./gradlew docs`.

# Testing

## Running Tests

To run the test suite:

    ./gradlew test

## Special Workloads

Some workloads require specific features or configurations that may not be available in all Cassandra deployments. These workloads are marked with special annotations and are skipped by default when running tests.

### DSE-Specific Workloads

Workloads that require DataStax Enterprise (DSE) features are marked with the `@RequireDSE` annotation.

Currently, the following workloads require DSE:
- `DSESearch` - Uses DSE Search (Solr) functionality

To run tests that require DSE, set the `TEST_DSE` environment variable:

    TEST_DSE=1 ./gradlew test

### Materialized Views Workloads

Workloads that use Materialized Views are marked with the `@RequireMVs` annotation. Materialized Views are not enabled by default. 

Currently, the following workloads require Materialized Views:
- `MaterializedViews` - Tests materialized view functionality

To run tests that require Materialized Views, set the `TEST_MVS` environment variable:

    TEST_MVS=1 ./gradlew test

### Accord Workloads

Workloads that use Accord (available in Cassandra 6.0+) are marked with the `@RequireAccord` annotation.

Currently, the following workloads require Accord:
- `TxnCounter` - Tests Accord transaction functionality

To run tests that require Accord, set the `TEST_ACCORD` environment variable:

    TEST_ACCORD=1 ./gradlew test

### Running All Tests

To run all tests including DSE, Materialized Views, and Accord workloads:

    TEST_DSE=1 TEST_MVS=1 TEST_ACCORD=1 ./gradlew test

Make sure you have the appropriate Cassandra configuration and features enabled before running these specialized tests.
