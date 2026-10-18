package org.jetbrains.kotlin.taskmanagerdemo.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.jetbrains.kotlin.taskmanagerdemo.domain.Category

/** What a client may send when creating a category. */
data class CategoryRequest(
    @field:NotBlank(message = "A category needs a name")
    @field:Size(max = 60, message = "A category name can be at most 60 characters")
    val name: String,
)

/** What the API sends back. Kept separate from the entity so the database
 *  schema and the published API can change independently. */
data class CategoryResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(category: Category) = CategoryResponse(
            id = requireNotNull(category.id) { "A saved category always has an id" },
            name = category.name,
        )
    }
}
