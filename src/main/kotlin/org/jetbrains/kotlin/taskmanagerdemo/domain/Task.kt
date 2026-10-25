package org.jetbrains.kotlin.taskmanagerdemo.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/** A single thing to do. */
@Entity
@Table(name = "tasks")
class Task(
    @Column(nullable = false, length = 200)
    var title: String,

    @Column(length = 2000)
    var notes: String? = null,

    /**
     * Many tasks point at one category; the category_id column lives on this
     * table. Fetched eagerly because a category is one small row and the task
     * is always mapped to a response while it is still attached.
     */
    @ManyToOne
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    var dueDate: LocalDate? = null,

    /**
     * EnumType.STRING stores "HIGH" rather than the ordinal 2, so reordering
     * the enum later cannot silently rewrite the meaning of existing rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var priority: Priority = Priority.MEDIUM,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TaskStatus = TaskStatus.TODO,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    /** Stamped when the task moves to DONE, cleared when it moves back. */
    var completedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @Column(nullable = false)
    var priorityRank: Int = priority.rank
            protected set

    @PrePersist
    @PreUpdate
    protected fun syncPriorityRank(){
        priorityRank = priority.rank
    }
}
