# Learning the parts this project hides

The controllers and services in this project are ordinary Kotlin. Read a method
and you can see what it does.

The domain and repository layers are different. That is where the framework
does work you cannot see, and nothing in the code tells you so. An interface
with no code behind it runs SQL. A line that looks like a plain assignment
writes to the database. Those are not accidents, and they are not magic — but
nobody can guess them.

These four chapters make the hidden part visible. Read them with the files open
beside you; every example comes from this codebase.

**1. [Entities and the database](01-entities-and-the-database.md)**
How a Kotlin class becomes a table, what every annotation on `Task` is
answering, and why entities are plain classes instead of data classes.

**2. [The entity lifecycle](02-the-entity-lifecycle.md)** ← *start here if you
only read one*
What Hibernate is doing while your code runs. Why `update()` would still work
with its `save()` line deleted, and why `CategoryService.delete` would break
without its `flush()`.

**3. [Repositories without code](03-repositories-without-code.md)**
How an interface with no implementation runs SQL, how a method *name* becomes a
query, and where that trick stops working.

**4. [Queries, four ways](04-queries-four-ways.md)**
Derived methods, `@Query`, Specifications and the `EntityManager` — what each
one is for, plus the N+1 problem and how to spot it.

## Then make the exercises pass

```bash
./gradlew exercises      # red to begin with — that is the point
```

Six of them, in
`src/test/kotlin/org/jetbrains/kotlin/taskmanagerdemo/exercises/`, roughly in
order of difficulty. Each file starts with a comment saying what to build,
which files to touch, and what to read first.

| # | Exercise | What it teaches |
|---|---|---|
| 1 | Rename a task | Saving without calling `save()` |
| 2 | Status counts endpoint | Query methods built from names, and what they cost |
| 3 | Pagination | Returning one page at a time |
| 4 | Search by category name | Asking a question across two tables |
| 5 | Clear finished tasks | Changing many rows in one statement |
| 6 | Optimistic locking | Stopping two people overwriting each other |

Two things to know before you start:

**Your normal test suite stays green.** These exercises are excluded from
`./gradlew test`, which keeps running the app's own 45 tests and passing. So a
half-finished exercise never leaves you wondering whether you broke the
application.

**A few tests in exercises 4 and 6 pass from the start.** Those are there on
purpose, to catch the usual way each exercise goes wrong. They must *still*
pass when you are done.

## After that

[The roadmap](../roadmap.md) — what to learn next and in what order, from
Flyway and Testcontainers through Spring Security to putting it on the
internet.
