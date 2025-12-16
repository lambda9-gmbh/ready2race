package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.certificate.entity.CertificateError
import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.certificate() {
    route("/certificatesOfParticipation") {

        post("/sendToParticipants") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)

                val eventId = !pathParam("eventId", uuid)

                CertificateService.createCertificateOfParticipationJobs(eventId)
            }
        }

        get {
            call.respondComprehension {
                val user = !authenticate(Privilege.ReadClubOwn)
                val eventId = !pathParam("eventId", uuid)
                val club = user.club
                if (club != null) {
                    CertificateService.downloadCertificatesOfParticipation(eventId, club)
                } else {
                    KIO.fail(CertificateError.NoResults) // TODO: better typed error please
                }
            }
        }

        get("/{participantId}") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                val eventId = !pathParam("eventId", uuid)
                val participantId = !pathParam("participantId", uuid)

                CertificateService.downloadCertificateOfParticipation(eventId, participantId, user, scope)
            }
        }

    }
}