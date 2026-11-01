package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.springframework.data.domain.Sort
import java.time.LocalDate

/** Everything a caller is allowed to set on a task, for both create and update. */
data class TaskDetails(
    val title: String,
    val notes: String? = null,
    val version: Long? = null,
    val categoryId: Long? = null,
    val dueDate: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
)

/** Optional narrowing of a task list. A null field means "don't filter on this". */
data class TaskFilter(
    val status: TaskStatus? = null,
    val categoryId: Long? = null,
    val priority: Priority? = null,
    val dueBefore: LocalDate? = null,
    val query: String? = null,
)

// The earlier version of TaskSort, kept for reference. It sorted in Kotlin,
// with a Comparator, after the database had already returned the rows. That
// gives the right order only when every row is in hand: once the database
// returns a page, sorting afterwards puts the wrong rows in order (the five
// LOW tasks on page 0 get neatly sorted while the HIGH task waits on page 3).
//
// A Comparator is still the right tool when:
//   - the list is already complete in memory and small, e.g. a handful of
//     results you are about to show, or the items of one order;
//   - the order depends on something the database cannot see, such as a
//     value computed in Kotlin, a call to another service, or a rule too
//     awkward to write as SQL (a "smart" score mixing due date and priority);
//   - the data did not come from a database at all (a file, an API response).
//
// Reach for a database Sort instead when the data is paged or large, or when
// the ordering only needs columns the database already has.
//
/*enum class TaskSort(val comparator: Comparator<Task>) {

    /** Soonest deadline first; tasks with no deadline at the bottom. */
    DUE_DATE(
        compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }
            .thenByDescending { it.priority },
    ),

    /** Most urgent first, then by deadline. */
    PRIORITY(
        compareByDescending<Task> { it.priority }
            .thenBy(nullsLast()) { it.dueDate },
    ),

    TITLE(compareBy<Task, String>(String.CASE_INSENSITIVE_ORDER) { it.title }),

    NEWEST(compareByDescending<Task> { it.createdAt }),
}*/

/**
 * The orderings the API offers. Each maps to a database Sort, so ordering
 * happens in SQL before any paging. Priority sorts by priorityRank, because
 * the priority column itself is text and would sort alphabetically
 * (HIGH, LOW, MEDIUM) instead of by urgency.
 */
enum class TaskSort(val sort: Sort) {

    /** Soonest deadline first; tasks with no deadline at the bottom. */
    DUE_DATE(Sort.by(Sort.Order.asc("dueDate").nullsLast(), Sort.Order.desc("priorityRank"))),

    /** Most urgent first, then by deadline. */
    PRIORITY(Sort.by(Sort.Order.desc("priorityRank"), Sort.Order.asc("dueDate").nullsLast())),

    TITLE(Sort.by(Sort.Order.asc("title").ignoreCase())),

    NEWEST(Sort.by(Sort.Order.desc("createdAt"))),
}