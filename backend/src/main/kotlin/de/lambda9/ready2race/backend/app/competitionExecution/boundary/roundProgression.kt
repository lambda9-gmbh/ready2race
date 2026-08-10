package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.entity.RoundProgressionConfigRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Die Folgerunden-Automatik eines Wettkampfs — unterhalb der Wettkampf-Route zu mounten, nach dem
 * Vorbild von `timingConfig()`.
 */
fun Route.roundProgression() {
    route("/roundProgression") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.getRoundProgressionConfig(eventId, competitionId)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                val body = !receiveKIO(RoundProgressionConfigRequest.example)
                CompetitionExecutionService.updateRoundProgressionConfig(
                    competitionId = competitionId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}
