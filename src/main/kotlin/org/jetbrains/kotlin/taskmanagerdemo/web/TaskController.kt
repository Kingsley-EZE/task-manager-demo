package org.jetbrains.kotlin.taskmanagerdemo.web

import jakarta.validation.Valid
import org.jetbrains.kotlin.taskmanagerdemo.domain.Priority
import org.jetbrains.kotlin.taskmanagerdemo.domain.TaskStatus
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskFilter
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskService
import org.jetbrains.kotlin.taskmanagerdemo.service.TaskSort
import org.jetbrains.kotlin.taskmanagerdemo.web.dto.StatusRequest
import org.jetbrains.kotlin.taskmanagerdemo.web.dto.TaskRequest
import org.jetbrains.kotlin.taskmanagerdemo.web.dto.TaskResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate

@RestController
@RequestMapping("/api/tasks")
class TaskController(private val tasks: TaskService) {

    /**
     * Every filter is optional. Because the parameters are typed, Spring
     * rejects a nonsense value such as ?status=banana before this method runs
     * and GlobalExceptionHandler turns that into a 400.
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) priority: Priority?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dueBefore: LocalDate?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "DUE_DATE") sort: TaskSort,
    ): List<TaskResponse> =
        tasks.findAll(TaskFilter(status, categoryId, priority, dueBefore, q), sort)
            .map { TaskResponse.from(it) }

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long): TaskResponse = TaskResponse.from(tasks.findById(id))

    @PostMapping
    fun create(@Valid @RequestBody request: TaskRequest): ResponseEntity<TaskResponse> {
        val created = TaskResponse.from(tasks.create(request.toDetails()))
        // 201 with a Location header pointing at the new resource.
        return ResponseEntity.created(URI.create("/api/tasks/${created.id}")).body(created)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: TaskRequest): TaskResponse =
        TaskResponse.from(tasks.update(id, request.toDetails()))

    /** Ticking a box should not require sending the whole task back. */
    @PatchMapping("/{id}/status")
    fun changeStatus(@PathVariable id: Long, @RequestBody request: StatusRequest): TaskResponse =
        TaskResponse.from(tasks.changeStatus(id, request.status))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = tasks.delete(id)
}
