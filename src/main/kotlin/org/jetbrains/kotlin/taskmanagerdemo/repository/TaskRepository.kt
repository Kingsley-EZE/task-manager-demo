package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/**
 * JpaSpecificationExecutor adds findAll(Specification, Sort), which lets the
 * service compose filters at runtime instead of needing a derived method for
 * every combination of status, category, priority and search term.
 */
interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    fun countByStatus(status: TaskStatus): Long
    fun countByStatusNotAndDueDateBefore(status: TaskStatus, date: LocalDate): Long
    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    fun countGroupedByStatus(): List<Array<Any>>
}
