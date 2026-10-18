package org.jetbrains.kotlin.taskmanagerdemo.repository

import org.jetbrains.kotlin.taskmanagerdemo.domain.Task
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * JpaSpecificationExecutor adds findAll(Specification, Sort), which lets the
 * service compose filters at runtime instead of needing a derived method for
 * every combination of status, category, priority and search term.
 */
interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>
