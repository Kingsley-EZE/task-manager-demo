package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import java.time.LocalDate

/** Everything a caller is allowed to set on a task, for both create and update. */
data class TaskDetails(
    val title: String,
    val notes: String? = null,
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

/**
 * The orderings the API offers. Sorting happens in Kotlin rather than SQL
 * because priority is stored as text ("HIGH", "LOW", "MEDIUM"), so ORDER BY
 * would sort it alphabetically instead of by urgency. A personal task list is
 * small enough that this costs nothing; a list of millions would want a
 * dedicated sortable column and an index instead.
 */
enum class TaskSort(val comparator: Comparator<Task>) {

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
}
