package org.jetbrains.kotlin.taskmanagerdemo.setup

import org.jetbrains.kotlin.taskmanagerdemo.repository.CategoryRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The loader is built by hand rather than autowired: sample data is switched
 * off in tests (see src/test/resources/application.properties), so there is no
 * bean to inject.
 */
@SpringBootTest
@Transactional
class SampleDataLoaderTest @Autowired constructor(
    private val taskService: TaskService,
    private val categoryService: CategoryService,
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {

    private fun loader() = SampleDataLoader(taskService, categoryService, tasks)

    @Test
    fun `fills an empty database with something to look at`() {
        loader().run()

        assertTrue(tasks.count() > 0, "expected sample tasks")
        assertTrue(categories.count() > 0, "expected sample categories")
    }

    @Test
    fun `leaves an existing list alone`() {
        taskService.create(TaskDetails(title = "My own task"))

        loader().run()

        assertEquals(listOf("My own task"), tasks.findAll().map { it.title })
        assertEquals(0, categories.count())
    }

    @Test
    fun `is safe to run twice`() {
        loader().run()
        val afterFirstRun = tasks.count()

        loader().run()

        assertEquals(afterFirstRun, tasks.count())
    }
}
