package de.lambda9.ready2race.backend.app.matchResultImportConfig.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class MatchResultImportConfigError : ServiceError {
    NotFound,

    /** Der Wettkampf hat kein Ergebnis-Import-Preset hinterlegt; siehe Zeitnahme-Tab. */
    NotConfigured;

    override fun respond(): ApiError = when(this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "match result import config not found"
        )

        NotConfigured -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No result import preset configured for this competition",
            errorCode = ErrorCode.RESULT_IMPORT_CONFIG_NOT_CONFIGURED,
        )
    }
}
