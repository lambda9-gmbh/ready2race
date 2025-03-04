package de.lambda9.ready2race.backend.responses

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.pagination.Pagination
import de.lambda9.ready2race.backend.pagination.ResponsePage
import de.lambda9.ready2race.backend.pagination.Sortable
import io.ktor.http.*
import java.util.*

sealed interface ApiResponse {

    data object NoData : ApiResponse

    data class Dto<T: Any>(
        val dto: T
    ): ApiResponse

    data class Page<T: Any, S: Sortable>(
        override val data: List<T>,
        override val pagination: Pagination<S>,
    ): ApiResponse, ResponsePage<T, S>

    data class File(
        val name: String,
        val bytes: ByteArray
    ): ApiResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as File

            if (name != other.name) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data class Created(
        val id: UUID
    ): ApiResponse

    companion object {
        val noData get() = App.ok(NoData)
    }
}