package org.jetbrains.kotlin.taskmanagerdemo.domain

/** How urgent a task is. Stored in the database as its name, e.g. "HIGH". */
enum class Priority(val rank: Int) { LOW(1), MEDIUM(2), HIGH(3) }
