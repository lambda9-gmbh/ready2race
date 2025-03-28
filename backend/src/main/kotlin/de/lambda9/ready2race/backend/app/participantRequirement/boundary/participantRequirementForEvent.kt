package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementCheckForEventConfigDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementCheckForEventUpsertDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementForEventSort
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.participantRequirementForEvent() {
    route("/participantRequirement") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<ParticipantRequirementForEventSort>()
                val eventId = !pathParam("eventId", uuid)
                ParticipantRequirementService.pageForEvent(params, eventId)
            }
        }
        post {
            call.respondComprehension {

                val multiPartData = call.receiveMultipart()

                val uploads = mutableListOf<Pair<String, ByteArray>>()
                var config: ParticipantRequirementCheckForEventConfigDto? = null

                var done = false
                while (!done) {
                    val part = multiPartData.readPart()
                    if (part == null) {
                        done = true
                    } else {
                        when (part) {
                            is PartData.FileItem -> {
                                uploads.add(
                                    part.originalFileName!! to part.provider().toByteArray()
                                )
                            }

                            is PartData.FormItem -> {
                                if (part.name == "config") {
                                    config = jsonMapper.readValue(
                                        part.value,
                                        ParticipantRequirementCheckForEventConfigDto::class.java
                                    )
                                }
                            }

                            else -> {}
                        }
                        part.dispose()
                    }
                }

                val (user, _) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId", uuid)
                ParticipantRequirementService.checkRequirementForEvent(
                    eventId,
                    uploads,
                    config!!,
                    user.id!!
                )
            }
        }

        route("/active") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<ParticipantRequirementForEventSort>()
                    val eventId = !pathParam("eventId", uuid)
                    ParticipantRequirementService.getActiveForEvent(params, eventId)
                }
            }
        }

        route("/approve") {
            post {
                call.respondComprehension {
                    val (user) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId", uuid)
                    val body = !receiveKIO(ParticipantRequirementCheckForEventUpsertDto.example)
                    ParticipantRequirementService.approveRequirementForEvent(eventId, body, user.id!!)
                }
            }
        }

        route("/{participantRequirementId}") {


            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
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
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val participantRequirementId = !pathParam("participantRequirementId", uuid)
                    ParticipantRequirementService.deleteParticipantRequirement(participantRequirementId)
                }
            }
        }
    }
}