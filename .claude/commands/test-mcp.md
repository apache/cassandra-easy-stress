---
description: Run comprehensive MCP integration tests for cassandra-easy-stress
tags: [testing, mcp, integration]
---

Run a comprehensive test suite for the cassandra-easy-stress MCP server integration:

1. **List Available Workloads**
   - Use `list_workloads` to verify all workloads are discoverable
   - Validate response format and completeness

2. **Get Workload Information**
   - Use `info` on KeyValue workload to verify detailed configuration
   - Validate response includes all expected fields

3. **List Field Generators**
   - Use `fields` to verify all available field generators
   - Validate response format

4. **Short Duration Test (10 seconds)**
   - Run KeyValue workload with duration=10 seconds
   - Monitor status during execution
   - Verify clean completion and metrics accuracy
   - Validate throughput, latency, and error metrics

5. **Status Monitoring Test**
   - Check status while test is running
   - Verify thread status and metrics updates
   - Check status after completion
   - Validate state transitions (running → completed)

6. **Stop Command Test**
   - Start a longer running test (duration=60)
   - Use `stop` command mid-execution
   - Verify graceful shutdown
   - Check final status

7. **Multiple Workload Test**
   - Run at least 2 different workloads sequentially
   - Verify each completes successfully
   - Compare metrics between workloads

8. **Parameter Validation**
   - Test with different rates, threads, and partitions
   - Verify parameter changes affect metrics as expected

9. **Error Handling**
   - Test invalid workload names
   - Test with unreachable host (if safe)
   - Verify appropriate error messages

For each test, document:
- Expected behavior
- Actual results
- Any discrepancies
- Performance metrics
- Verify the arguments returned by the status call match what was submitted.

*Note*: There is a ramp up period at the start of each test.  Getting the status will show the throughput is significantly less than what is requested, that is expected. 

Create a summary report with pass/fail status for each test scenario.
