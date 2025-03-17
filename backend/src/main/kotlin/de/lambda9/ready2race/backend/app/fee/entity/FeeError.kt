package de.lambda9.ready2race.backend.app.fee.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionsOrTemplatesContainingReference
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface FeeError : ServiceError {
    data object NotFound : FeeError

    data class FeeInUse(val competitionsOrTemplates: CompetitionsOrTemplatesContainingReference) :
        FeeError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Fee not found"
        )

        is FeeInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = competitionsOrTemplates.errorMessage("Fee"),
            details = mapOf("entitiesContainingFee" to competitionsOrTemplates)
        )
    }
}
