package org.jetbrains.kotlin.taskmanagerdemo.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import java.time.Instant
import java.time.LocalDate

/**
 * What a client may send when creating or replacing a task.
 *
 * Note what is absent: id, createdAt and completedAt. Those are the server's
 * to decide, so there is no field for a client to lie about.
 */
data class TaskRequest(
    @field:NotBlank(message = "A task needs a title")
    @field:Size(max = 200, message = "A title can be at most 200 characters")
    val title: String,

    @field:Size(max = 2000, message = "Notes can be at most 2000 characters")
    val notes: String? = null,

    val categoryId: Long? = null,
    val dueDate: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
) {
    fun toDetails() = TaskDetails(
        title = title,
        notes = notes,
        categoryId = categoryId,
        dueDate = dueDate,
        priority = priority,
        status = status,
    )
}

/** Body of PATCH /api/tasks/{id}/status. */
data class StatusRequest(val status: TaskStatus)

data class TaskResponse(
    val id: Long,
    val title: String,
    val notes: String?,
    val category: CategoryResponse?,
    val dueDate: LocalDate?,
    val priority: Priority,
    val status: TaskStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
    /** Worked out here so every client shows "overdue" by the same rule. */
    val overdue: Boolean,
) {
    companion object {
        fun from(task: Task, today: LocalDate = LocalDate.now()) = TaskResponse(
            id = requireNotNull(task.id) { "A saved task always has an id" },
            title = task.title,
            notes = task.notes,
            category = task.category?.let(CategoryResponse::from),
            dueDate = task.dueDate,
            priority = task.priority,
            status = task.status,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            overdue = task.status != TaskStatus.DONE &&
                task.dueDate != null &&
                task.dueDate!!.isBefore(today),
        )
    }
}
