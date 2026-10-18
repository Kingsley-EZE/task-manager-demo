package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * @DataJpaTest spins up only the persistence layer against the in-memory
 * database configured in src/test/resources/application.properties, and rolls
 * each test back afterwards.
 */
@DataJpaTest
class TaskRepositoryTest @Autowired constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {

    @Test
    fun `stores a task with its category and reads it back`() {
        val groceries = categories.save(Category(name = "Groceries"))

        val saved = tasks.save(
            Task(
                title = "Buy milk",
                notes = "Semi-skimmed",
                category = groceries,
                dueDate = LocalDate.of(2026, 10, 24),
                priority = Priority.HIGH,
            ),
        )

        val found = tasks.findById(assertNotNull(saved.id)).orElseThrow()
        assertEquals("Buy milk", found.title)
        assertEquals("Semi-skimmed", found.notes)
        assertEquals("Groceries", found.category?.name)
        assertEquals(LocalDate.of(2026, 10, 24), found.dueDate)
        assertEquals(Priority.HIGH, found.priority)
    }

    @Test
    fun `gives a new task medium priority and todo status by default`() {
        val saved = tasks.save(Task(title = "Something to do"))

        assertEquals(Priority.MEDIUM, saved.priority)
        assertEquals(TaskStatus.TODO, saved.status)
        assertNotNull(saved.createdAt)
        assertNull(saved.completedAt)
    }

    @Test
    fun `allows a task without a category or due date`() {
        val saved = tasks.saveAndFlush(Task(title = "Uncategorised"))

        val found = tasks.findById(assertNotNull(saved.id)).orElseThrow()
        assertNull(found.category)
        assertNull(found.dueDate)
    }
}
