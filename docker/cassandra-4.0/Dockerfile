# Cassandra 4.0 with experimental features enabled
FROM cassandra:4.0

# Enable materialized views and increase timeouts
RUN sed -i 's/#materialized_views_enabled: false/materialized_views_enabled: true/g' /etc/cassandra/cassandra.yaml && \
    sed -i 's/write_request_timeout_in_ms: .*/write_request_timeout_in_ms: 10000/g' /etc/cassandra/cassandra.yaml && \
    sed -i 's/read_request_timeout_in_ms: .*/read_request_timeout_in_ms: 10000/g' /etc/cassandra/cassandra.yaml && \
    sed -i 's/range_request_timeout_in_ms: .*/range_request_timeout_in_ms: 20000/g' /etc/cassandra/cassandra.yaml

# Set heap size for containers
ENV MAX_HEAP_SIZE=512M
ENV HEAP_NEWSIZE=128M
