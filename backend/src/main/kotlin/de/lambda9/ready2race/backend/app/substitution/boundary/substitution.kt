package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.substitution() {
    route("/substitution") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(SubstitutionRequest.example)
                SubstitutionService.addSubstitution(user.id!!, body)
            }
        }

        route("/{substitutionId}") {
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val substitutionId = !pathParam("substitutionId", uuid)

                    SubstitutionService.deleteSubstitution(substitutionId)
                }
            }
        }
        route("{competitionSetupRoundId}") {

            route("/possibleSubOuts") {
                get {
                    call.respondComprehension {
                        !authenticate(Privilege.ReadRegistrationGlobal) // todo: other privilege
                        val competitionSetupRoundId = !pathParam("competitionSetupRoundId", uuid)

                        SubstitutionService.getParticipantsCurrentlyParticipatingInRound(competitionSetupRoundId)
                    }
                }
            }

            route("/possibleSubIns") {
                get {
                    call.respondComprehension {
                        !authenticate(Privilege.ReadRegistrationGlobal) // todo: other privilege
                        val competitionSetupRoundId = !pathParam("competitionSetupRoundId", uuid)
                        val participantId = !queryParam("participantId", uuid)

                        SubstitutionService.getPossibleSubstitutionsForParticipant(
                            competitionSetupRoundId,
                            participantId
                        )
                    }
                }
            }
        }
    }
}