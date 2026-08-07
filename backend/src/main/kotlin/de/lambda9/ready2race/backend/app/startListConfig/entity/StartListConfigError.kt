package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class StartListConfigError : ServiceError {
    NotFound,

    /**
     * Der Wettkampf hat kein Startlisten-Preset hinterlegt, also gibt es keine Spaltenbelegung fuer den
     * CSV-Export. Vor dem Zeitnahme-Tab wurde das Preset bei jedem Download einzeln gewaehlt; jetzt
     * gehoert es zum Wettkampf, und die Oberflaeche verweist bei diesem Code dorthin.
     */
    NotConfigured;

    override fun respond(): ApiError = when(this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Start list config not found"
        )

        NotConfigured -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No start list export preset configured for this competition",
            errorCode = ErrorCode.STARTLIST_CONFIG_NOT_CONFIGURED,
        )
    }
}
