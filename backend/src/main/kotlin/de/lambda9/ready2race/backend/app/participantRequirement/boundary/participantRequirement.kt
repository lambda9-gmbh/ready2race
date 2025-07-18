package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementSort
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.participantRequirement() {
    route("/participantRequirement") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(ParticipantRequirementUpsertDto.example)
                ParticipantRequirementService.addParticipantRequirement(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<ParticipantRequirementSort>()
                ParticipantRequirementService.page(params)
            }
        }

        route("/{participantRequirementId}") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val participantRequirementId = !pathParam("participantRequirementId", uuid)

                    val body = !receiveKIO(ParticipantRequirementUpsertDto.example)
                    ParticipantRequirementService.updateParticipantRequirement(
                        participantRequirementId,
                        body,
                        user.id!!
                    )
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val participantRequirementId = !pathParam("participantRequirementId", uuid)
                    ParticipantRequirementService.deleteParticipantRequirement(participantRequirementId)
                }
            }
        }
    }
}