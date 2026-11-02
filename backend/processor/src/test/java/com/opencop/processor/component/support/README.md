# Processor Component Test Conventions

This package contains the shared infrastructure for Kafka-facing component tests.

These conventions are the baseline for new suites. Some areas of the component tree are still being built out; treat the suite shape below as the target structure to follow as coverage expands, not as a claim that every existing suite already implements every section.

## Test lane boundaries

- `component/**` is for Spring component tests.
- `integration/**` is reserved for environment-backed tests with real external dependencies.
- Component tests should be Kafka-backed when the flow depends on listener or container behavior.
- Component tests should be DB-backed when the processing flow depends on persistence.
- Component tests should be Redis-backed when the processing flow depends on runtime or cache side effects.
- Component tests should use the same topic names configured by the application and should read them from Spring configuration rather than hardcoding duplicate topic strings in the test.
- Component tests should exercise the real Spring wiring and the real processor orchestration for the ingestion path under test.
- For ingestion-flow suites, keep the real processor use case chain in place and only replace seams outside the processor module contract.
- Do not mock internal use cases, managers, repositories, listener containers, or service orchestration just to make the test easier to write.
- Only mock true external seams or non-deterministic boundaries, such as Azure/blob access, third-party API calls, or other infrastructure outside the processor module's contract.
- Narrow adapter or container-wiring slices are allowed when the explicit goal is to verify listener bootstrap, bean aliasing, or container behavior in isolation, but they should be named and scoped accordingly.

## Ingestion suite template

Keep ingestion suites grouped in this order when applicable:

1. `Startup and wiring`
2. `Happy path`
3. `Headers and payload failures`
4. `Retry or DLT behavior`
5. `Persistence or cache side effects`
6. `Replay, ordering, and idempotency`
7. `Other edge cases`

The goal is not to force every listener flow into every section. The goal is to keep the suite shape predictable as suites grow.

At minimum, each ingestion-flow suite should cover:

- The main happy path for the topic or queue entrypoint.
- Meaningful malformed-input cases, such as missing headers, bad payloads, or invalid state transitions.
- The side effects that define the contract for that flow, such as downstream publication, persisted state, Redis updates, or listener lifecycle changes.
- The asynchronous behavior that matters for correctness, such as retries, DLT routing, startup replay, or stale message rejection.

Tests should verify the processor's real contract, even when that contract is slightly surprising. Do not rewrite tests to fit an assumed design if the implemented behavior is different; either assert the current behavior or change the production code deliberately.

## Deterministic async readiness

- Do not use fixed sleeps as the only readiness signal for Kafka-backed, Redis-backed, DB-backed, polling, or cancellation scenarios. Use a concrete readiness observation, such as listener partition assignment, a latch or barrier that proves work reached the state under test, or a polling helper around the observable side effect.
- For embedded Kafka suites, wait for the listener container assignment before publishing records. Use `KafkaListenerEndpointRegistry` and `ContainerTestUtils.waitForAssignment(...)` for `@KafkaListener` containers, or the same assignment-wait pattern when starting listener containers directly.
- When testing cancellation, route gates, polling, retries, or async side effects, arrange the test so publishing or assertions happen only after the system has reached the relevant state. Timeouts should bound the wait; they should not define the state.
- Keep awaits visible in the scenario or in small named helpers and probes. Do not hide publishing, release gates, or other control flow that explains why the assertion is deterministic.

## Scenario isolation in shared async state

- Treat fixture cleanup as hygiene, not as proof that every record published by the previous scenario has finished processing. Late Kafka work may still reach a shared database, cache, or DLT after cleanup returns.
- Give each scenario unique record keys and domain correlations, such as event IDs and object IDs. Scope broker reads, persistence waits, and cache assertions to those values so unrelated late work cannot satisfy the current scenario.
- Positive persistence waits should query only the current scenario's correlations and retain exact cardinality and domain-field assertions. Avoid whole-table counts as readiness or correctness checks in asynchronous suites.
- For a negative persistence assertion, first await the matching terminal outcome, such as the uniquely keyed DLT record, and then query for the invalid record's own event, object, or payload correlation. A global zero-row assertion can be contaminated by unrelated work and does not identify which record was persisted.
- Treat embedded Kafka partition count as topology coverage, not as a stability knob. Tests whose contract is not partition-specific should assert behavior that remains valid with one or more partitions. Change the count when partition routing or cross-partition ordering is the behavior under test, not solely to force deterministic scheduling.

## Support responsibilities

- Shared support in this package stays narrow and only owns generic Kafka, DB, Redis, and async test helpers.
- Reusable Spring test bootstrap should live in shared support, such as a generic processor Kafka component test application and shared Kafka test configuration for embedded broker producers, templates, and listener factories.
- When component tests need production-like Kafka listener behavior, shared test bootstrap should reuse the same extracted factory-building logic as production rather than reimplementing parallel listener configuration in test code.
- `fixture/` owns repeatable setup, topic publishing helpers, and seed data.
- `assertion/` owns repeated broker, persistence, and cache checks.
- Domain-specific probes, setup, and assertions should live beside the consumer or feature suite that owns them, not in the generic shared support root.
- Consumer-specific tests should keep only the minimal local configuration needed to import the consumer under test, register its test doubles, and alias shared infrastructure beans to the bean names that consumer expects.
- When a component slice needs local Spring wiring, prefer a dedicated top-level component test context class over a nested test configuration inside the test class.

## Fixture conventions

Every fixture helper should follow the same lifecycle-oriented API:

- `reset()` clears broker-visible state, persistence, and cache state needed by the scenario.
- `seed...()` prepares prerequisite reference data.
- `persist...()` inserts concrete records for a scenario when setup must happen through storage instead of ingestion.
- `publish...()` sends messages to the ingress topic under test.
- `default...()` returns reusable payload, header, or entity defaults.

Fixture helpers should not perform assertions.

Prefer fixture helpers that prepare real Kafka, database, or Redis state for a scenario over mocking orchestration.

## Assertion conventions

- Assertion helpers should verify repeated topic output, domain invariants, persisted state, or cache/runtime side effects.
- They should stay small and composable.
- They should not hide control flow, publishing, or await logic that is important to understanding the scenario.

Tests should still read like scenarios; helpers should remove duplication, not hide behavior.
