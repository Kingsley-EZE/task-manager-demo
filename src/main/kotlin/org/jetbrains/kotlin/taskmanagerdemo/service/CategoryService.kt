package org.jetbrains.kotlin.taskmanagerdemo.service

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.jetbrains.kotlin.taskmanagerdemo.repository.CategoryRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskRepository
import org.jetbrains.kotlin.taskmanagerdemo.repository.TaskSpecifications
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @Transactional on the class wraps every public method in a database
 * transaction, so a method that fails part-way leaves nothing half-written.
 */
@Service
@Transactional
class CategoryService(
    private val categories: CategoryRepository,
    private val tasks: TaskRepository,
) {

    @Transactional(readOnly = true)
    fun findAll(): List<Category> = categories.findAllByOrderByNameAsc()

    @Transactional(readOnly = true)
    fun findById(id: Long): Category =
        categories.findById(id).orElseThrow { NotFoundException("No category with id $id") }

    fun create(name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A category needs a name" }
        if (categories.existsByNameIgnoreCase(trimmed)) {
            throw DuplicateNameException("A category called \"$trimmed\" already exists")
        }
        return categories.save(Category(name = trimmed))
    }

    /**
     * Deleting a category does not delete its tasks: the tasks are the point,
     * the grouping is incidental. They are detached first so the delete cannot
     * fail on the foreign key.
     */
    fun delete(id: Long) {
        val category = findById(id)
        val orphaned = tasks.findAll(TaskSpecifications.inCategory(id))
        orphaned.forEach { it.category = null }
        tasks.saveAll(orphaned)
        tasks.flush()
        categories.delete(category)
    }
}
