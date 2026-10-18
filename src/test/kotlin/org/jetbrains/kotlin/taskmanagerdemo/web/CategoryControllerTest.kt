package org.jetbrains.kotlin.taskmanagerdemo.web

import org.jetbrains.kotlin.taskmanagerdemo.service.CategoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val categoryService: CategoryService,
) {

    @Test
    fun `creates a category`() {
        mockMvc.post("/api/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Groceries"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Groceries") }
        }
    }

    @Test
    fun `refuses a blank category name`() {
        mockMvc.post("/api/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"  "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.name") { exists() }
        }
    }

    @Test
    fun `refuses a name that is already taken`() {
        categoryService.create("Groceries")

        mockMvc.post("/api/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"groceries"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `lists categories alphabetically`() {
        categoryService.create("Work")
        categoryService.create("Errands")

        mockMvc.get("/api/categories").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Errands") }
            jsonPath("$[1].name") { value("Work") }
        }
    }

    @Test
    fun `deletes a category`() {
        val category = categoryService.create("Groceries")

        mockMvc.delete("/api/categories/${assertNotNull(category.id)}")
            .andExpect { status { isNoContent() } }
    }
}
