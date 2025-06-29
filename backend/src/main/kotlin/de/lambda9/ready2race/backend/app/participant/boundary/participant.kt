package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*

fun Route.participant() {

    route("/participant") {

        get {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                val clubId = !pathParam("clubId", uuid)
                val params = !pagination<ParticipantSort>()
                ParticipantService.page(params, clubId, user, scope)
            }
        }

        post {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                val clubId = !pathParam("clubId", uuid)
                val payload = !receiveKIO(ParticipantUpsertDto.example)
                if (scope == Privilege.Scope.OWN && clubId != user.club) {
                    KIO.fail(AuthError.PrivilegeMissing)
                } else {
                    ParticipantService.addParticipant(payload, user.id!!, clubId)
                }
            }
        }

        route("/{participantId}") {

            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    ParticipantService.getParticipant(id, clubId, user, scope)
                }
            }

            put {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    val payload = !receiveKIO(ParticipantUpsertDto.example)
                    ParticipantService.updateParticipant(payload, user.id!!, clubId, id, user, scope)
                }
            }

            delete {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                    val id = !pathParam("participantId", uuid)
                    val clubId = !pathParam("clubId", uuid)
                    ParticipantService.deleteParticipant(id, clubId, user, scope)
                }
            }
        }
    }
}