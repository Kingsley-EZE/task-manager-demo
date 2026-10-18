package org.jetbrains.kotlin.taskmanagerdemo.setup

import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskDetails
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Puts a short example list in the database the first time the application
 * starts, so the page is not blank on day one.
 *
 * A CommandLineRunner bean is run once, after the application context is ready.
 * Nothing happens if there are already tasks, so your own list is never
 * overwritten and restarting is harmless. Set
 * taskmanager.seed-sample-data=false to switch it off.
 */
@Component
@ConditionalOnProperty(
    name = ["taskmanager.seed-sample-data"],
    havingValue = "true",
    matchIfMissing = true,
)
class SampleDataLoader(
    private val taskService: TaskService,
    private val categoryService: CategoryService,
    private val tasks: TaskRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        if (tasks.count() > 0) return

        val groceries = categoryService.create("Groceries")
        val home = categoryService.create("Home")
        val work = categoryService.create("Work")
        val today = LocalDate.now()

        listOf(
            TaskDetails(
                title = "Buy milk",
                notes = "Semi-skimmed, two bottles",
                categoryId = groceries.id,
                dueDate = today.plusDays(1),
                priority = Priority.HIGH,
            ),
            TaskDetails(title = "Buy coffee beans", categoryId = groceries.id, priority = Priority.LOW),
            TaskDetails(
                title = "Email the landlord",
                notes = "About the kitchen tap",
                categoryId = home.id,
                dueDate = today.minusDays(1),
                priority = Priority.HIGH,
            ),
            TaskDetails(
                title = "Write the project README",
                categoryId = work.id,
                dueDate = today.plusDays(5),
                status = TaskStatus.IN_PROGRESS,
            ),
            TaskDetails(title = "Pick up the parcel", categoryId = home.id, status = TaskStatus.DONE),
        ).forEach(taskService::create)

        log.info("Added {} sample tasks to a new database", tasks.count())
    }
}
