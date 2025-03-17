package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.participant() {

    route("/participant") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.PARTICIPANT)
                val clubId = !pathParam("clubId", uuid)
                val params = !pagination<ParticipantSort>()
                ParticipantService.page(params, clubId)
            }
        }

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.PARTICIPANT)
                val clubId = !pathParam("clubId", uuid)
                val payload = !receiveKIO(ParticipantUpsertDto.example)
                ParticipantService.addParticipant(payload, user.id!!, clubId)

            }
        }

        route("/{participantId}") {

            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.PARTICIPANT)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    ParticipantService.getParticipant(id, clubId)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.PARTICIPANT)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    val payload = !receiveKIO(ParticipantUpsertDto.example)
                    ParticipantService.updateParticipant(payload, user.id!!, clubId, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.PARTICIPANT)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    ParticipantService.deleteParticipant(id, clubId)
                }
            }
        }
    }
}