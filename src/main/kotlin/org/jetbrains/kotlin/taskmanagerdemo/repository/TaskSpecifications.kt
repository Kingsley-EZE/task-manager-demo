package org.jetbrains.kotlin.taskmanagerdemo.repository

import jakarta.persistence.criteria.JoinType
import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

/**
 * Reusable query fragments. A Specification is one piece of a WHERE clause;
 * combining the ones that apply means a single query handles every mix of
 * filters, instead of a method per combination.
 */
object TaskSpecifications {

    fun hasStatus(status: TaskStatus) =
        Specification<Task> { root, _, cb -> cb.equal(root.get<TaskStatus>("status"), status) }

    fun hasPriority(priority: Priority) =
        Specification<Task> { root, _, cb -> cb.equal(root.get<Priority>("priority"), priority) }

    fun inCategory(categoryId: Long) =
        Specification<Task> { root, _, cb ->
            cb.equal(root.get<Category>("category").get<Long>("id"), categoryId)
        }

    fun dueOnOrBefore(date: LocalDate) =
        Specification<Task> { root, _, cb -> cb.lessThanOrEqualTo(root.get<LocalDate>("dueDate"), date) }

    /**
     * Case-insensitive substring match over the title, the notes and the
     * category name. The join is LEFT so tasks with no category still match
     * on their title or notes; an inner join would drop them.
     */
    fun mentions(text: String) =
        Specification<Task> { root, _, cb ->
            val pattern = "%${text.trim().lowercase()}%"
            val category = root.join<Task, Category>("category", JoinType.LEFT)
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("notes")), pattern),
                cb.like(cb.lower(category.get("name")), pattern)
            )
        }
}
