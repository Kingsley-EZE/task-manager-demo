package org.jetbrains.kotlin.taskmanagerdemo.web

import org.jetbrains.kotlin.taskmanagerdemo.service.DuplicateNameException
import org.jetbrains.kotlin.taskmanagerdemo.service.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** The shape every error response takes, so clients only parse one format. */
data class ApiError(
    val status: Int,
    val error: String,
    val message: String,
    /** Field name to explanation, filled in only for validation failures. */
    val fieldErrors: Map<String, String> = emptyMap(),
)

/**
 * One place that turns exceptions into HTTP responses. Because this exists,
 * the controllers contain no try/catch and no status-code juggling.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(DuplicateNameException::class)
    fun handleDuplicate(ex: DuplicateNameException) =
        respond(HttpStatus.CONFLICT, ex.message ?: "Already exists")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")

    /** A query parameter that does not parse, e.g. ?sort=whatever. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> {
        val allowed = ex.requiredType?.enumConstants?.joinToString()
        val detail = if (allowed != null) " Expected one of: $allowed." else ""
        return respond(HttpStatus.BAD_REQUEST, "'${ex.value}' is not a valid ${ex.name}.$detail")
    }

    /** A @Valid request body that broke one of its constraints. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "is invalid")
        }
        return respond(HttpStatus.BAD_REQUEST, "The request has invalid fields", fieldErrors)
    }

    private fun respond(
        status: HttpStatus,
        message: String,
        fieldErrors: Map<String, String> = emptyMap(),
    ): ResponseEntity<ApiError> = ResponseEntity
        .status(status)
        .body(ApiError(status.value(), status.reasonPhrase, message, fieldErrors))
}
