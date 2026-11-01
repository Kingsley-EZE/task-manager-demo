package org.jetbrains.kotlin.taskmanagerdemo.service

/** Thrown when an id does not match anything. The web layer turns it into a 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** Thrown when a name is already taken. The web layer turns it into a 409. */
class DuplicateNameException(message: String) : RuntimeException(message)

class TaskEditConflictException(message: String) : RuntimeException(message)
