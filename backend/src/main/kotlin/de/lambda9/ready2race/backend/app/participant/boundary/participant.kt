package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.participant() {

    route("/participant") {

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.PARTICIPANT)
                    val clubId = !pathParam("clubId") { UUID.fromString(it) }
                    val params = !pagination<ParticipantSort>()
                    ParticipantService.page(params, clubId)
                }
            }
        }

        post {
            val payload = call.receiveV(ParticipantUpsertDto.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.PARTICIPANT)
                    val clubId = !pathParam("clubId") { UUID.fromString(it) }

                    ParticipantService.addParticipant(!payload, user.id!!, clubId)

                }
            }
        }

        route("/{participantId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.PARTICIPANT)
                        val id = !pathParam("participantId") { UUID.fromString(it) }
                        val clubId = !pathParam("clubId") { UUID.fromString(it) }
                        ParticipantService.getParticipant(id, clubId)
                    }
                }
            }

            put {
                val payload = call.receiveV(ParticipantUpsertDto.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.PARTICIPANT)
                        val id = !pathParam("participantId") { UUID.fromString(it) }
                        val clubId = !pathParam("clubId") { UUID.fromString(it) }
                        ParticipantService.updateParticipant(!payload, user.id!!, clubId, id)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.PARTICIPANT)
                        val id = !pathParam("participantId") { UUID.fromString(it) }
                        val clubId = !pathParam("clubId") { UUID.fromString(it) }
                        ParticipantService.deleteParticipant(id, clubId)
                    }
                }
            }
        }
    }
}