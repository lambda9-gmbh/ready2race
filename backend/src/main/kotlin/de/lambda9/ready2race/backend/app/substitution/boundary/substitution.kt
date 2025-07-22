package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.substitution() {
    route("/substitution") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                val body = !receiveKIO(SubstitutionRequest.example)
                SubstitutionService.addSubstitution(user.id!!, competitionId, body)
            }
        }

        route("/{substitutionId}") {
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val competitionId = !pathParam("competitionId", uuid)
                    val substitutionId = !pathParam("substitutionId", uuid)

                    SubstitutionService.deleteSubstitution(competitionId, substitutionId)
                }
            }
        }

        route("/possibleSubOuts") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadRegistrationGlobal) // todo: other privilege
                    val competitionId = !pathParam("competitionId", uuid)

                    SubstitutionService.getParticipantsCurrentlyParticipatingInRound(competitionId)
                }
            }
        }
        route("/possibleSubIns/{participantId}") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.ReadRegistrationGlobal) // todo: other privilege
                    val competitionId = !pathParam("competitionId", uuid)
                    val participantId = !pathParam("participantId", uuid)

                    SubstitutionService.getPossibleSubstitutionsForParticipant(
                        competitionId,
                        participantId
                    )
                }
            }
        }
    }
}