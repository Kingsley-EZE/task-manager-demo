package org.jetbrains.kotlin.taskmanagerdemo.exercises

import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
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
 * EXERCISE 4 — Search across a relationship
 * ═════════════════════════════════════════
 * Difficulty: ★★★☆☆     Read first: docs/learning/04-queries-four-ways.md
 *
 * GOAL
 *   Make ?q= also match the category name. Searching "groceries" should find
 *   every task filed under Groceries, even when the word appears nowhere in
 *   the title or notes.
 *
 * THE POINT
 *   TaskSpecifications.mentions currently looks at two columns on the tasks
 *   table. Matching a category name means reaching across the @ManyToOne into
 *   another table — a JOIN, written through the Criteria API.
 *
 * WHERE TO WORK
 *   repository/TaskSpecifications.kt   the mentions() function
 *
 * NOTE
 *   Two of the three tests below already pass. They are regression guards:
 *   they must STILL pass when you are done. Breaking them is the normal way
 *   this exercise goes wrong, which is precisely why they are here.
 *
 * HINTS
 *   - root.join<Task, Category>("category", JoinType.LEFT) gives you the other
 *     table. LEFT matters: an INNER join would silently drop every
 *     uncategorised task from the results, including ones whose title matches.
 *   - Add the joined column as a third branch of the existing cb.or(…).
 *   - A null column never matches a LIKE, which is why this needs no special
 *     handling for tasks that have no category.
 *
 * GOING FURTHER
 *   Turn on SQL logging and look at what your specification produces:
 *
 *       logging.level.org.hibernate.SQL=DEBUG
 *
 *   Run the search with and without a category filter and compare the two
 *   statements. Being able to read the SQL your code generates is the skill
 *   that separates people who use an ORM from people who are used by one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Exercise4SpecificationJoinTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val taskService: TaskService,
    private val categoryService: CategoryService,
) {

    @Test
    fun `finds tasks by the name of their category`() {
        val groceries = categoryService.create("Groceries")
        taskService.create(TaskDetails(title = "Pick up the parcel", categoryId = groceries.id))
        taskService.create(TaskDetails(title = "Write the report"))

        mockMvc.get("/api/tasks") { param("q", "groceries") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Pick up the parcel") }
        }
    }

    @Test
    fun `still finds tasks by title and notes`() {
        categoryService.create("Groceries")
        taskService.create(TaskDetails(title = "Buy milk"))
        taskService.create(TaskDetails(title = "Call the plumber", notes = "about the milk stain"))

        mockMvc.get("/api/tasks") { param("q", "milk") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `does not lose uncategorised tasks from the results`() {
        taskService.create(TaskDetails(title = "Buy milk"))

        // The classic INNER JOIN bug: this task has no category at all, and a
        // title match must still find it.
        mockMvc.get("/api/tasks") { param("q", "milk") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }
}
