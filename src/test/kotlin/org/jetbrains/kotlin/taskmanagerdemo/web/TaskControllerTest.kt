package org.jetbrains.kotlin.taskmanagerdemo.web

import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertNotNull

/**
 * Drives the controllers through MockMvc with the real services and database
 * behind them, so these cover the whole request path: JSON in, rules applied,
 * JSON or error out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
    private val categoryService: CategoryService,
) {

    @Test
    fun `creates a task and reports where it lives`() {
        val groceries = categoryService.create("Groceries")

        mockMvc.post("/api/tasks") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title":"Buy milk","notes":"Semi-skimmed","categoryId":${groceries.id},
                 "dueDate":"2026-10-24","priority":"HIGH"}
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.id") { exists() }
            jsonPath("$.title") { value("Buy milk") }
            jsonPath("$.category.name") { value("Groceries") }
            jsonPath("$.dueDate") { value("2026-10-24") }
            jsonPath("$.priority") { value("HIGH") }
            jsonPath("$.status") { value("TODO") }
        }
    }

    @Test
    fun `refuses a task with a blank title and says which field is wrong`() {
        mockMvc.post("/api/tasks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"   "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.title") { exists() }
        }
    }

    @Test
    fun `refuses a task in a category that does not exist`() {
        mockMvc.post("/api/tasks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Buy milk","categoryId":9999}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `returns 404 for a task that does not exist`() {
        mockMvc.get("/api/tasks/9999").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `lists only the tasks matching the filters`() {
        taskService.create(TaskDetails(title = "Buy milk", priority = Priority.HIGH))
        val done = taskService.create(TaskDetails(title = "Pick up parcel"))
        taskService.changeStatus(assertNotNull(done.id), TaskStatus.DONE)

        mockMvc.get("/api/tasks") { param("status", "TODO") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Buy milk") }
        }
    }

    @Test
    fun `rejects a sort option it does not offer`() {
        mockMvc.get("/api/tasks") { param("sort", "whatever") }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `marks a task done through the status endpoint`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))

        mockMvc.patch("/api/tasks/${assertNotNull(task.id)}/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"DONE"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DONE") }
            jsonPath("$.completedAt") { exists() }
        }
    }

    @Test
    fun `replaces a task through put`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))

        mockMvc.put("/api/tasks/${assertNotNull(task.id)}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Buy oat milk","priority":"LOW","status":"IN_PROGRESS"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Buy oat milk") }
            jsonPath("$.priority") { value("LOW") }
            jsonPath("$.status") { value("IN_PROGRESS") }
        }
    }

    @Test
    fun `deletes a task`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))
        val id = assertNotNull(task.id)

        mockMvc.delete("/api/tasks/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/tasks/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `flags a task whose due date has passed as overdue`() {
        taskService.create(TaskDetails(title = "Overdue", dueDate = LocalDate.now().minusDays(1)))
        taskService.create(TaskDetails(title = "Future", dueDate = LocalDate.now().plusDays(1)))

        mockMvc.get("/api/tasks") { param("sort", "DUE_DATE") }.andExpect {
            status { isOk() }
            jsonPath("$[0].title") { value("Overdue") }
            jsonPath("$[0].overdue") { value(true) }
            jsonPath("$[1].overdue") { value(false) }
        }
    }

    /**
     * The urgent task is created last, so it only reaches page 0 if the
     * database sorts before it pages. Sorting in Kotlin after paging would
     * leave it on the last page.
     */
    @Test
    fun `sorts before paging, so the most urgent task is on the first page`() {
        (1..5).forEach { taskService.create(TaskDetails(title = "Task $it")) }
        taskService.create(TaskDetails(title = "Urgent", priority = Priority.HIGH))

        mockMvc.get("/api/tasks/page") {
            param("page", "0")
            param("size", "2")
            param("sort", "PRIORITY")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].title") { value("Urgent") }
            jsonPath("$.totalElements") { value(6) }
        }
    }
}
