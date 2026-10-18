package org.jetbrains.kotlin.taskmanagerdemo.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A grouping for tasks, such as "Groceries" or "Work".
 *
 * Entities are plain classes with `var` properties rather than data classes:
 * a data class derives equals/hashCode from every property, which breaks once
 * Hibernate assigns an id to an object you are already holding.
 */
@Entity
@Table(name = "categories")
class Category(
    @Column(nullable = false, unique = true, length = 60)
    var name: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
