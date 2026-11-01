package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * Deletes every task with the given status in a single DELETE statement
     * and returns how many rows it removed. Unlike deleteAllInBatch, nothing
     * is loaded first: there is no find step, so it is one database trip in
     * total however many rows match.
     *
     * What @Modifying does: @Query on its own assumes a SELECT and tries to
     * read rows back. @Modifying tells Spring Data this query changes data,
     * so it runs it as an update and returns the affected row count instead.
     * Leave it off and the call fails at runtime. A modifying query also needs
     * a transaction, which TaskService already provides.
     *
     * What it skips: the statement goes straight to the database, past
     * Hibernate's in-memory copies of the entities. So:
     *   - Task objects already loaded in this transaction still look alive.
     *     clearAutomatically = true on @Modifying clears them afterwards, and
     *     flushAutomatically = true writes any unsaved changes beforehand.
     *   - Lifecycle callbacks such as @PreRemove do not run, and JPA cascades
     *     do not apply. Only the database's own foreign-key rules do.
     *
     * When to use it: removing or updating many rows that a simple condition
     * describes, when no per-row Kotlin logic has to run. Clearing finished
     * tasks or archiving old ones are typical cases.
     *
     * When not to: when each deletion must trigger a callback, a cascade or
     * an audit entry, or when you need the deleted tasks themselves (to log
     * or return them). Then load them and use deleteAllInBatch, or delete
     * them one by one if each needs its own handling.
     */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.status = :status")
    fun deleteByStatusInOneGo(@Param("status") status: TaskStatus): Int
}
