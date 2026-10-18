package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * EXERCISE 6 — Stop two people overwriting each other
 * ═══════════════════════════════════════════════════
 * Difficulty: ★★★★★     Read first: docs/learning/02-the-entity-lifecycle.md
 *
 * GOAL
 *   Two people open the same task. One saves. Then the other saves, still
 *   holding the version they loaded before the first change. Right now the
 *   second save wins silently and the first person's edit vanishes — the
 *   "lost update" problem. Make the stale save fail instead.
 *
 * THE POINT
 *   One annotation does it:
 *
 *       @Version
 *       var version: Long? = null
 *
 *   on Task. Hibernate then adds `AND version = ?` to every UPDATE and
 *   increments the column. If the row was changed in between, zero rows match,
 *   Hibernate notices, and throws. This is optimistic locking: no locks are
 *   held, conflicts are detected at write time rather than prevented.
 *
 * WHERE TO WORK
 *   domain/Task.kt   add the @Version property
 *
 * THEN DEAL WITH THE CONSEQUENCES
 *   - The exception escapes as a 500. Map it in GlobalExceptionHandler to 409
 *     Conflict with a message a person can act on ("Someone else changed this
 *     task — reload and try again").
 *   - Decide whether `version` belongs in TaskResponse. It must, if a client
 *     is ever going to send it back on a PUT. That is the real design
 *     question this exercise is asking.
 *
 * NOTE ON THIS TEST
 *   Unlike the others it is not @Transactional, because the whole point is
 *   that the two edits happen in *separate* transactions. TransactionTemplate
 *   runs each block in its own.
 *
 * GOING FURTHER
 *   When would you reach for pessimistic locking (SELECT … FOR UPDATE) over
 *   this? What does each cost under contention? Spring Data exposes both via
 *   @Lock on a repository method.
 */
@SpringBootTest
class Exercise6OptimisticLockingTest @Autowired constructor(
    private val tasks: TaskRepository,
    private val transactions: TransactionTemplate,
) {

    @AfterEach
    fun cleanUp() {
        transactions.executeWithoutResult { tasks.deleteAll() }
    }

    @Test
    fun `refuses a save based on a stale copy`() {
        val id = assertNotNull(
            transactions.execute { tasks.save(Task(title = "Shared task")).id },
        )

        // Two people load the same task, each in their own transaction.
        val mine = assertNotNull(transactions.execute { tasks.findById(id).orElseThrow() })
        val theirs = assertNotNull(transactions.execute { tasks.findById(id).orElseThrow() })

        // They save first, and that is allowed.
        transactions.executeWithoutResult {
            theirs.title = "Their title"
            tasks.save(theirs)
        }

        // My copy is now out of date, so my save must be refused rather than
        // quietly discarding their change.
        assertFailsWith<ObjectOptimisticLockingFailureException> {
            transactions.executeWithoutResult {
                mine.title = "My title"
                tasks.saveAndFlush(mine)
            }
        }
    }

    @Test
    fun `keeps the change that was actually accepted`() {
        val id = assertNotNull(
            transactions.execute { tasks.save(Task(title = "Shared task")).id },
        )

        val mine = assertNotNull(transactions.execute { tasks.findById(id).orElseThrow() })

        transactions.executeWithoutResult {
            val theirs = tasks.findById(id).orElseThrow()
            theirs.title = "Their title"
            tasks.save(theirs)
        }

        runCatching {
            transactions.executeWithoutResult {
                mine.title = "My title"
                tasks.saveAndFlush(mine)
            }
        }

        val survivor = assertNotNull(transactions.execute { tasks.findById(id).orElseThrow() })
        assertEquals("Their title", survivor.title)
    }

    @Test
    fun `an ordinary edit still works`() {
        val id = assertNotNull(
            transactions.execute { tasks.save(Task(title = "Lonely task")).id },
        )

        transactions.executeWithoutResult {
            val task = tasks.findById(id).orElseThrow()
            task.title = "Renamed"
            tasks.save(task)
        }

        val reloaded = assertNotNull(transactions.execute { tasks.findById(id).orElseThrow() })
        assertEquals("Renamed", reloaded.title)
    }
}
