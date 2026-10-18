# 4. Queries, four ways

> Open these files beside you: `repository/TaskSpecifications.kt`, `service/TaskService.kt`

A **query** is just a question you ask the database. "Give me every unfinished
task." "How many are overdue?"

There are four ways to write one in Spring Data JPA. None is best — each is
right somewhere, and knowing which to reach for is most of the skill.

---

## Way 1 — Derived methods (the default)

```kotlin
fun findByNameIgnoreCase(name: String): Category?
```

Chapter 3 covered these: you name the method and Spring writes the query.

**Reach for it when** the question is fixed and has one or two conditions.
**Stop when** the method name gets too long to read, or when some conditions
are optional.

---

## Way 2 — `@Query` (write it yourself, in JPQL)

```kotlin
@Query("SELECT t FROM Task t WHERE t.category IS NULL AND t.status <> :done")
fun findUncategorisedUnfinished(@Param("done") done: TaskStatus): List<Task>
```

**JPQL** stands for Java Persistence Query Language. It looks almost exactly
like SQL with one important difference: **it talks about your Kotlin classes,
not your tables.**

| JPQL says | SQL would say |
|---|---|
| `Task` (the class) | `tasks` (the table) |
| `t.dueDate` (the property) | `due_date` (the column) |

Hibernate translates. `:done` is a named placeholder — the value gets filled in
safely, which is also what stops SQL injection attacks.

Three variations worth knowing:

```kotlin
// Real SQL, for database-specific features JPQL cannot express
@Query("SELECT * FROM tasks WHERE …", nativeQuery = true)

// Return a summary object instead of whole entities
@Query("SELECT new com.x.Summary(t.status, COUNT(t)) FROM Task t GROUP BY t.status")

// Change many rows in one statement
@Modifying
@Query("UPDATE Task t SET t.status = :s WHERE …")
```

⚠️ **That last one comes with a warning.** A `@Modifying` query goes straight
to the database and **skips the notebook** from chapter 2. Any object Hibernate
is currently watching keeps its old values, because nobody told it. Great for
mass updates; dangerous when mixed with objects you are also editing by hand.

**Reach for it when** the question is fixed but too complicated to express as a
method name — joins, totals, summaries.

---

## Way 3 — Specifications (when the filters are optional)

This is what this app uses, and the reason is worth seeing.

Your task list has five filters — status, priority, category, due date, search
text — and the user might use **any combination**. Five optional filters is
32 possible queries. Nobody writes 32 methods.

A **Specification** is one reusable *piece* of a `WHERE` clause. You build only
the pieces you need and glue them together as the request comes in:

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

Read it in plain English, line by line:

> For each filter the caller actually sent, make the matching piece.
> Throw away the nulls (`listOfNotNull`).
> Glue what is left together with AND (`allOf`).

It is a sandwich order. The filters are the fillings. The customer picks three
of five; you build that sandwich, not all 32.

### What a single piece looks like

```kotlin
fun hasStatus(status: TaskStatus) =
    Specification<Task> { root, _, cb -> cb.equal(root.get<TaskStatus>("status"), status) }
```

Three parameters arrive, and only two matter here:

- **`root`** — the thing you are querying. Think `FROM tasks t`; `root` is `t`.
  `root.get("status")` means "the status column."
- **`cb`** — short for **criteria builder**, a factory for conditions.
  `cb.equal(a, b)` builds `a = b`. There is also `cb.like`, `cb.lessThan`,
  `cb.or`, and so on.
- **the one written `_`** — the query object itself, for grouping and ordering.
  Unused here, and Kotlin's `_` means "I know it is there, I do not need it."

So that line reads: **`WHERE status = ?`**.

**Reach for it when** filters are optional and combine freely — search screens,
filter bars, admin lists.

⚠️ **The cost:** `root.get<TaskStatus>("status")` contains a **string**. Rename
the property to `state` and nothing complains until it breaks at runtime. (A
tool called the JPA Metamodel generator fixes this by generating `Task_.status`
for you, at the price of an extra build step.)

---

## Way 4 — The `EntityManager` (the escape hatch)

```kotlin
@PersistenceContext private lateinit var em: EntityManager
```

The `EntityManager` is the raw Hibernate tool that all three ways above are
built on top of. It is the notebook from chapter 2, as an object you can hold.

Drop to it for things Spring Data does not model: stored procedures,
hand-tuned SQL, or direct control of the notebook (`em.detach`, `em.refresh`,
`em.clear`).

**Reach for it when** nothing else fits. If you find yourself here often,
something further up is wrong.

---

## Choosing between them

| Your situation | Reach for |
|---|---|
| One or two fixed conditions | a derived method |
| Fixed, but complicated — joins, totals | `@Query` |
| Optional filters that combine | a Specification |
| Database-specific SQL, procedures, bulk changes | native `@Query` or `EntityManager` |

---

# The N+1 problem

This is the performance bug nearly every JPA developer ships at least once. It
has a silly name and a simple cause.

Imagine `Category` had a list of its tasks, and you wrote:

```kotlin
categories.findAll().forEach { category ->
    println("${category.name}: ${category.tasks.size}")
}
```

Looks like one question. It is not:

```
Query 1   SELECT * FROM categories              ← the "1"
Query 2   SELECT * FROM tasks WHERE category_id = 1   ┐
Query 3   SELECT * FROM tasks WHERE category_id = 2   │ the "N"
Query 4   SELECT * FROM tasks WHERE category_id = 3   │
…                                                      ┘
```

Twenty categories means **twenty-one** trips to the database instead of one or
two. Hence "N+1": one query, plus one more per result.

It passes every test. It looks instant with five rows of sample data. It falls
over the week you get real users.

It is going to a shop with a list of twenty items and making twenty separate
trips, one item each.

## Three fixes, best first

**`@EntityGraph`** — tell the repository method to fetch the related data in
the same trip:

```kotlin
@EntityGraph(attributePaths = ["category"])
fun findByStatus(status: TaskStatus): List<Task>
```

**A join fetch** — the same idea written out in JPQL:

```kotlin
@Query("SELECT t FROM Task t LEFT JOIN FETCH t.category WHERE t.status = :status")
```

**`FetchType.EAGER`** on the mapping itself — blunt, because it applies to
*every* query whether you need the related data or not.

### Lazy and eager, plainly

- **Eager** = fetch it now, along with the main thing.
- **Lazy** = do not fetch it until somebody actually asks.

Lazy sounds strictly better and is not: lazy is exactly what produces N+1,
because "somebody asks" happens once per row inside your loop. Lazy is also
what throws `LazyInitializationException` when the asking happens after the
transaction closed (chapter 2).

The defaults are worth memorising:

| Mapping | Default | Why |
|---|---|---|
| `@ManyToOne` | **EAGER** | one extra small row — cheap |
| `@OneToMany` | **LAZY** | could be thousands of rows — never load blindly |

`Task.category` is a single `@ManyToOne` and leaves the default alone, which is
why **this app has no N+1 problem to fix**. You will meet one the moment you
add a `@OneToMany` — which the ["persistence you can trust"
stage](../roadmap.md) of the roadmap walks you through on purpose.

## Watching it happen for yourself

Add this to `application.properties`:

```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE   # shows the ? values too
```

Now run the app, load the page, and read the log. Every query Hibernate sends
appears. If you ever see the same `SELECT` repeated with different ids, you
have just caught an N+1 the way professionals do.

Being able to read the SQL your Kotlin produces is the single habit that
separates people who use an ORM from people who are used by one.

---

That is the domain and repository layers covered. Now go
[make the exercises pass](README.md#then-make-the-exercises-pass), or read
[the roadmap](../roadmap.md) for what comes after this project.
