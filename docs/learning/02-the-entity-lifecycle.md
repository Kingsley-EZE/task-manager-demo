# 2. The entity lifecycle

> Open these files beside you: `service/TaskService.kt`, `service/CategoryService.kt`

This is the chapter that makes JPA stop feeling like magic. Nearly everything
surprising about Hibernate comes from one idea:

> **Inside a transaction, Hibernate is watching your objects.**

## First, what is a transaction?

A **transaction** is a group of database changes that all succeed together or
all fail together. Nothing in between.

The classic example is moving money. Take £50 out of one account, put £50 into
another. If the second half fails, you must undo the first half — otherwise
£50 vanished. A transaction makes those two steps one all-or-nothing step.

In Spring you get one by putting `@Transactional` on a class or method:

```kotlin
@Service
@Transactional      // every method here runs inside a transaction
class TaskService(…)
```

When the method finishes normally, the transaction **commits** — the changes
are made permanent. If it throws, the transaction **rolls back** — everything
is undone as if it never happened.

## The notebook

While a transaction is open, Hibernate keeps what the docs call a *persistence
context*. Forget the name and picture a **notebook**.

Every object Hibernate hands you goes in the notebook, along with a
**photograph of how it looked at that moment**.

When the transaction ends, Hibernate flips through the notebook, compares each
object to its photograph, and writes an `UPDATE` for whatever is different.

That comparison is called **dirty checking**. "Dirty" just means "changed since
the photo." That is the whole trick, and it explains most of what follows.

```
        findById(7)                        commit
            │                                 │
            ▼                                 ▼
   ┌──────────────────┐            ┌────────────────────┐
   │ 📓 notebook      │            │ compare each object│
   │   task 7         │            │ to its photo       │
   │   📷 "Buy milk"  │            │                    │
   └──────────────────┘            │ "Buy milk" ≠       │
            │                      │ "Buy oat milk"     │
   task.title = "Buy oat milk"     │   → UPDATE tasks…  │
            │                      └────────────────────┘
            └──────────────────────────────┘
```

## Four states an object can be in

| State | In plain English | How you get there |
|---|---|---|
| **Transient** | A plain object. Hibernate has never seen it. No row exists. | `Task(title = "Buy milk")` |
| **Managed** | In the notebook. Changes will be saved automatically. | `findById(…)`, or `save()` on a transient object |
| **Detached** | Was in the notebook, but the transaction ended. Changes now go nowhere. | returning an entity out of a `@Transactional` method |
| **Removed** | Marked for deletion when the transaction commits. | `delete(entity)` |

A library makes this concrete:

- **Transient** — a book you bought in a shop. The library has no record of it.
- **Managed** — a book you borrowed. The library knows you have it and is
  tracking it.
- **Detached** — you have left the building. Still holding the book, but the
  library is no longer watching what you do with it.
- **Removed** — you handed it back and they are about to bin it.

## What this means: `save()` is often doing nothing

Look at `TaskService.update()`:

```kotlin
fun update(id: Long, details: TaskDetails): Task {
    val task = findById(id)              // ← in the notebook now (MANAGED)
    task.title = details.title.trim()    // ← Hibernate will spot this
    task.dueDate = details.dueDate
    …
    return tasks.save(task)              // ← does nothing you would miss
}
```

`findById` put the task in the notebook with its photo. The assignments changed
it. At commit, the comparison spots the difference and writes the `UPDATE`.

**The `save()` call is not what saves it.** Delete that line and the row still
changes. It is kept because it reads clearly, and it costs nothing.

Now compare `create()`:

```kotlin
val task = Task(title = …)   // TRANSIENT — Hibernate has never seen this
applyStatus(task, details.status)
return tasks.save(task)      // ← REQUIRED. This is what puts it in the notebook.
```

Here `save()` is essential. There is no photo to compare against, because
Hibernate does not know this object exists.

Same method name, completely different job. The only thing that changed is
which state the object was in.

> **See it yourself.** Exercise 1 asks you to build a rename endpoint with no
> `save()` call anywhere, then proves with a second HTTP request that the
> change really reached the database. Watching that pass is the moment this
> stops being a claim you read and becomes something you have seen.

## When the writing actually happens

Hibernate is lazy on purpose. It holds `INSERT`, `UPDATE` and `DELETE`
statements back as long as it can, so it can send them efficiently and in
whatever order suits it.

Sending them is called a **flush**. It happens:

1. when the transaction commits,
2. just before a query that might be affected by the waiting changes,
3. whenever you call `flush()` yourself.

That freedom to reorder is normally invisible. Here is the one place in this
app where it bites — `CategoryService.delete`:

```kotlin
val orphaned = tasks.findAll(TaskSpecifications.inCategory(id))
orphaned.forEach { it.category = null }    // set these tasks loose
tasks.saveAll(orphaned)
tasks.flush()                              // ← send those UPDATEs NOW
categories.delete(category)
```

Without that `flush()`, Hibernate might send the category `DELETE` *first*.
At that instant, tasks are still pointing at the category, and the database
refuses: *"you cannot delete this row, three tasks still reference it."*

It is the restaurant kitchen. You tell the waiter "cancel the soup, and change
my main to fish." The waiter can walk to the kitchen whenever they like, and
may pass the notes along in either order. `flush()` is you saying: **go now,
and take the soup note first.**

Same reason the tests use `saveAndFlush` when checking that duplicate category
names are rejected. Without the flush, the `INSERT` might still be sitting in
the waiter's pocket when the test looks for the error.

## Why entities must not escape the service layer

Once a transaction ends, its managed objects become **detached** — you left the
library. If Hibernate had been holding something back to fetch later, it now
cannot, because the connection is gone. You get
`LazyInitializationException`, which is Hibernate saying *"you are asking for
something I would have to go back for, and I cannot."*

This app avoids the whole problem in two ways.

**First**, in `application.properties`:

```properties
spring.jpa.open-in-view=false
```

Spring Boot's default here is `true`, which keeps the notebook open for the
entire web request. That makes lazy loading "just work" in the view layer —
and quietly fires dozens of extra queries while nobody is watching. Turning it
off means such problems appear immediately as a loud error during development,
instead of as mysterious slowness in production months later.

**Second**, the controllers convert entities into DTOs before replying:

```kotlin
TaskResponse.from(task)
```

**DTO** stands for Data Transfer Object — a plain, dumb object whose only job
is to carry values across a boundary. It has no connection to Hibernate, no
notebook, no surprises.

This is the deeper reason `web/dto/` exists. Not just so the API shape and the
table shape can differ, but so that live, watched, database-connected objects
never leak into a layer that cannot look after them.

## `readOnly = true`

```kotlin
@Transactional(readOnly = true)
fun findAll(…): List<Task>
```

This tells Hibernate: *nothing here will change, so skip the photographs and
skip the comparison.* Less work and less memory on every read. It also tells
the next person reading the code that this method does not write.

## A checklist for when things are weird

When something saves that you did not expect, or does not save when you did,
ask these three in order:

1. **Is this object managed?** Did it come from `findById`, or did I build it
   myself with a constructor?
2. **Am I actually in a transaction?** Is the method `@Transactional` — and am
   I calling it from a *different* class? Calling a `@Transactional` method
   from another method in the same class bypasses Spring's wrapper entirely,
   and no transaction ever starts. This one catches everybody once.
3. **Has it flushed, and does the order matter?**

Nine times out of ten the answer is in those three questions.

---

**Next:** [repositories without code](03-repositories-without-code.md).
