package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.repository.CategoryRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskSpecifications
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class TaskService(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {

    @Transactional(readOnly = true)
    fun findAll(filter: TaskFilter = TaskFilter(), sort: TaskSort = TaskSort.DUE_DATE): List<Task> =
        tasks.findAll(specificationFor(filter)).sortedWith(sort.comparator)

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

    fun delete(id: Long) {
        if (!tasks.existsById(id)) throw NotFoundException("No task with id $id")
        tasks.deleteById(id)
    }

    /**
     * The completion time is derived from the status rather than supplied by
     * the caller, so the two can never disagree. Re-finishing an already
     * finished task keeps the time it was first finished.
     */
    private fun applyStatus(task: Task, status: TaskStatus) {
        when {
            status != TaskStatus.DONE -> task.completedAt = null
            task.status != TaskStatus.DONE -> task.completedAt = Instant.now()
        }
        task.status = status
    }

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
