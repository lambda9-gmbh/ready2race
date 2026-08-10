package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.awardCertificate() {

    route("/awardCertificates") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val options = awardCertificateOptions()
                val format = certificateFormat()

                AwardCertificateService.downloadForEvent(eventId, options, format)
            }
        }
    }

    route("/competition/{competitionId}/awardCertificates") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)
                val options = awardCertificateOptions()
                val format = certificateFormat()

                AwardCertificateService.downloadForCompetition(eventId, competitionId, options, format)
            }
        }

        get("/{registrationId}") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)
                val registrationId = !pathParam("registrationId", uuid)
                val options = awardCertificateOptions()
                val format = certificateFormat()

                AwardCertificateService.downloadForRegistration(
                    eventId,
                    competitionId,
                    registrationId,
                    options,
                    format,
                )
            }
        }
    }
}

/**
 * Die Query-Parameter der drei Urkunden-Endpunkte, mit den dokumentierten Defaults.
 */
private fun CallComprehensionScope.awardCertificateOptions(): AwardCertificateOptions {
    val maxPlace = !optionalQueryParam("maxPlace", int) ?: AwardCertificateOptions.DEFAULT_MAX_PLACE
    val mode = !optionalQueryParam("mode", enum<AwardCertificateMode>()) ?: AwardCertificateMode.PER_ATHLETE
    val withBackground = !optionalQueryParam("background", boolean) ?: false
    val printRatingCategory = !optionalQueryParam("ratingCategory", boolean) ?: false

    return AwardCertificateOptions(
        maxPlace = maxPlace,
        mode = mode,
        withBackground = withBackground,
        printRatingCategory = printRatingCategory,
    )
}

/**
 * `format` kommt kleingeschrieben an (`pdf`, `docx`), anders als die übrigen Enum-Query-Parameter.
 * Wird auch von den Teilnahmeurkunden-Routen in `certificate.kt` genutzt, damit es dafür nur
 * diesen einen Parser gibt.
 */
internal val certificateFormatParser = Parser<AwardCertificateService.Format> { value ->
    when (value.lowercase()) {
        "pdf" -> AwardCertificateService.Format.PDF
        "docx" -> AwardCertificateService.Format.DOCX
        else -> throw IllegalArgumentException("Unknown format: $value")
    }
}

internal fun CallComprehensionScope.certificateFormat(): AwardCertificateService.Format =
    !optionalQueryParam("format", certificateFormatParser) ?: AwardCertificateService.Format.PDF
