package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
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

    }
}