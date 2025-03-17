package de.lambda9.ready2race.backend.calls.responses

import de.lambda9.ready2race.backend.calls.pagination.Pagination
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.tailwind.core.KIO
import java.util.*

sealed interface ApiResponse {

    data object NoData : ApiResponse

    data class Dto<T: Any>(
        val dto: T
    ): ApiResponse

    data class Page<T: Any, S: Sortable>(
        val data: List<T>,
        val pagination: Pagination<S>,
    ): ApiResponse

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
        val noData get() = KIO.ok(NoData)
    }
}