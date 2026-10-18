# 4. Queries, four ways

> Files to have open: `repository/TaskSpecifications.kt`, `service/TaskService.kt`

There are four ways to ask the database a question in Spring Data JPA. They are
not ranked — each is right somewhere.

## 1. Derived methods — the default

```kotlin
fun findByNameIgnoreCase(name: String): Category?
```

**Use when** the query is fixed and has one or two conditions.
**Stop when** the method name gets long enough to be hard to read, or when
conditions are optional.

## 2. `@Query` — when you want to say it in JPQL

```kotlin
@Query("SELECT t FROM Task t WHERE t.category IS NULL AND t.status <> :done")
fun findUncategorisedUnfinished(@Param("done") done: TaskStatus): List<Task>
```

JPQL looks like SQL but addresses **entities and properties**, not tables and
columns: `Task` not `tasks`, `t.dueDate` not `due_date`. Hibernate translates.

Useful variants:

```kotlin
@Query("… ", nativeQuery = true)     // real SQL, for database-specific features
@Query("SELECT new com.x.Summary(t.status, COUNT(t)) FROM Task t GROUP BY t.status")
@Modifying @Query("UPDATE Task t SET t.status = :s WHERE …")   // bulk write
```

That last one carries a warning: `@Modifying` queries go straight to the
database and **bypass the persistence context**, so entities already loaded in
the current transaction keep their stale values. Useful for mass updates,
dangerous when mixed with managed entities.

**Use when** the query is fixed but too complex to name, or you need a join,
aggregate, or projection.

## 3. Specifications — when the filters are optional

This is what this app uses, and the reason is in `TaskService`:

```kotlin
private fun specificationFor(filter: TaskFilter): Specification<Task> = Specification.allOf(
    listOfNotNull(
        filter.status?.let(TaskSpecifications::hasStatus),
        filter.priority?.let(TaskSpecifications::hasPriority),
        filter.categoryId?.let(TaskSpecifications::inCategory),
        filter.dueBefore?.let(TaskSpecifications::dueOnOrBefore),
        filter.query?.takeIf { it.isNotBlank() }?.let(TaskSpecifications::mentions),
    ),
)
```

Read it as: *for each filter that was actually supplied, add that fragment;
drop the nulls; combine what's left with AND.* Five optional filters are
thirty-two possible queries, and this is all of them.

Each fragment is a small function building a piece of a `WHERE` clause:

```kotlin
fun hasStatus(status: TaskStatus) =
    Specification<Task> { root, _, cb -> cb.equal(root.get<TaskStatus>("status"), status) }
```

The three parameters are the Criteria API: `root` is the entity you are
querying (think `FROM tasks t`), `cb` is a *builder* of conditions, and the
middle one — ignored here — is the query itself, for when you need grouping or
ordering.

**Use when** filters are optional and combine freely — search screens, admin
listings, anything with a filter bar.
**The cost:** `root.get<TaskStatus>("status")` is a *string*. Rename the
property and this breaks at runtime, not compile time. The JPA Metamodel
generator (`Task_.status`) fixes that, at the price of a build step.

## 4. The `EntityManager` — the escape hatch

```kotlin
@PersistenceContext private lateinit var em: EntityManager
```

Everything above is built on this. Drop to it for things Spring Data does not
model: stored procedures, hand-tuned native SQL, bulk operations, direct control
of the persistence context (`em.detach`, `em.refresh`, `em.clear`).

**Use when** nothing else fits. If you reach for it often, something upstream is
wrong.

## Choosing

| Situation | Reach for |
|---|---|
| One or two fixed conditions | derived method |
| Fixed but complex: joins, aggregates, projections | `@Query` |
| Optional filters that combine | Specification |
| Database-specific SQL, procedures, bulk | native `@Query` or `EntityManager` |

---

# The N+1 problem

This is the performance bug every JPA developer ships at least once.

Suppose `Category` had a `@OneToMany` list of tasks, and you wrote:

```kotlin
categories.findAll().forEach { category ->
    println("${category.name}: ${category.tasks.size}")
}
```

That is **1** query for the categories, then **N** more — one per category, as
each lazy list is touched for the first time. Twenty categories, twenty-one
queries. It passes every test, looks fine with sample data, and falls over in
production.

Three fixes, in order of preference:

**`@EntityGraph`** — declarative, on the repository method:

```kotlin
@EntityGraph(attributePaths = ["category"])
fun findByStatus(status: TaskStatus): List<Task>
```

**A join fetch** — explicit, in JPQL:

```kotlin
@Query("SELECT t FROM Task t LEFT JOIN FETCH t.category WHERE t.status = :status")
```

**`FetchType.EAGER`** — on the mapping itself. Blunt: it applies to *every*
query, whether or not you need the association. Acceptable for a single small
`@ManyToOne`, which is exactly why `Task.category` leaves the default in place
and this app has no N+1 problem to solve.

The rule of thumb: **`@ManyToOne` defaults to EAGER, `@OneToMany` defaults to
LAZY**, and collections should generally stay lazy — you fetch them explicitly
where you need them.

## Seeing it happen

Turn on SQL logging in `application.properties` and count for yourself:

```properties
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE   # shows the ? parameter values
```

Run the app, load the page, and read the log. If you see the same `SELECT`
repeated with different ids, that is an N+1 and you have just caught it the way
professionals do.

This app does not currently have an N+1 to find — `Task.category` is a single
eager `@ManyToOne`, which is the one shape that is safe by default. You will
meet the problem the moment you add a `@OneToMany`, which is what the
"persistence you trust" stage of [the roadmap](../roadmap.md) is for.

---

That is the domain and repository layers. Next, open
[the exercises](../../src/test/kotlin/org/jetbrains/kotlin/taskmanagerdemo/exercises/)
and make them pass — or read [the roadmap](../roadmap.md) for what comes after
this project.
