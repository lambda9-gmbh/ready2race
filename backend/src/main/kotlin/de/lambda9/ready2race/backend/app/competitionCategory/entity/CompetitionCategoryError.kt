package de.lambda9.ready2race.backend.app.competitionCategory.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionsOrTemplatesContainingReference
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

sealed interface CompetitionCategoryError : ServiceError {
    data object NotFound : CompetitionCategoryError

    data class CompetitionCategoryInUse(
        val competitionsOrTemplates: CompetitionsOrTemplatesContainingReference
    ) : CompetitionCategoryError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "CompetitionCategory not Found"
        )

        is CompetitionCategoryInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = competitionsOrTemplates.errorMessage("CompetitionCategory"),
            details = mapOf("entitiesContainingCompetitionCategory" to competitionsOrTemplates)
        )
    }
}