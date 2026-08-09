package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.awardCeremony() {

    route("/awardCeremony") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                AwardCeremonyService.listCeremonies(eventId)
            }
        }

        // POST, obwohl es ein Download ist: die Auswahl umfasst bei einer Regatta mit 40 Rennen
        // leicht hundert Schlüssel und passt nicht mehr sinnvoll in einen Query-String.
        post("/pdf") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(AwardCeremonySelectionRequest.example)

                AwardCeremonyService.download(eventId, body)
            }
        }
    }
}
