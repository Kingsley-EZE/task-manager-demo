package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.repository.CategoryRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskSpecifications
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
@Transactional
class TaskService(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {

    @Transactional(readOnly = true)
    fun findAll(filter: TaskFilter = TaskFilter(), sort: TaskSort = TaskSort.DUE_DATE): List<Task> =
        tasks.findAll(specificationFor(filter), sort.sort)

    @Transactional(readOnly = true)
    fun findById(id: Long): Task =
        tasks.findById(id).orElseThrow { NotFoundException("No task with id $id") }

    fun create(details: TaskDetails): Task {
        val task = Task(
            title = details.title.trim(),
            notes = details.notes?.trim()?.ifBlank { null },
            category = resolveCategory(details.categoryId),
            dueDate = details.dueDate,
            priority = details.priority,
        )
        applyStatus(task, details.status)
        return tasks.save(task)
    }

    fun update(id: Long, details: TaskDetails): Task {
        val task = findById(id)
        task.title = details.title.trim()
        task.notes = details.notes?.trim()?.ifBlank { null }
        task.category = resolveCategory(details.categoryId)
        task.dueDate = details.dueDate
        task.priority = details.priority
        applyStatus(task, details.status)
        return tasks.save(task)
    }

    fun changeStatus(id: Long, status: TaskStatus): Task {
        val task = findById(id)
        applyStatus(task, status)
        return tasks.save(task)
    }

    fun changeTaskTitle(id: Long, title: String): Task {
        val task = findById(id)
        task.title = title.trim()
        return task
    }

    fun delete(id: Long) {
        if (!tasks.existsById(id)) throw NotFoundException("No task with id $id")
        tasks.deleteById(id)
    }

    fun stats(): Map<String, Long> =
        TaskStatus.entries.associate { it.name to tasks.countByStatus(it) } +
                ("overdue" to tasks.countByStatusNotAndDueDateBefore(TaskStatus.DONE, LocalDate.now()))

    // Why this version is better for fetching stats: stats() above runs one
    // COUNT query per status, so the number of database round trips grows with
    // every status added to the enum. This version asks for every count at once
    // with a single GROUP BY, so it is two queries (that one plus overdue) no
    // matter how many statuses exist. The catch is that GROUP BY only returns
    // rows for statuses that actually have tasks, so we iterate over
    // TaskStatus.entries rather than over the query result, and fill each gap
    // with 0L. Without that, an unused status would be missing from the JSON
    // instead of showing 0.
    fun statss(): Map<String, Long> {
        val counted = tasks.countGroupedByStatus()
            .associate { row -> row[0] as TaskStatus to row[1] as Long }

        return TaskStatus.entries.associate { it.name to (counted[it] ?: 0L) } +
                ("overdue" to tasks.countByStatusNotAndDueDateBefore(TaskStatus.DONE, LocalDate.now()))
    }

    @Transactional(readOnly = true)
    fun findPage(filter: TaskFilter, pageable: Pageable): Page<Task> =
        tasks.findAll(specificationFor(filter), pageable)

    /**
     * The completion time is derived from the status rather than supplied by
     * the caller, so the two can never disagree. Re-finishing an already
     * finished task keeps the time it was first finished.
     */
    private fun applyStatus(task: Task, newStatus: TaskStatus) {
        val wasDone = task.status == TaskStatus.DONE
        val willBeDone = newStatus == TaskStatus.DONE

        task.completedAt = when {
            !willBeDone -> null             // not finished: no completion time
            wasDone -> task.completedAt     // already finished: keep the original
            else -> Instant.now()           // finishing right now
        }
        task.status = newStatus
    }

    // The earlier version of applyStatus, kept for reference. It behaves
    // identically; the difference is only in how plainly it reads. There, the
    // third rule was implied by the *absence* of an else branch: when a task
    // that was already DONE was marked DONE again, neither condition matched,
    // the when did nothing, and completedAt kept its original value.
    //
    // private fun applyStatus(task: Task, status: TaskStatus) {
    //     when {
    //         status != TaskStatus.DONE -> task.completedAt = null
    //         task.status != TaskStatus.DONE -> task.completedAt = Instant.now()
    //     }
    //     task.status = status
    // }

    private fun resolveCategory(categoryId: Long?): Category? = categoryId?.let { id ->
        categories.findById(id).orElseThrow { NotFoundException("No category with id $id") }
    }

    private fun specificationFor(filter: TaskFilter): Specification<Task> = Specification.allOf(
        listOfNotNull(
            filter.status?.let(TaskSpecifications::hasStatus),
            filter.priority?.let(TaskSpecifications::hasPriority),
            filter.categoryId?.let(TaskSpecifications::inCategory),
            filter.dueBefore?.let(TaskSpecifications::dueOnOrBefore),
            filter.query?.takeIf { it.isNotBlank() }?.let(TaskSpecifications::mentions),
        ),
    )
}
