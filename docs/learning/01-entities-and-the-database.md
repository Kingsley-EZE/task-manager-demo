# 1. Entities and the database

> Files to have open: `domain/Task.kt`, `domain/Category.kt`, `build.gradle.kts`

A JPA entity is not really a class. It is a **map between two worlds**:

| Kotlin | Database |
|---|---|
| class `Task` | table `tasks` |
| property `title` | column `title` |
| one `Task` object | one row |
| `task.category` | the foreign key column `category_id` |

Everything in `Task.kt` is an answer to a question about that map. Read the
annotations as a conversation:

```kotlin
@Entity                      // "this class is a table"
@Table(name = "tasks")       // "...called tasks, not TASK"
class Task(
    @Column(nullable = false, length = 200)
    var title: String,       // "a NOT NULL VARCHAR(200) column"
```

## The identity column

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
var id: Long? = null,
```

`@Id` marks the primary key. `@GeneratedValue` says *the database assigns it*,
not you — `IDENTITY` maps to an auto-increment column.

This is why the type is `Long?` and not `Long`. A task you have just
constructed has no id yet; it gets one when Hibernate inserts it. The nullable
type is telling you the truth about a real state the object passes through.
That is also why `TaskResponse.from()` does this:

```kotlin
id = requireNotNull(task.id) { "A saved task always has an id" }
```

By the time an entity is being turned into a response it has certainly been
saved, so the null is impossible — but you have to say so, because the type
system cannot know it.

## Why a plain class, not a `data class`

Kotlin's `data class` is the obvious reach, and it is wrong here. Two reasons:

**`equals`/`hashCode` are derived from every property.** Put a `Task` in a
`HashSet` before saving it, save it, and its `id` changes from `null` to `1`.
Its hash code changes with it, and the set can no longer find the object it is
already holding. Entities should be compared by id, never by content.

**Hibernate subclasses your entity.** To return a lazy proxy, Hibernate
generates a subclass at runtime. A `data class` is final, and `copy()` on an
entity gives you an object that looks saved but isn't attached to anything.

Kotlin classes are also final by default, which is why `build.gradle.kts` has:

```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
    …
}
```

That makes `@Entity` classes open so Hibernate can subclass them. Its sibling,
`kotlin("plugin.jpa")`, generates the no-argument constructor JPA needs to
instantiate a row it has just read. Without those two plugins this app does not
start — they are not optional polish.

## Why `var` and not `val`

Hibernate writes into your object when loading a row, and watches it for
changes afterwards (see [the entity lifecycle](02-the-entity-lifecycle.md)).
Immutable entities fight both. `val` belongs in the DTOs in `web/dto/`, where
immutability is genuinely right — a request object should never change after it
arrives.

## Three different kinds of "required"

This trips up everyone, because the same idea appears at three layers and they
do *different* jobs:

```kotlin
var title: String                              // Kotlin: cannot be null, ever (compile time)
@Column(nullable = false)                      // DDL: the column is NOT NULL (database)
@field:NotBlank val title: String              // Validation: reject this request (HTTP, in TaskRequest)
```

The first is a compile-time guarantee. The second shapes the generated schema
and is the database's own last line of defence. The third is the only one that
produces a friendly `400` with a message — it lives on the DTO, not the entity,
because it is about *requests*, not about *storage*. Drop the `@NotBlank` and a
blank title fails deeper down as an ugly constraint violation instead of a clear
error.

## Enums

```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
var priority: Priority = Priority.MEDIUM,
```

`EnumType.STRING` stores `'HIGH'`. The default, `ORDINAL`, stores the enum's
position as a number — `LOW` is 0, `MEDIUM` is 1, `HIGH` is 2. That default is a
trap: insert `URGENT` between `MEDIUM` and `HIGH` later and every existing row
silently changes meaning. Always use `STRING` unless you have measured a reason
not to.

The cost of `STRING` shows up in `service/TaskCommands.kt`: sorting by priority
in SQL would order the *text* — `HIGH`, `LOW`, `MEDIUM` alphabetically — which
is not what anyone means by urgency. That is why `TaskSort` sorts in Kotlin
using the enum's declaration order instead.

## Relationships

```kotlin
@ManyToOne
@JoinColumn(name = "category_id")
var category: Category? = null,
```

Read `@ManyToOne` literally: **many** tasks point at **one** category. The side
holding the annotation is the *owning* side, which means the foreign key column
lives on **its** table — `tasks.category_id`. There is no matching property on
`Category`, and that is deliberate: nothing in this app needs to ask a category
for its tasks, and adding a `@OneToMany` list would invite loading every task
whenever you touch a category.

The property is nullable because a task without a category is a normal thing. It
is also why `CategoryService.delete` can detach tasks instead of cascading a
delete into them.

`@ManyToOne` defaults to `FetchType.EAGER` — the category is fetched in the same
query as the task. That is fine for one small row, and it is why mapping a task
to a `TaskResponse` outside the transaction does not explode. The alternative,
`LAZY`, and the trouble it causes, are covered in
[queries, four ways](04-queries-four-ways.md).

## A detail you will not see elsewhere

In `build.gradle.kts`:

```kotlin
freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
```

Kotlin constructor properties are ambiguous: when you annotate one, does the
annotation belong to the *constructor parameter* or to the *backing field*? JPA
needs the field. This flag makes the compiler apply it to both, which is why
`@Column` on a constructor parameter works at all. In older code you will see
`@field:Column` written out by hand for the same reason — and that is still what
the DTOs do, because there the distinction matters.

---

Next: [the entity lifecycle](02-the-entity-lifecycle.md) — where the real magic
is, and where the surprises come from.
