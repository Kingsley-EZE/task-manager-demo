# Learning the parts this project hides

The controllers and services in this project are ordinary Kotlin: read a method
and you can see what it does. The domain and repository layers are different —
that is where the framework does invisible work, and nothing in the code tells
you so.

These four chapters make the invisible part visible. Read them with the files
open beside you; every example is from this codebase.

1. **[Entities and the database](01-entities-and-the-database.md)** — how a
   Kotlin class becomes a table, what every annotation on `Task` is answering,
   and why entities are plain classes rather than data classes.

2. **[The entity lifecycle](02-the-entity-lifecycle.md)** — transient, managed,
   detached. Dirty checking, why `save()` is often redundant, and why
   `CategoryService.delete` has to call `flush()`. **Start here if you only read
   one.**

3. **[Repositories without code](03-repositories-without-code.md)** — how an
   interface with no implementation runs SQL, how method names are parsed into
   queries, and where that approach stops scaling.

4. **[Queries, four ways](04-queries-four-ways.md)** — derived methods,
   `@Query`, Specifications, and the `EntityManager`; when to reach for each;
   and the N+1 problem.

## Then make the exercises pass

```bash
./gradlew exercises      # red to start with — that is the point
```

Six of them, in `src/test/kotlin/org/jetbrains/kotlin/taskmanagerdemo/exercises/`,
roughly in order of difficulty. Each file opens with a comment saying what to
build, which files to touch, and what to read first. They are excluded from
`./gradlew test`, so the application's own 45 tests stay a clean signal while
you work.

| # | Exercise | Teaches |
|---|---|---|
| 1 | Rename a task | Dirty checking — persisting without `save()` |
| 2 | Status counts endpoint | Derived query methods, and what they cost |
| 3 | Pagination | `Pageable`, `Page`, and not leaking framework types into your API |
| 4 | Search by category name | Joins through the Criteria API |
| 5 | Clear finished tasks | Bulk operations, and bypassing the persistence context |
| 6 | Optimistic locking | `@Version`, lost updates, concurrency |

A few tests in exercises 4 and 6 pass already. Those are regression guards —
they must still pass when you are finished.

## After that

[The roadmap](../roadmap.md) — what to learn next, in order, from Flyway and
Testcontainers through Spring Security to deployment.
