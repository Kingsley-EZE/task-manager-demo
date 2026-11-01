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

    // Exercise 6, going further: optimistic versus pessimistic locking.
    //
    // Optimistic locking (what Task uses, through @Version) takes no lock.
    // Everyone reads and edits freely, and a conflict is only caught when
    // saving, by the version check. Like a shared document that says
    // "someone changed this, reload".
    //
    // Pessimistic locking locks the row the moment it is read, with
    // SELECT ... FOR UPDATE. Anyone else who wants to change that row waits
    // until the locking transaction finishes. Like a bathroom door lock:
    // nobody walks in on you, but a queue forms outside.
    //
    // Cost when many requests want the same row:
    //   - Optimistic: reads and saves are cheap, but the losers redo their
    //     work. Ten simultaneous updates to one task means one wins and nine
    //     fail and retry. When conflicts are rare, as in a task app, it costs
    //     almost nothing.
    //   - Pessimistic: nobody fails, but everyone waits in line. Work on that
    //     row happens one request at a time, a slow transaction holds up the
    //     rest, and lock timeouts or deadlocks become possible (two
    //     transactions each waiting for a row the other has locked, until the
    //     database cancels one).
    //
    // Why this app needs optimistic locking: a pessimistic lock lasts only as
    // long as one transaction, a few milliseconds inside one request. It
    // cannot cover a person who opens the edit form, thinks for two minutes,
    // then saves, because those are separate requests and separate
    // transactions. Two people editing from stale pages can only be caught by
    // a version check.
    //
    // When pessimistic locking is the better choice: short read-then-write
    // steps entirely on the server, where many requests hit the same row and a
    // failed save is costly or awkward to retry. Taking the last item from
    // stock, booking the last seat, moving money between balances, handing out
    // the next invoice number, or anything whose retry would repeat a side
    // effect such as sending an email.
    //
    // In Spring Data, it is an annotation on a repository method. Called
    // inside a transaction, Hibernate adds FOR UPDATE to the SELECT and the
    // row stays locked until that transaction commits. LockModeType comes from
    // jakarta.persistence. With logging.level.org.hibernate.SQL=DEBUG you can
    // see "for update" at the end of the generated query.
    //
    // @Lock(LockModeType.PESSIMISTIC_WRITE)
    // fun findLockedById(id: Long): Task?
    //
    // Rule of thumb: optimistic by default; pessimistic only for short, highly
    // contested writes on the server, where a retry is worse than a wait.
}
