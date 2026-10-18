package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull

/**
 * EXERCISE 5 — Clear the finished tasks, in one statement
 * ═══════════════════════════════════════════════════════
 * Difficulty: ★★★☆☆     Read first: docs/learning/03-repositories-without-code.md
 *
 * GOAL
 *   Add DELETE /api/tasks/completed. It removes every DONE task, replies 200,
 *   and reports how many it removed:
 *
 *       {"deleted": 3}
 *
 * THE POINT
 *   The obvious implementation — find them all, loop, deleteById each — issues
 *   one DELETE per row. JpaRepository offers deleteAllInBatch(entities), which
 *   sends a single statement. This is the first exercise where you choose
 *   between two working implementations on cost rather than correctness.
 *
 * WHERE TO WORK
 *   repository/TaskRepository.kt   a derived finder for DONE tasks, if you want one
 *   service/TaskService.kt         fun deleteCompleted(): Int
 *   web/TaskController.kt          @DeleteMapping("/completed")
 *
 * HINTS
 *   - Route order again: /completed must be declared before /{id}, or Spring
 *     will try to parse "completed" as a Long.
 *   - deleteAllInBatch bypasses the persistence context: entities already
 *     loaded in this transaction will not know they are gone. That is fine
 *     here, and is the kind of thing worth knowing before it surprises you.
 *
 * GOING FURTHER
 *   Try it a third way, with a modifying query:
 *
 *       @Modifying
 *       @Query("DELETE FROM Task t WHERE t.status = :status")
 *       fun deleteByStatusInOneGo(@Param("status") status: TaskStatus): Int
 *
 *   Three implementations, same behaviour, different costs. Which would you
 *   ship, and what would change your mind?
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Exercise5BulkDeleteTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
) {

    private fun finish(title: String) {
        val task = taskService.create(TaskDetails(title = title))
        taskService.changeStatus(assertNotNull(task.id), TaskStatus.DONE)
    }

    @Test
    fun `removes every finished task and says how many`() {
        finish("Done one")
        finish("Done two")
        taskService.create(TaskDetails(title = "Still to do"))

        mockMvc.delete("/api/tasks/completed").andExpect {
            status { isOk() }
            jsonPath("$.deleted") { value(2) }
        }

        mockMvc.get("/api/tasks").andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Still to do") }
        }
    }

    @Test
    fun `leaves unfinished tasks alone`() {
        taskService.create(TaskDetails(title = "To do"))
        val started = taskService.create(TaskDetails(title = "In progress"))
        taskService.changeStatus(assertNotNull(started.id), TaskStatus.IN_PROGRESS)

        mockMvc.delete("/api/tasks/completed").andExpect {
            status { isOk() }
            jsonPath("$.deleted") { value(0) }
        }

        mockMvc.get("/api/tasks").andExpect {
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `is harmless when there is nothing to clear`() {
        mockMvc.delete("/api/tasks/completed").andExpect {
            status { isOk() }
            jsonPath("$.deleted") { value(0) }
        }
    }
}
