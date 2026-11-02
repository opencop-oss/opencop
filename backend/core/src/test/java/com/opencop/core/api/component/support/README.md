# Component Test Conventions

This package contains the shared foundation for HTTP-facing component tests.

## Test lane boundaries

- `component/**` is for Spring component tests.
- `integration/**` is reserved for environment-backed tests with real external dependencies.
- Component tests should be DB-backed when the endpoint flow depends on persistence.
- Component tests should exercise the real Spring wiring and the real application orchestration for the endpoint under test.
- Do not mock internal use cases, managers, repositories, or service orchestration just to make the test easier to write.
- Only mock true external seams or non-deterministic boundaries, such as IDP access, message brokers, notifications, cache/runtime infrastructure, or third-party API calls.

## Endpoint suite template

Keep endpoint suites grouped in this order when applicable:

1. `Auth`
2. `Happy path`
3. `Access edge cases`
4. `Validation failures`
5. `Missing resource`
6. `Other edge cases`
7. `Persistence/readback`

The goal is not to force every endpoint into every section. The goal is to keep the suite shape predictable.

At minimum, each endpoint suite should cover:

- The main happy paths for the endpoint.
- Access behavior for the meaningful roles involved, not just unauthenticated access.
- Edge cases around missing resources, invalid transitions, duplicate state, ownership rules, or empty results when those behaviors exist.
- Readback or persisted side effects when the endpoint mutates state.

Tests should verify the endpoint's real contract, even when that contract is slightly surprising. Do not rewrite tests to fit an assumed design if the implemented behavior is different; either assert the current behavior or change the production code deliberately.

## Support responsibilities

- [`AbstractWebIT`](./AbstractWebIT.java) stays narrow and only owns generic HTTP helpers.
- `fixture/` owns repeatable setup and seed data.
- `assertion/` owns repeated domain response checks.
- Domain-specific setup or assertions should not be added to `AbstractWebIT`.

## Fixture conventions

Every fixture helper should follow the same lifecycle-oriented API:

- `reset()` clears domain state.
- `seed...()` prepares prerequisite reference data.
- `persist...()` inserts concrete entities for a scenario.
- `default...()` returns reusable request or entity defaults.

Fixture helpers should not perform assertions.

Prefer fixture helpers that prepare real database state for a scenario over mocking orchestration.

## Assertion conventions

- Assertion helpers should verify repeated response shapes or domain invariants.
- They should stay small and composable.
- They should not hide control flow or request execution.

Tests should still read like scenarios; helpers should remove duplication, not hide behavior.
