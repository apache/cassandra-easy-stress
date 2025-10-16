# cassandra-easy-stress Developer Guide

## Build and Testing Commands
- Build: `./gradlew shadowJar`
- Run: `bin/cassandra-easy-stress`
- Run tests: `./gradlew test`
- Run single test: `./gradlew test --tests "org.apache.cassandra.easystress.MainArgumentsTest"`
- Format code: `./gradlew ktlintFormat`
- Check formatting: `./gradlew ktlintCheck`
- Generate docs: `./gradlew docs`

## Code Style Guidelines
- Kotlin version: 1.9.0
- Uses ktlint for style enforcement
- Indentation: 4 spaces
- Classes: PascalCase, Functions/Variables: camelCase, Constants: UPPER_SNAKE_CASE
- Use data classes for simple data containers
- Prefer extension functions, default parameters, and named parameters
- Import ordering: standard library, external libraries, project-specific
- Use null safety operators and when expressions for type checking
- String templates for string interpolation
- Include proper exception handling with logging
- JUnit 5 and AssertJ for tests.  Write tests using assertj assertions.
- Do not use runBlocking for tests, they cause issues with junit.  Use `kotlinx.coroutines.test.runTest` instead.
- Do not test simple configuration parsing.
