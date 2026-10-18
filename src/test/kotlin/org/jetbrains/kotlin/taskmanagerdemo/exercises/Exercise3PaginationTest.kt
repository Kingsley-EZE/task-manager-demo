package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * EXERCISE 3 — Pages instead of everything
 * ════════════════════════════════════════
 * Difficulty: ★★★☆☆     Read first: docs/learning/03-repositories-without-code.md
 *
 * GOAL
 *   Add GET /api/tasks/page?page=0&size=2, returning one page at a time:
 *
 *       {"content": [ …tasks… ], "totalElements": 5, "totalPages": 3,
 *        "page": 0, "size": 2}
 *
 *   The existing GET /api/tasks keeps working unchanged — this is a new route,
 *   so nothing that already passes breaks.
 *
 * THE POINT
 *   Returning an unbounded list is fine for a personal task list and a bug
 *   waiting to happen anywhere else. Paging is the fix, and Spring Data has it
 *   built in: findAll(Pageable) gives you a Page<T>, which carries the rows
 *   plus the total count.
 *
 * WHERE TO WORK
 *   service/TaskService.kt   fun findPage(filter, sort, pageable): Page<Task>
 *   web/dto/TaskDtos.kt      a small PageResponse<T> wrapper
 *   web/TaskController.kt    @GetMapping("/page")
 *
 * HINTS
 *   - TaskRepository already extends JpaSpecificationExecutor, which has
 *     findAll(Specification, Pageable). You need no new repository method.
 *   - Build a Pageable with PageRequest.of(page, size).
 *   - Declare /page before /{id}, or Spring will try to read "page" as a Long
 *     and you will get a 400 instead of your new endpoint.
 *   - Do NOT return Spring's Page type straight out of the controller. Its
 *     JSON shape is an implementation detail that has changed between Spring
 *     versions; map it to your own DTO so your API contract is yours.
 *   - TaskSort sorts in Kotlin, not SQL (chapter 1 explains why), so sorting a
 *     page sorts only that page. Noticing this is part of the exercise — see
 *     below.
 *
 * GOING FURTHER
 *   That last hint is a real bug. Sorting after paging sorts the wrong things.
 *   Fix it properly: give Priority a sortable column so the database can do
 *   the ordering. Add `priorityRank: Int` to Task, keep it in step with
 *   priority, and pass a real Sort into the Pageable. Then TaskSort can go
 *   away entirely. This is the most realistic piece of work in the whole set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Exercise3PaginationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
) {

    private fun givenFiveTasks() =
        (1..5).forEach { taskService.create(TaskDetails(title = "Task $it")) }

    @Test
    fun `returns one page of tasks`() {
        givenFiveTasks()

        mockMvc.get("/api/tasks/page") {
            param("page", "0")
            param("size", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.totalElements") { value(5) }
            jsonPath("$.totalPages") { value(3) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(2) }
        }
    }

    @Test
    fun `returns the last partial page`() {
        givenFiveTasks()

        mockMvc.get("/api/tasks/page") {
            param("page", "2")
            param("size", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.totalElements") { value(5) }
        }
    }

    @Test
    fun `pages past the end are empty rather than an error`() {
        givenFiveTasks()

        mockMvc.get("/api/tasks/page") {
            param("page", "99")
            param("size", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(0) }
            jsonPath("$.totalElements") { value(5) }
        }
    }

    @Test
    fun `still applies the filters`() {
        givenFiveTasks()

        mockMvc.get("/api/tasks/page") {
            param("page", "0")
            param("size", "10")
            param("q", "Task 3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].title") { value("Task 3") }
        }
    }
}
