package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull

/**
 * EXERCISE 1 — Rename a task, and let dirty checking do the saving
 * ════════════════════════════════════════════════════════════════
 * Difficulty: ★☆☆☆☆     Read first: docs/learning/02-the-entity-lifecycle.md
 *
 * GOAL
 *   Add PATCH /api/tasks/{id}/title, taking {"title":"…"} and returning the
 *   updated task, the same shape every other task endpoint returns.
 *
 * THE POINT
 *   Implement the service method WITHOUT calling tasks.save(). An entity
 *   loaded by findById inside a @Transactional method is managed: assigning to
 *   a property is already enough, and Hibernate writes the UPDATE at commit.
 *   If the test passes with no save() call, you have seen dirty checking work.
 *
 * WHERE TO WORK
 *   web/dto/TaskDtos.kt   a request record for the body (see StatusRequest)
 *   service/TaskService.kt   fun rename(id: Long, title: String): Task
 *   web/TaskController.kt    @PatchMapping("/{id}/title")
 *
 * HINTS
 *   - changeStatus() is the closest existing method. Copy its shape, then
 *     delete the save() call and watch the test still pass.
 *   - Trim the title, and reject a blank one the way TaskRequest does.
 *
 * GOING FURTHER
 *   Why does update() still call save() if it is unnecessary? Would you keep
 *   it? There is no single right answer — decide, and be able to say why.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Exercise1DirtyCheckingTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
) {

    @Test
    fun `renames a task`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))

        mockMvc.patch("/api/tasks/${assertNotNull(task.id)}/title") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Buy oat milk"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Buy oat milk") }
        }
    }

    @Test
    fun `the new title is really in the database`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))
        val id = assertNotNull(task.id)

        mockMvc.patch("/api/tasks/$id/title") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Buy oat milk"}"""
        }

        // Fetched through a second request, not from the object we just changed.
        mockMvc.get("/api/tasks/$id").andExpect {
            jsonPath("$.title") { value("Buy oat milk") }
        }
    }

    @Test
    fun `refuses a blank title`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))

        mockMvc.patch("/api/tasks/${assertNotNull(task.id)}/title") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"   "}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
