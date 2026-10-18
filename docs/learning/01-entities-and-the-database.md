# 1. Entities and the database

> Open these files beside you: `domain/Task.kt`, `domain/Category.kt`, `build.gradle.kts`

## First, the words

Three words get used constantly. Here they are in plain English:

**Database** — a program that stores your data in tables. A table is just a
grid, like a spreadsheet. Columns are the headings (`title`, `due_date`).
Rows are the entries (one row = one task).

**ORM** — stands for Object-Relational Mapping. It means: *a translator between
Kotlin objects and database rows.* Without it you would write SQL by hand and
copy values into objects yourself. With it, you work with Kotlin objects and
the translator writes the SQL.

**JPA and Hibernate** — JPA is the rulebook for how that translator should
behave. Hibernate is the program that follows the rulebook. You write JPA;
Hibernate does the work. Like a recipe and a cook.

**Entity** — a Kotlin class that has been linked to a database table.
`Task` is an entity. `TaskResponse` is not — it is just a plain object.

**Annotation** — a label you stick on code, written with `@`, that tells a
tool something. `@Entity` on a class is you telling Hibernate "this one is a
table." The annotation does nothing by itself; some tool has to read it.

## An entity is a translation table

A `Task` object and a row in the database are the same information in two
places:

| In Kotlin | In the database |
|---|---|
| class `Task` | table `tasks` |
| property `title` | column `title` |
| one `Task` object | one row |
| `task.category` | the column `category_id` |

So when you read `Task.kt`, read each annotation as you answering a question
Hibernate is asking you:

```kotlin
@Entity                      // "Is this class a table?"        Yes.
@Table(name = "tasks")       // "What is the table called?"     tasks.
class Task(
    @Column(nullable = false, length = 200)
    var title: String,       // "What kind of column?"  Text, max 200
                             //  characters, never allowed to be empty.
```

That is the whole file. Questions and answers.

## The id column

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
var id: Long? = null,
```

**Primary key** means: the column that uniquely identifies a row. Like a
student number — two students can share a name, never a student number.
`@Id` marks it.

`@GeneratedValue` means *you do not make up the id — the database does.*
`IDENTITY` is the common way: the database counts 1, 2, 3 as rows go in.

### Why `Long?` and not `Long`

In Kotlin, `Long?` means "this might be nothing." That question mark is telling
you the truth about a real moment in the object's life:

```kotlin
val task = Task(title = "Buy milk")   // task.id is null — nothing saved yet
tasks.save(task)                      // now the database gives it an id
println(task.id)                      // 7
```

Before saving, there is no id, because the database has not seen it. After
saving, there is. The type has to allow both.

This is also why `TaskResponse.from()` looks the way it does:

```kotlin
id = requireNotNull(task.id) { "A saved task always has an id" }
```

By then the task has definitely been saved, so the id definitely exists. But
the compiler cannot know that, so you say it out loud.

## Why a plain class, not a `data class`

A `data class` is the usual Kotlin reach for "a class that holds data." Here it
causes two real bugs.

### Bug one: the object gets lost

A `data class` builds its identity out of *all* its values. Two tasks with the
same title, date and status count as "the same task."

Now watch what happens:

```kotlin
val task = Task(title = "Buy milk")   // id = null
val box = hashSetOf(task)             // put it in a set
tasks.save(task)                      // id becomes 7 — the object CHANGED

box.contains(task)                    // false! Even though it is right there.
```

A `HashSet` files objects by a number worked out from their values. The id
changed from `null` to `7`, so the number changed, so the set looks in the
wrong drawer. The object is in the box and the box says it is not.

Think of a cloakroom that files coats by colour. You hand in a white coat, they
file it under W. While it hangs there someone dyes it blue. You come back, they
look under B, and your coat is gone — even though it is hanging three feet
away.

**Entities should be compared by id, not by contents.** Plain classes do that
by default.

### Bug two: Hibernate needs to extend your class

Hibernate sometimes hands you a stand-in object instead of the real one (more
on that in chapter 4). To build a stand-in, it has to create a *child class* of
`Task`. Kotlin classes are **final** by default, which means "no child classes
allowed." A `data class` cannot be un-finalled at all.

That is what this block in `build.gradle.kts` is for:

```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
}
```

It says: any class marked `@Entity`, drop the "no children allowed" rule.

Its partner, `kotlin("plugin.jpa")`, fixes a different problem. When Hibernate
reads a row, it needs to make an empty `Task` first and then fill it in. An
empty one means calling `Task()` with no arguments — and your `Task` requires a
title. The plugin quietly adds a no-argument constructor for Hibernate's
private use.

**Neither plugin is optional. Remove either and the app will not start.**

## Why `var` and not `val`

`val` means "cannot be changed after it is created." That sounds safer, and for
most Kotlin it is. But Hibernate needs to *write into* your object when it loads
a row, and watch it for changes afterwards (chapter 2). Both need `var`.

`val` is still right in `web/dto/` — a request that arrived from a browser
should never change afterwards.

## Three different kinds of "required"

This confuses everyone, because the same idea shows up three times and each one
does a *different job*:

```kotlin
var title: String                    // 1. Kotlin: cannot be null. Ever.
@Column(nullable = false)            // 2. Database: this column rejects empty.
@field:NotBlank val title: String    // 3. Requests: reply 400 if it is blank.
```

Say someone sends `{"title": ""}` to your API:

1. **Kotlin's non-null type** does nothing here — `""` is not null, it is an
   empty string. This one only stops *null*.
2. **`@Column(nullable = false)`** also does nothing — again, `""` is not null.
   This one shapes the table and is the database's last line of defence.
3. **`@NotBlank`** is the one that fires, and it is the only one that produces
   a helpful reply:
   `{"fieldErrors": {"title": "A task needs a title"}}`

Note where number 3 lives: on `TaskRequest` in `web/dto/`, not on the entity.
It is a rule about *incoming requests*, not about *storage*. Delete it and a
blank title sails through until something deeper breaks, with an ugly error
nobody can act on.

## Enums: store the word, not the number

An **enum** is a fixed list of options. `Priority` is `LOW`, `MEDIUM`, `HIGH`
and nothing else.

```kotlin
@Enumerated(EnumType.STRING)
var priority: Priority = Priority.MEDIUM,
```

`STRING` stores the word `'HIGH'` in the column.

The default, if you leave `@Enumerated` off, is `ORDINAL` — it stores the
*position in the list* as a number. `LOW` is 0, `MEDIUM` is 1, `HIGH` is 2.

Here is why that default is a trap. Suppose next year you add `URGENT` in the
middle:

```kotlin
enum class Priority { LOW, MEDIUM, URGENT, HIGH }
//                     0      1       2      3
```

Every row that said `2` used to mean `HIGH`. Now it means `URGENT`. You have
silently changed the meaning of data already saved, with no error anywhere.
With `STRING`, a row saying `'HIGH'` means `HIGH` forever.

**The cost of `STRING`:** sorting. Ask the database to sort by priority and it
sorts the *text* alphabetically — `HIGH`, `LOW`, `MEDIUM`. That is nonsense as
an order of urgency. It is exactly why `TaskSort` in `service/TaskCommands.kt`
sorts in Kotlin instead, where `LOW < MEDIUM < HIGH` follows the order you
wrote them in.

## Relationships: how one row points at another

```kotlin
@ManyToOne
@JoinColumn(name = "category_id")
var category: Category? = null,
```

Read `@ManyToOne` literally: **many** tasks point at **one** category. Twenty
tasks can all say "Groceries."

**Foreign key** means: a column holding the id of a row in another table. Not
the whole category — just its id, like writing a phone number down instead of
carrying the person around.

```
tasks table                          categories table
┌────┬────────────┬─────────────┐    ┌────┬────────────┐
│ id │ title      │ category_id │    │ id │ name       │
├────┼────────────┼─────────────┤    ├────┼────────────┤
│  1 │ Buy milk   │      1 ──────────→  1 │ Groceries  │
│  2 │ Buy bread  │      1 ──────────→    │            │
│  3 │ Email Bob  │      NULL   │    │  2 │ Work       │
└────┴────────────┴─────────────┘    └────┴────────────┘
                     ↑
            task 3 has no category — that is allowed
```

`@JoinColumn(name = "category_id")` just names that column.

Notice `Category` has **no** list of tasks. That is on purpose. Nothing in this
app asks a category for its tasks, and adding such a list invites loading every
task whenever you touch one category. Only build the direction you use.

The `?` on `Category?` is there because a task with no category is perfectly
normal — "Email Bob" above. It is also what lets `CategoryService.delete` set
tasks loose instead of deleting them along with the category.

## One odd line in the build file

```kotlin
freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
```

When you write a Kotlin class like this:

```kotlin
class Task(
    @Column(nullable = false)
    var title: String,
)
```

`title` is two things at once: an argument to the constructor, *and* a property
on the class. So where does `@Column` stick — to the argument, or to the
property? Kotlin needs to be told. That flag says "both," which is what makes
the annotation reach the place JPA looks.

In older code you will see people writing `@field:Column` by hand to say the
same thing. The DTOs in this project still do, with `@field:NotBlank` — there
the distinction genuinely matters.

---

**Next:** [the entity lifecycle](02-the-entity-lifecycle.md) — the chapter that
makes Hibernate stop feeling like magic.
