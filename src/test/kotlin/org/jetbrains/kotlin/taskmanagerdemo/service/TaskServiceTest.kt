package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * These run against the real repositories and a real (in-memory) database
 * rather than mocks, so a rule that only works in theory cannot pass.
 * @Transactional rolls each test back, leaving the next one a clean slate.
 */
@SpringBootTest
@Transactional
class TaskServiceTest @Autowired constructor(
    private val taskService: TaskService,
    private val categoryService: CategoryService,
) {

    @Test
    fun `creates a task in the given category`() {
        val groceries = categoryService.create("Groceries")

        val task = taskService.create(
            TaskDetails(
                title = "Buy milk",
                categoryId = groceries.id,
                dueDate = LocalDate.of(2026, 10, 24),
                priority = Priority.HIGH,
            ),
        )

        assertEquals("Groceries", task.category?.name)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals(TaskStatus.TODO, task.status)
    }

    @Test
    fun `refuses a task pointing at a category that does not exist`() {
        assertFailsWith<NotFoundException> {
            taskService.create(TaskDetails(title = "Buy milk", categoryId = 9999))
        }
    }

    @Test
    fun `stamps the completion time when a task is marked done`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))

        val done = taskService.changeStatus(assertNotNull(task.id), TaskStatus.DONE)

        assertEquals(TaskStatus.DONE, done.status)
        assertNotNull(done.completedAt)
    }

    @Test
    fun `clears the completion time when a done task is reopened`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))
        val id = assertNotNull(task.id)
        taskService.changeStatus(id, TaskStatus.DONE)

        val reopened = taskService.changeStatus(id, TaskStatus.IN_PROGRESS)

        assertNull(reopened.completedAt)
    }

    @Test
    fun `keeps the original completion time when an already done task is marked done again`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))
        val id = assertNotNull(task.id)
        val firstCompletedAt = taskService.changeStatus(id, TaskStatus.DONE).completedAt

        val again = taskService.changeStatus(id, TaskStatus.DONE)

        assertEquals(firstCompletedAt, again.completedAt)
    }

    @Test
    fun `updates every field and can remove the category`() {
        val groceries = categoryService.create("Groceries")
        val task = taskService.create(TaskDetails(title = "Buy milk", categoryId = groceries.id))

        val updated = taskService.update(
            assertNotNull(task.id),
            TaskDetails(
                title = "Buy oat milk",
                notes = "The barista one",
                version = task.version,
                categoryId = null,
                dueDate = LocalDate.of(2026, 11, 1),
                priority = Priority.LOW,
                status = TaskStatus.IN_PROGRESS,
            ),
        )

        assertEquals("Buy oat milk", updated.title)
        assertEquals("The barista one", updated.notes)
        assertNull(updated.category)
        assertEquals(LocalDate.of(2026, 11, 1), updated.dueDate)
        assertEquals(Priority.LOW, updated.priority)
        assertEquals(TaskStatus.IN_PROGRESS, updated.status)
    }

    @Test
    fun `filters tasks by status`() {
        taskService.create(TaskDetails(title = "Still to do"))
        val done = taskService.create(TaskDetails(title = "Already done"))
        taskService.changeStatus(assertNotNull(done.id), TaskStatus.DONE)

        val found = taskService.findAll(TaskFilter(status = TaskStatus.DONE))

        assertEquals(listOf("Already done"), found.map { it.title })
    }

    @Test
    fun `filters tasks by category`() {
        val groceries = categoryService.create("Groceries")
        val work = categoryService.create("Work")
        taskService.create(TaskDetails(title = "Buy milk", categoryId = groceries.id))
        taskService.create(TaskDetails(title = "Write report", categoryId = work.id))

        val found = taskService.findAll(TaskFilter(categoryId = groceries.id))

        assertEquals(listOf("Buy milk"), found.map { it.title })
    }

    @Test
    fun `searches titles and notes without caring about case`() {
        taskService.create(TaskDetails(title = "Buy MILK"))
        taskService.create(TaskDetails(title = "Call the plumber", notes = "about the milk stain"))
        taskService.create(TaskDetails(title = "Write report"))

        val found = taskService.findAll(TaskFilter(query = "milk"))

        assertEquals(setOf("Buy MILK", "Call the plumber"), found.map { it.title }.toSet())
    }

    @Test
    fun `sorts by due date and puts undated tasks last`() {
        taskService.create(TaskDetails(title = "No date"))
        taskService.create(TaskDetails(title = "Later", dueDate = LocalDate.of(2026, 12, 1)))
        taskService.create(TaskDetails(title = "Sooner", dueDate = LocalDate.of(2026, 10, 20)))

        val found = taskService.findAll(TaskFilter(), TaskSort.DUE_DATE)

        assertEquals(listOf("Sooner", "Later", "No date"), found.map { it.title })
    }

    @Test
    fun `sorts by priority with the most urgent first`() {
        taskService.create(TaskDetails(title = "Low", priority = Priority.LOW))
        taskService.create(TaskDetails(title = "High", priority = Priority.HIGH))
        taskService.create(TaskDetails(title = "Medium", priority = Priority.MEDIUM))

        val found = taskService.findAll(TaskFilter(), TaskSort.PRIORITY)

        assertEquals(listOf("High", "Medium", "Low"), found.map { it.title })
    }

    @Test
    fun `re-ranks a task whose priority changes`() {
        taskService.create(TaskDetails(title = "Medium", priority = Priority.MEDIUM))
        val promoted = taskService.create(TaskDetails(title = "Promoted", priority = Priority.LOW))

        taskService.update(
            assertNotNull(promoted.id),
            TaskDetails(title = "Promoted", priority = Priority.HIGH, version = promoted.version),
        )
        val found = taskService.findAll(TaskFilter(), TaskSort.PRIORITY)

        assertEquals(listOf("Promoted", "Medium"), found.map { it.title })
    }

    @Test
    fun `complains when asked for a task that does not exist`() {
        assertFailsWith<NotFoundException> { taskService.findById(9999) }
        assertFailsWith<NotFoundException> { taskService.delete(9999) }
    }

    @Test
    fun `deletes a task`() {
        val task = taskService.create(TaskDetails(title = "Buy milk"))
        val id = assertNotNull(task.id)

        taskService.delete(id)

        assertFailsWith<NotFoundException> { taskService.findById(id) }
    }
}
