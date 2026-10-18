# 2. The entity lifecycle

> Files to have open: `service/TaskService.kt`, `service/CategoryService.kt`

This is the chapter that makes JPA stop feeling like magic. Everything
surprising about Hibernate comes from one idea: **inside a transaction,
Hibernate is watching your objects.**

## The persistence context

A `@Transactional` method runs with a *persistence context* open — think of it
as a notebook Hibernate keeps for the duration of the transaction. Every entity
it loads or saves goes in the notebook, along with a snapshot of what that row
looked like at the time.

At commit, Hibernate compares each object to its snapshot and writes an `UPDATE`
for anything that changed. This is called **dirty checking**, and it is the
single most important thing to understand about JPA.

## Four states

| State | Meaning | How you get there |
|---|---|---|
| **Transient** | A plain object. Hibernate has never seen it, no row exists. | `Task(title = "Buy milk")` |
| **Managed** | In the notebook. Changes to it will be written at commit. | `findById(…)`, or `save()` on a transient object |
| **Detached** | Was managed, but the transaction has ended. Changes go nowhere. | Returning an entity out of a `@Transactional` method |
| **Removed** | Marked for deletion at commit. | `delete(entity)` |

## The consequence: `save()` is often redundant

Look at `TaskService.update()`:

```kotlin
fun update(id: Long, details: TaskDetails): Task {
    val task = findById(id)              // ← now MANAGED
    task.title = details.title.trim()    // ← Hibernate notices this
    task.dueDate = details.dueDate
    …
    return tasks.save(task)              // ← does nothing you would miss
}
```

`findById` returned a managed entity. The assignments are already enough: at
commit, dirty checking sees the difference and issues the `UPDATE`. The `save()`
call is kept because it reads clearly and costs nothing, but the row changes
with or without it.

Now compare `create()`:

```kotlin
val task = Task(title = …)   // TRANSIENT — Hibernate has never seen this
applyStatus(task, details.status)
return tasks.save(task)      // ← REQUIRED. This is what makes it managed.
```

Here `save()` is doing real work. Same method name, completely different job,
and the difference is entirely about which state the object was in.

> **Try it.** Exercise 1 asks you to build a rename endpoint *without* calling
> `save` anywhere, and proves through a second HTTP request that the change
> reached the database anyway. Seeing that pass is the moment dirty checking
> stops being a claim and becomes something you have watched happen.

## When writes actually happen

Hibernate delays `INSERT`/`UPDATE`/`DELETE` as long as it can, so it can batch
and reorder them. It flushes:

1. at commit,
2. before running a query that might be affected by pending changes,
3. when you call `flush()` yourself.

That reordering is usually invisible — until it isn't. Here is the one place in
this app where it bites, in `CategoryService.delete`:

```kotlin
val orphaned = tasks.findAll(TaskSpecifications.inCategory(id))
orphaned.forEach { it.category = null }
tasks.saveAll(orphaned)
tasks.flush()                 // ← force the UPDATEs out NOW
categories.delete(category)
```

Without that `flush()`, Hibernate is free to send the category `DELETE` before
the task `UPDATE`s. Rows would still be pointing at the category being deleted,
and the database would reject it with a foreign key violation. The flush pins
the order. This is also why the tests use `saveAndFlush` when checking a unique
constraint — without the flush, the `INSERT` might not reach the database before
the test finishes looking.

## Why entities must not escape

Once the transaction ends, managed entities become **detached**. Touch a lazily
loaded property on a detached entity and you get the famous
`LazyInitializationException`, because the connection it would need is gone.

This app sidesteps the whole category of problem in two ways.
`application.properties` has:

```properties
spring.jpa.open-in-view=false
```

Spring Boot's default is `true`, which holds the persistence context open for
the entire HTTP request. It makes lazy loading "just work" in the view layer and
quietly produces N+1 queries while nobody is looking. Turning it off means lazy
loading problems show up immediately, as a clear exception during development,
rather than as mysterious slowness in production.

Second, the controllers map entities to DTOs — `TaskResponse.from(task)` — so
what leaves the service layer is a plain immutable object with no connection to
Hibernate at all. That is the deeper reason `web/dto/` exists: not just API
versioning, but keeping managed state from leaking into a layer that cannot
manage it.

## `readOnly = true`

```kotlin
@Transactional(readOnly = true)
fun findAll(…): List<Task>
```

This tells Hibernate it can skip dirty checking entirely, since nothing is
meant to change — less work and less memory on every read. It also signals
intent to the next person: this method does not write.

## The mental checklist

When something persists that you did not expect, or does not persist when you
did:

1. Is this object **managed**? (did it come from `findById`, or did I construct it?)
2. Am I inside a **transaction**? (is the method `@Transactional`, and am I
   calling it from *another* bean — self-calls bypass the proxy and the
   transaction never starts)
3. Has it **flushed** yet, and does the order matter?

Nine times out of ten the answer is in those three questions.

---

Next: [repositories without code](03-repositories-without-code.md).
