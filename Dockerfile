FROM gradle:8-jdk17 AS builder
COPY . /workspace
WORKDIR /workspace
RUN gradle shadowJar

FROM eclipse-temurin:17
COPY --from=builder /workspace/build/ build/
COPY --from=builder /workspace/bin/ bin/
ENTRYPOINT ["bin/cassandra-easy-stress"]
