package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Category
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Extending JpaRepository gives save/findAll/findById/deleteById for free.
 * The extra methods are derived queries: Spring Data reads the method name and
 * writes the SQL, so there is no implementation class to maintain.
 */
interface CategoryRepository : JpaRepository<Category, Long> {

    fun findByNameIgnoreCase(name: String): Category?

    fun existsByNameIgnoreCase(name: String): Boolean

    fun findAllByOrderByNameAsc(): List<Category>
}
