package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull

/**
 * EXERCISE 2 — A count, written as a method name
 * ══════════════════════════════════════════════
 * Difficulty: ★★☆☆☆     Read first: docs/learning/03-repositories-without-code.md
 *
 * GOAL
 *   Add GET /api/tasks/stats, returning how many tasks sit in each status:
 *
 *       {"TODO": 2, "IN_PROGRESS": 1, "DONE": 1, "overdue": 1}
 *
 *   Every status appears, even when its count is zero.
 *
 * THE POINT
 *   Write countByStatus(status: TaskStatus): Long on TaskRepository and watch
 *   it work with no implementation. Then notice what it costs: one query per
 *   status. Doing it in a single GROUP BY query is the "going further" below.
 *
 * WHERE TO WORK
 *   repository/TaskRepository.kt   the derived count method
 *   service/TaskService.kt         fun stats(): …  (a Map<String, Long> is fine)
 *   web/TaskController.kt          @GetMapping("/stats")
 *
 * HINTS
 *   - TaskStatus.entries gives you every status to iterate.
 *   - "overdue" means: not DONE, has a due date, and that date is in the past.
 *     A derived method can express this —
 *     countByStatusNotAndDueDateBefore(status, date). Check the keyword table
 *     in chapter 3 if that name looks like an incantation.
 *   - Watch the route order: /stats must not be swallowed by /{id}. If you get
 *     a 400 about converting "stats" to a Long, that is exactly what happened.
 *
 * GOING FURTHER
 *   Replace the N count queries with one:
 *
 *       @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
 *       fun countGroupedByStatus(): List<Array<Any>>
 *
 *   Does the endpoint still return zeros for unused statuses? (It will not —
 *   GROUP BY only returns rows that exist. Handle that.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Exercise2DerivedQueryTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
) {

    @Test
    fun `counts the tasks in each status`() {
        taskService.create(TaskDetails(title = "First"))
        taskService.create(TaskDetails(title = "Second"))
        val started = taskService.create(TaskDetails(title = "Third"))
        taskService.changeStatus(assertNotNull(started.id), TaskStatus.IN_PROGRESS)
        val finished = taskService.create(TaskDetails(title = "Fourth"))
        taskService.changeStatus(assertNotNull(finished.id), TaskStatus.DONE)

        mockMvc.get("/api/tasks/stats").andExpect {
            status { isOk() }
            jsonPath("$.TODO") { value(2) }
            jsonPath("$.IN_PROGRESS") { value(1) }
            jsonPath("$.DONE") { value(1) }
        }
    }

    @Test
    fun `reports zero for a status nothing is in`() {
        taskService.create(TaskDetails(title = "The only task"))

        mockMvc.get("/api/tasks/stats").andExpect {
            status { isOk() }
            jsonPath("$.TODO") { value(1) }
            jsonPath("$.IN_PROGRESS") { value(0) }
            jsonPath("$.DONE") { value(0) }
        }
    }

    @Test
    fun `counts overdue tasks separately`() {
        taskService.create(TaskDetails(title = "Late", dueDate = java.time.LocalDate.now().minusDays(2)))
        taskService.create(TaskDetails(title = "Soon", dueDate = java.time.LocalDate.now().plusDays(2)))
        taskService.create(TaskDetails(title = "Whenever"))

        mockMvc.get("/api/tasks/stats").andExpect {
            status { isOk() }
            jsonPath("$.overdue") { value(1) }
        }
    }
}
