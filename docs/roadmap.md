# Getting good at Kotlin and Spring Boot

A route from "I can read this project" to "I can be trusted with a production
service." Five stages, each with a reason to care, a milestone you can actually
check, and what to read.

Two rules make the difference between working through this and merely reading
it:

**Build the milestone before reading more.** Every stage has one. A stage you
have read about but not built is a stage you have not done.

**Read the SQL and the logs.** Most Spring confusion is cured by turning on
`logging.level.org.hibernate.SQL=DEBUG` and looking at what actually ran.

**Build it in new projects.** [The project ladder](project-ladder.md) turns
these stages into small projects, each started from an empty folder.

---

## Stage 0 — Finish this project

You are here. Before moving on:

- [ ] Work through [the four chapters](learning/) on domain and repositories
- [x] Make the six exercises pass: `./gradlew exercises`
- [ ] Explain to someone else why `update()` does not need its `save()` call

That last one is the real test. If you can explain dirty checking out loud, the
hardest idea in JPA is behind you.

---

## Stage 1 — Persistence you can trust

**Why:** `ddl-auto=update` and an H2 file are training wheels. Every real
service has a schema it controls deliberately and a test suite that runs
against the database it actually ships on.

| Learn | Why it matters |
|---|---|
| **Flyway migrations** | Replace `ddl-auto`. Schema changes become reviewed, versioned, ordered files. This is the single biggest step toward production. |
| **PostgreSQL** | Swap H2 for the real thing. Discover which of your assumptions were H2-shaped. |
| **Testcontainers** | Tests run against real PostgreSQL in Docker. No more "worked on H2". |
| **The N+1 problem** | Add a `@OneToMany` from `Category` to its tasks and watch the query count explode. Fix it with `@EntityGraph`. |
| **Transactions properly** | Propagation, isolation levels, and why a `@Transactional` method calling another method on `this` silently does nothing. |

**Milestone:** this app, on PostgreSQL in Docker, with Flyway owning the schema
and the whole suite green against Testcontainers.

**Read:** [Flyway's getting started](https://documentation.red-gate.com/fd),
[Testcontainers for Java](https://java.testcontainers.org/),
*Spring in Action* chapter 3, and Vlad Mihalcea's blog for anything JPA — he is
the best source on Hibernate internals that exists.

---

## Stage 2 — A web layer that survives contact with users

**Why:** your API currently trusts whoever calls it and tells them everything
when it breaks. Neither survives being public.

| Learn | Why it matters |
|---|---|
| **Spring Security** | Authentication and authorisation. Start with form login, then JWT, then OAuth2. Expect it to be the hardest thing here. |
| **Users and ownership** | Tasks belong to a person. Every query gets narrower, and forgetting one is a data leak. |
| **Pagination everywhere** | Exercise 3 covers the mechanics. The habit is what counts. |
| **OpenAPI / springdoc** | Generated, browsable API docs. One dependency. |
| **Problem Details (RFC 9457)** | The standard error format. Compare it with the `ApiError` you already have. |
| **Bean Validation in depth** | Custom constraints, validation groups, cross-field rules. |

**Milestone:** multi-user task manager. Log in, see only your own tasks, and
prove it with a test that asserts one user cannot read another's task by id.
That test is the whole stage in one assertion.

**Read:** [Spring Security reference](https://docs.spring.io/spring-security/reference/)
— the architecture chapter first, before any tutorial.

---

## Stage 3 — Kotlin as a Kotlin developer

**Why:** so far you have written Java with nicer syntax. Kotlin has its own
idioms, and the gap between the two is where "cracked" lives.

| Learn | Why it matters |
|---|---|
| **Coroutines** | Structured concurrency. `suspend`, scopes, dispatchers. |
| **Spring WebFlux with coroutines** | Non-blocking endpoints that still read top to bottom. Know *when* it is worth it — usually not. |
| **Sealed classes and exhaustive `when`** | Model results and states so the compiler enforces your branches. A `sealed interface TaskResult` beats exceptions for expected failures. |
| **Inline/value classes** | `TaskId(val value: Long)` makes mixing up two Longs impossible. |
| **Delegation, scope functions, DSLs** | `by`, `let`/`run`/`apply`/`also` used deliberately rather than by reflex. |
| **Arrow** (optional) | Functional error handling. Worth a look; not required. |

**Milestone:** rewrite one slice of this app — say categories — using a sealed
result type instead of thrown exceptions, and decide honestly whether it reads
better. Both answers are defensible; having an opinion backed by having tried it
is the point.

**Read:** *Kotlin in Action* (2nd ed), and the coroutines guide on kotlinlang.org.

---

## Stage 4 — Running it in production

**Why:** code that works on your laptop is half a service.

| Learn | Why it matters |
|---|---|
| **Actuator** | Health checks, metrics, readiness probes. |
| **Structured logging + correlation ids** | Tracing one request through the logs. |
| **Micrometer + Prometheus/Grafana** | Knowing it is slow before a user tells you. |
| **Caching** | `@Cacheable`, Redis, and the hard part: invalidation. |
| **Docker + a real deploy** | Multi-stage builds, layered jars, Fly.io or Railway to start. |
| **CI** | GitHub Actions running the suite on every push. |
| **Config and secrets** | Profiles, environment variables, and never committing a password. |
| **Graceful shutdown, connection pools** | HikariCP sizing, timeouts, retries. |

**Milestone:** this app deployed publicly, with a health endpoint, a dashboard
showing request rates and latency, and CI that blocks a merge when tests fail.

---

## Stage 5 — Design judgment

**Why:** past this point the questions stop having single right answers, and
seniority is mostly about choosing well and explaining why.

- **Hexagonal / ports and adapters** — your `domain → service → web` split is
  already a simple version. Read the full idea and decide how much you want.
- **CQRS, event-driven, outbox pattern** — and when the complexity is *not*
  worth it, which is most of the time.
- **Spring Modulith** — modular monolith. Usually the right answer before
  microservices, and the one people skip.
- **Kafka or RabbitMQ** — asynchronous messaging, and what it costs you in
  debuggability.
- **Reading the framework's source** — the genuine step change. When
  `@Transactional` surprises you, open `TransactionInterceptor` and find out.

**Milestone:** take a feature request — "tasks can be shared with other users" —
and write the design document before writing code. Alternatives considered,
trade-offs named, decision justified.

---

## How to keep learning after that

**Read code.** Spring's own source, and well-run Kotlin projects on GitHub.

**Rebuild things you already understand.** The second task manager teaches more
than the first, because the problem is no longer in the way.

**Follow a few people rather than many:** Vlad Mihalcea (Hibernate),
Josh Long (Spring), Baeldung (reference, not depth), the Kotlin blog.

**Be suspicious of tutorials.** Most are copied from each other and years out of
date — you hit exactly that in this project, where Spring Boot 4 had moved
`@DataJpaTest` and the H2 console somewhere tutorials still get wrong. The
official reference documentation is less fun and almost always correct.

---

## The honest timeline

Stages 1 and 2 will take a few months of evenings. Stage 3 changes how you
write code and is worth going slowly. Stages 4 and 5 never really finish.

Nobody holds all of this in their head. The skill is not having memorised it —
it is knowing what exists, what it is called, and where to look it up.
