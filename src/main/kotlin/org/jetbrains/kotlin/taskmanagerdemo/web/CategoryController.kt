package org.jetbrains.kotlin.taskmanagerdemo.web

import jakarta.validation.Valid
import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
import org.jetbrains.kotlin.taskmanagerdemo.web.dto.CategoryRequest
import org.jetbrains.kotlin.taskmanagerdemo.web.dto.CategoryResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/categories")
class CategoryController(private val categories: CategoryService) {

    @GetMapping
    fun list(): List<CategoryResponse> = categories.findAll().map(CategoryResponse::from)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CategoryRequest): CategoryResponse =
        CategoryResponse.from(categories.create(request.name))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = categories.delete(id)
}
