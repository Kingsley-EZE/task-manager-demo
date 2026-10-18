# 3. Repositories without code

> Files to have open: `repository/TaskRepository.kt`, `repository/CategoryRepository.kt`

```kotlin
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByNameIgnoreCase(name: String): Category?
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findAllByOrderByNameAsc(): List<Category>
}
```

No implementation. No `class CategoryRepositoryImpl` anywhere. Yet
`categoryService.findAll()` runs SQL. Here is what actually happens.

## At startup, Spring writes the class for you

When the application context starts, Spring Data scans for interfaces extending
`Repository` and, for each one, generates a proxy object implementing it. Each
method is backed by one of three strategies:

1. **Inherited** (`save`, `findById`, `deleteById`, …) — standard JPA code that
   ships with Spring Data.
2. **Derived** (`findByNameIgnoreCase`) — the method *name* is parsed and a
   query is built from it.
3. **Declared** (`@Query("…")`) — you supply the query yourself.

That generated object is what gets injected into your service. The interface is
a specification; Spring Data is the implementer.

## Reading method names as queries

The parser splits a name into a **subject** and a **predicate**:

```
findAllByOrderByNameAsc
└──┬──┘└────────┬──────┘
 subject     predicate

findByNameIgnoreCase   →   SELECT … FROM categories WHERE LOWER(name) = LOWER(?)
existsByNameIgnoreCase →   SELECT COUNT(…) > 0 …
findAllByOrderByNameAsc →  SELECT … FROM categories ORDER BY name ASC
```

The subject decides the *kind* of query: `find`, `exists`, `count`, `delete`.
The predicate after `By` is matched against **property names on the entity**,
joined with `And` / `Or`.

Useful keywords, all of which compose:

| Keyword | Generates |
|---|---|
| `findByTitle` | `title = ?` |
| `findByTitleContaining` | `title LIKE %?%` |
| `findByDueDateBefore` / `After` | `due_date < ?` / `> ?` |
| `findByDueDateBetween` | `BETWEEN ? AND ?` |
| `findByDueDateIsNull` | `IS NULL` |
| `findByStatusIn` | `IN (?, ?, …)` |
| `findByTitleIgnoreCase` | wraps both sides in `LOWER()` |
| `findByCategoryName` | joins to `categories` and matches `name` |
| `findTop5ByOrderByDueDateAsc` | `ORDER BY … LIMIT 5` |

That last column of the table is the important one: `findByCategoryName`
traverses the `@ManyToOne` into `Category.name` and writes the join for you.

## It fails at startup, not at runtime

Misspell a property — `findByNaem` — and the application **refuses to start**,
with an error naming the property it could not find on the entity. This is a
feature. A typo in a query string is a bug you find in production; a typo in a
derived method name is a bug you find before the app serves a single request.

The same discipline applies to return types, which the parser also respects:

| You write | You get |
|---|---|
| `Category?` | the row, or null |
| `Optional<Category>` | the same, Java style |
| `List<Category>` | all matches, possibly empty |
| `Boolean` (with `exists`) | whether any row matched |
| `Long` (with `count`) | how many |
| `Page<Category>` | a page plus the total count — needs a `Pageable` argument |

## Where derived queries stop being a good idea

They scale badly with the number of conditions. Four optional filters would mean
writing every combination:

```kotlin
fun findByStatus(…)
fun findByStatusAndPriority(…)
fun findByStatusAndPriorityAndCategoryId(…)
fun findByStatusAndCategoryId(…)
// …and twelve more
```

That is exactly the problem `TaskRepository` avoids:

```kotlin
interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>
```

`JpaSpecificationExecutor` adds `findAll(Specification)`, which lets
`TaskService.specificationFor()` build the `WHERE` clause at runtime from
whichever filters the caller actually supplied. One query method, every
combination. That is the subject of the next chapter.

## `JpaRepository` vs `CrudRepository`

`JpaRepository` *is* a `CrudRepository`, through `ListCrudRepository`:

```
Repository → CrudRepository → ListCrudRepository → JpaRepository
```

On top of the CRUD basics it adds `List` return types instead of `Iterable`,
paging and sorting, query-by-example, and the JPA-specific methods: `flush()`,
`saveAndFlush()`, `getReferenceById()`, and the `…InBatch` bulk deletes.

This app genuinely needs that last group — `CategoryService.delete` calls
`tasks.flush()`, and three tests call `saveAndFlush`. Declaring
`CrudRepository` instead would not compile.

One trap when reading older tutorials: **`PagingAndSortingRepository` no longer
extends `CrudRepository`.** They were decoupled in Spring Data 3.0, so declaring
it alone gets you paging without `save` or `findById`. `JpaRepository` inherits
from both branches, which is why it has everything.

---

Next: [queries, four ways](04-queries-four-ways.md).
