# 3. Repositories without code

> Open these files beside you: `repository/TaskRepository.kt`, `repository/CategoryRepository.kt`

## The thing that should not work

```kotlin
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByNameIgnoreCase(name: String): Category?
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findAllByOrderByNameAsc(): List<Category>
}
```

An **interface** in Kotlin is a list of method names with no bodies — a
promise that someone, somewhere, will provide the actual code.

Except nobody does. There is no `CategoryRepositoryImpl` anywhere in this
project. Search for it; it does not exist. And yet
`categoryService.findAll()` runs and returns rows.

Here is what is really happening.

## Spring writes the class for you at startup

When the app starts, Spring Data looks for interfaces like this one and
**generates the implementation in memory**, then hands you an object of that
generated class. Your service gets a real working object; it just was not
written by a human.

Each method gets handled in one of three ways:

| Kind | Example | Where the code comes from |
|---|---|---|
| **Inherited** | `save`, `findById`, `deleteById` | Standard code shipped with Spring Data |
| **Derived** | `findByNameIgnoreCase` | Spring reads the *method name* and builds the query from it |
| **Declared** | `@Query("SELECT …")` | You wrote the query yourself |

It is the coffee shop where the barista knows your order. You say three words —
"the usual, large" — and a complicated drink appears. The name carried the
whole instruction.

## Method names, read as sentences

Spring splits the name into two parts:

```
findAllByOrderByNameAsc
└──┬───┘└──────┬───────┘
 what to do   how to filter / sort
```

The first part decides the *kind* of query: `find`, `exists`, `count`,
`delete`. Everything after `By` is matched against **property names on your
entity**.

So these three become:

```kotlin
findByNameIgnoreCase     →  SELECT … FROM categories WHERE LOWER(name) = LOWER(?)
existsByNameIgnoreCase   →  SELECT COUNT(…) > 0 FROM categories WHERE …
findAllByOrderByNameAsc  →  SELECT … FROM categories ORDER BY name ASC
```

Words you can use, all of which combine freely:

| Write this | Get this SQL |
|---|---|
| `findByTitle` | `title = ?` |
| `findByTitleContaining` | `title LIKE %?%` |
| `findByDueDateBefore` / `After` | `due_date < ?` / `> ?` |
| `findByDueDateBetween` | `BETWEEN ? AND ?` |
| `findByDueDateIsNull` | `IS NULL` |
| `findByStatusIn` | `IN (?, ?, …)` |
| `findByTitleIgnoreCase` | wraps both sides in `LOWER()` |
| `findByCategoryName` | **joins to the categories table** and matches `name` |
| `findTop5ByOrderByDueDateAsc` | `ORDER BY … LIMIT 5` |

That second-to-last one earns attention. `findByCategoryName` walks from `Task`
into `Task.category`, then into `Category.name`, and writes the join for you.
A **join** means asking one question across two tables at once — "give me tasks,
and look up each one's category while you are at it."

## The spelling matters, and that is good news

Misspell a property — `findByNaem` — and the app **refuses to start**. The
error names the property it could not find.

That feels harsh and is a gift. Compare the two ways of being wrong:

- A typo inside a handwritten SQL string: found by a user, in production, at
  2am.
- A typo in a derived method name: found by you, ten seconds after you save
  the file, before the app serves a single request.

Failing early and loudly is almost always better than failing late and quietly.

## The return type is part of the instruction

Spring also reads what you promised to return:

| You write | You get |
|---|---|
| `Category?` | the row, or `null` if there is none |
| `Optional<Category>` | the same idea, Java's way of saying it |
| `List<Category>` | every match — an empty list if none |
| `Boolean` (with `exists`) | whether anything matched |
| `Long` (with `count`) | how many matched |
| `Page<Category>` | one page of results plus the total count |

## Where this approach stops being a good idea

Derived names fall apart as conditions pile up. Four optional filters means
writing every combination by hand:

```kotlin
fun findByStatus(…)
fun findByStatusAndPriority(…)
fun findByStatusAndPriorityAndCategoryId(…)
fun findByStatusAndCategoryId(…)
// …and about a dozen more
```

That is the exact problem `TaskRepository` dodges:

```kotlin
interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>
```

That second interface adds `findAll(Specification)`, which lets
`TaskService.specificationFor()` build the filter **while the app is running**,
out of whichever filters the caller actually sent. One method, every
combination. Chapter 4 covers it.

## `JpaRepository` vs `CrudRepository`

People present these as a choice. They are not — one contains the other:

```
Repository  →  CrudRepository  →  ListCrudRepository  →  JpaRepository
(empty          save, findById,     same, but returns      + everything below
 marker)        delete, count…      List not Iterable
```

**CRUD** just stands for Create, Read, Update, Delete — the four basic things
you do to stored data.

So `JpaRepository` *is* a `CrudRepository` with extras on top:

- **`List` instead of `Iterable`.** `Iterable` only promises "you can loop over
  this" — no `.size`, no `list[0]`. `List` gives you both. This is the
  difference you feel day to day.
- **Paging and sorting**, and query-by-example.
- **Methods that only make sense for a real SQL database:** `flush()`,
  `saveAndFlush()`, `getReferenceById()`, and the `…InBatch` bulk deletes.

This project genuinely needs that last group. `CategoryService.delete` calls
`tasks.flush()` (chapter 2 explains why it must), and three tests call
`saveAndFlush`. Swap in `CrudRepository` and the code stops compiling.

**The rule of thumb:** use `CrudRepository` if you might swap the database for
something non-SQL like MongoDB later and can live without flush control. Use
`JpaRepository` once you have committed to a SQL database — which this app has.

### One trap in older tutorials

You will read that `PagingAndSortingRepository extends CrudRepository`. **That
stopped being true in Spring Data 3.0**, and it is still false in the 4.1.1
this project uses:

```
public interface PagingAndSortingRepository<T, ID> extends Repository<T, ID>
```

So if you declare it on its own expecting `save` and `findById` to come along
for free, they will not. `JpaRepository` inherits from both branches, which is
why it has the lot.

---

**Next:** [queries, four ways](04-queries-four-ways.md).
