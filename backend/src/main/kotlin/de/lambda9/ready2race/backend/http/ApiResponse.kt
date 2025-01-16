package de.lambda9.ready2race.backend.http

import de.lambda9.ready2race.backend.app.App
import io.ktor.http.*
import java.util.*

sealed class ApiResponse(
    open val status: HttpStatusCode = HttpStatusCode.OK
) {

    data object NoData : ApiResponse(HttpStatusCode.NoContent)

    data class Dto<T: Any>(
        val dto: T,
        override val status: HttpStatusCode = HttpStatusCode.OK
    ): ApiResponse(status)

    data class Page<T: Any, S: Sortable>(
        override val data: List<T>,
        override val pagination: Pagination<S>,
    ): ApiResponse(), LimitedResult<T, S>

    data class File(
        val bytes: ByteArray,
        val contentType: ContentType
    ): ApiResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as File

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data class ID(
        val id: UUID,
        override val status: HttpStatusCode = HttpStatusCode.OK
    ): ApiResponse(status)

    companion object {
        val noData get() = App.ok(NoData)
    }
}