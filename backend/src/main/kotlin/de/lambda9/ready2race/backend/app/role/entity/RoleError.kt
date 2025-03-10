package de.lambda9.ready2race.backend.app.role.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ErrorCode
import io.ktor.http.*
import java.util.*

sealed interface RoleError : ServiceError {

    data object Static : RoleError
    data object NotFound : RoleError
    data class CannotAssignRoles(val notFound: List<UUID>, val notAssignable: List<UUID>) : RoleError

    override fun respond(): ApiError = when (this) {
        NotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "Role not found",
            )
        is CannotAssignRoles ->
            ApiError(
                status = HttpStatusCode.Conflict,
                message = "Cannot assign some roles",
                details = listOfNotNull(
                    notFound.takeIf { it.isNotEmpty() }?.let { "notFound" to it },
                    notAssignable.takeIf { it.isNotEmpty() }?.let { "notAssignable" to it }
                ).toMap(),
                errorCode = ErrorCode.CANNOT_ASSIGN_ROLES,
            )

        Static ->
            ApiError(
                status = HttpStatusCode.Forbidden,
                message = "Role is static and cannot be changed or assigned",
            )
    }
}