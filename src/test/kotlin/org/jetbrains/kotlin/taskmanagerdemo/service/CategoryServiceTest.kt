package org.jetbrains.kotlin.taskmanagerdemo.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Transactional
class CategoryServiceTest @Autowired constructor(
    private val categoryService: CategoryService,
    private val taskService: TaskService,
) {

    @Test
    fun `creates a category and trims its name`() {
        val created = categoryService.create("  Groceries  ")

        assertEquals("Groceries", created.name)
        assertNotNull(created.id)
    }

    @Test
    fun `refuses a duplicate name regardless of case`() {
        categoryService.create("Groceries")

        assertFailsWith<DuplicateNameException> { categoryService.create("groceries") }
    }

    @Test
    fun `refuses a blank name`() {
        assertFailsWith<IllegalArgumentException> { categoryService.create("   ") }
    }

    @Test
    fun `lists categories alphabetically`() {
        categoryService.create("Work")
        categoryService.create("Errands")

        assertEquals(listOf("Errands", "Work"), categoryService.findAll().map { it.name })
    }

    @Test
    fun `deleting a category keeps its tasks but leaves them uncategorised`() {
        val groceries = categoryService.create("Groceries")
        val task = taskService.create(TaskDetails(title = "Buy milk", categoryId = groceries.id))

        categoryService.delete(assertNotNull(groceries.id))

        val survivor = taskService.findById(assertNotNull(task.id))
        assertEquals("Buy milk", survivor.title)
        assertNull(survivor.category)
    }

    @Test
    fun `complains when deleting a category that does not exist`() {
        assertFailsWith<NotFoundException> { categoryService.delete(9999) }
    }
}
