# Task manager

A small task manager — groceries, chores, work — with categories, due dates,
priorities and status tracking. Kotlin and Spring Boot on the back, a plain
HTML page on the front, and an H2 database in a file so your list survives a
restart.

## Run it

```bash
./gradlew bootRun
```

Then open <http://localhost:8080>. The first run puts a short example list in
the database so the page isn't blank; it is skipped on every run after that, so
your own tasks are never overwritten. To start empty, delete the `data/` folder
or set `taskmanager.seed-sample-data=false`.

Run the tests with:

```bash
./gradlew test
```

## The API

Everything the page does, it does through these endpoints. `curl` works just as
well as the browser.

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/tasks` | List tasks. Optional `status`, `categoryId`, `priority`, `dueBefore`, `q`, `sort` |
| `GET` | `/api/tasks/{id}` | One task |
| `POST` | `/api/tasks` | Create one; replies `201` with a `Location` header |
| `PUT` | `/api/tasks/{id}` | Replace one |
| `PATCH` | `/api/tasks/{id}/status` | Change only the status — what ticking the box calls |
| `DELETE` | `/api/tasks/{id}` | Delete one; replies `204` |
| `GET` | `/api/categories` | List categories, alphabetically |
| `POST` | `/api/categories` | Create one; replies `201` |
| `DELETE` | `/api/categories/{id}` | Delete one; its tasks survive, uncategorised |

`sort` is one of `DUE_DATE` (the default), `PRIORITY`, `TITLE`, `NEWEST`.
`status` is `TODO`, `IN_PROGRESS` or `DONE`. `priority` is `LOW`, `MEDIUM` or
`HIGH`.

```bash
curl -s localhost:8080/api/tasks

curl -s -X POST localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Buy milk","priority":"HIGH","dueDate":"2026-10-24"}'

curl -s 'localhost:8080/api/tasks?status=TODO&sort=PRIORITY'
```

Problems always come back in the same shape, so there is only one thing to
parse:

```json
{ "status": 400,
  "error": "Bad Request",
  "message": "The request has invalid fields",
  "fieldErrors": { "title": "A task needs a title" } }
```

`400` for an invalid body or query parameter, `404` for an id that doesn't
exist, `409` for a category name already in use.

## How the code is arranged

Requests fall through four layers, each one only knowing about the next:

```
web/         HTTP: request and response shapes, status codes      TaskController
  ↓
service/     the rules                                            TaskService
  ↓
repository/  database queries                                     TaskRepository
  ↓
domain/      the things themselves                                Task, Category
```

**`domain/`** — `Task` and `Category` are JPA entities: one class, one table.
They're plain classes with `var` properties rather than `data class`es, because
a data class derives `equals`/`hashCode` from every property and that breaks
once Hibernate fills in a generated id.

**`repository/`** — interfaces, with no implementation to write. Extending
`JpaRepository` provides `save`, `findAll`, `findById` and `deleteById`; Spring
Data writes the SQL for extra methods by reading their names, which is why
`findByNameIgnoreCase` works without a body. `TaskSpecifications` holds reusable
fragments of a `WHERE` clause, so one query can serve every combination of
filters instead of needing a method per combination.

**`service/`** — where the rules that aren't plain CRUD live: a task's
completion time is derived from its status so the two can't disagree, a task
pointing at a missing category is a clean `404`, category names are trimmed and
must be unique. `@Transactional` wraps each method in a database transaction, so
a method that fails half way leaves nothing half-written.

**`web/`** — controllers that translate HTTP to service calls and back. The
request and response DTOs in `web/dto/` are deliberately separate from the
entities: the database schema and the published API are then free to change
independently, and a request body has no field in which a client could send an
`id` or a `completedAt` it shouldn't control. `GlobalExceptionHandler` is the
only place that maps an exception to a status code, which is why no controller
contains a `try`/`catch`.

**`src/main/resources/static/`** — the page. Spring Boot serves anything in
`static/` straight from `/`, which is why there's no frontend build step, no
npm and no framework.

## Tests

45 of them, all run by `./gradlew test`, each layer tested the way it's used:

- `repository/` — `@DataJpaTest`, real SQL against an in-memory database
- `service/` — `@SpringBootTest`, real repositories, no mocks, so a rule that
  only works in theory can't pass
- `web/` — `@SpringBootTest` with `MockMvc`, driving real HTTP requests through
  the whole stack

Tests use their own in-memory database (`src/test/resources/application.properties`),
so running them never touches `data/taskdb`.

## The database

H2 in file mode: the entire database is `data/taskdb.mv.db`, which is
gitignored. Hibernate creates and updates the tables from the entity classes
(`spring.jpa.hibernate.ddl-auto=update`) — fine while learning, though a
deployed application would use migrations instead.

To look inside, open <http://localhost:8080/h2-console> with JDBC URL
`jdbc:h2:file:./data/taskdb`, user `sa`, and no password. That console has no
authentication, so it is restricted to this machine; set
`spring.h2.console.enabled=false` before running this anywhere other than your
own laptop.

Moving to PostgreSQL later means changing the datasource URL and driver in
`application.properties` and swapping the H2 dependency — no Kotlin changes.

## Where you could take it next

User accounts (Spring Security), recurring tasks, subtasks, reminders, or
replacing `ddl-auto` with Flyway migrations.
