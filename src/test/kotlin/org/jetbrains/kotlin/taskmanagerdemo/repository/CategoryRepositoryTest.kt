package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
class CategoryRepositoryTest @Autowired constructor(
    private val categories: CategoryRepository,
) {

    @Test
    fun `finds a category by name regardless of case`() {
        categories.save(Category(name = "Groceries"))

        val found = categories.findByNameIgnoreCase("groceries")

        assertEquals("Groceries", found?.name)
    }

    @Test
    fun `reports whether a category name is already taken`() {
        categories.save(Category(name = "Work"))

        assertTrue(categories.existsByNameIgnoreCase("WORK"))
        assertFalse(categories.existsByNameIgnoreCase("Home"))
    }

    @Test
    fun `refuses two categories with the same name`() {
        categories.saveAndFlush(Category(name = "Home"))

        assertFailsWith<DataIntegrityViolationException> {
            categories.saveAndFlush(Category(name = "Home"))
        }
    }

    @Test
    fun `lists categories in alphabetical order`() {
        categories.save(Category(name = "Work"))
        categories.save(Category(name = "Errands"))
        categories.save(Category(name = "Groceries"))

        val names = categories.findAllByOrderByNameAsc().map { it.name }

        assertEquals(listOf("Errands", "Groceries", "Work"), names)
    }
}
