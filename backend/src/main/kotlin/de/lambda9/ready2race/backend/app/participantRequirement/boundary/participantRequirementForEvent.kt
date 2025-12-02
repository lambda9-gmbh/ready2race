package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantImportRequest
import de.lambda9.ready2race.backend.app.participantRequirement.entity.AssignRequirementToNamedParticipantDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementCheckForEventConfigDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementCheckForEventUpsertDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementForEventSort
import de.lambda9.ready2race.backend.app.participantRequirement.entity.UpdateQrCodeRequirementDto
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.participantRequirementForEvent() {
    route("/participantRequirement") {

        get {
            call.respondComprehension {
                !authenticateAny(Privilege.ReadEventGlobal, Privilege.UpdateAppEventRequirementGlobal)
                val params = !pagination<ParticipantRequirementForEventSort>()
                val eventId = !pathParam("eventId", uuid)
                ParticipantRequirementService.pageForEvent(params, eventId)
            }
        }
        post {
            call.respondComprehension {

                val multiPartData = call.receiveMultipart()

                var upload: File? = null
                var config: ParticipantRequirementCheckForEventConfigDto? = null

                var done = false
                while (!done) {
                    val part = multiPartData.readPart()
                    if (part == null) {
                        done = true
                    } else {
                        when (part) {
                            is PartData.FileItem -> {
                                if (upload == null) {
                                    upload = File(
                                        part.originalFileName!!,
                                        part.provider().toByteArray(),
                                    )
                                } else {
                                    KIO.fail(RequestError.File.Multiple)
                                }
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

                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                val req = !KIO.failOnNull(config) { RequestError.BodyMissing(ParticipantRequirementCheckForEventConfigDto.example) }

                ParticipantRequirementService.checkRequirementForEvent(
                    eventId,
                    file,
                    req,
                    user.id!!
                )
            }
        }

        route("/active") {
            get {
                call.respondComprehension {
                    !authenticate()
                    val params = !pagination<ParticipantRequirementForEventSort>()
                    val eventId = !pathParam("eventId", uuid)
                    ParticipantRequirementService.getActiveForEvent(params, eventId)
                }
            }
        }

        route("/approve") {
            post {
                call.respondComprehension {
                    val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateAppEventRequirementGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val body = !receiveKIO(ParticipantRequirementCheckForEventUpsertDto.example)
                    ParticipantRequirementService.approveRequirementForEvent(eventId, body, user.id!!)
                }
            }
        }

        route("/{participantRequirementId}") {

            get {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val participantRequirementId = !pathParam("participantRequirementId", uuid)
                    val eventId = !pathParam("eventId", uuid)
                    ParticipantRequirementService.activateRequirementForEvent(
                        participantRequirementId,
                        eventId,
                        user.id!!
                    )
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val participantRequirementId = !pathParam("participantRequirementId", uuid)
                    val eventId = !pathParam("eventId", uuid)
                    ParticipantRequirementService.removeRequirementForEvent(participantRequirementId, eventId)
                }
            }
        }

        route("/namedParticipant/{namedParticipantId}") {
            post {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val namedParticipantId = !pathParam("namedParticipantId", uuid)
                    val body = !receiveKIO(AssignRequirementToNamedParticipantDto.example)
                    ParticipantRequirementService.assignRequirementToNamedParticipant(
                        eventId,
                        body.requirementId,
                        namedParticipantId,
                        body.qrCodeRequired,
                        user.id!!
                    )
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val namedParticipantId = !pathParam("namedParticipantId", uuid)
                    val body = !receiveKIO(AssignRequirementToNamedParticipantDto.example)
                    ParticipantRequirementService.removeRequirementForEvent(
                        body.requirementId,
                        eventId,
                        namedParticipantId
                    )
                }
            }
        }

        route("/qrCode") {
            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val body = !receiveKIO(UpdateQrCodeRequirementDto.example)
                    ParticipantRequirementService.updateQrCodeRequirement(
                        eventId,
                        body.requirementId,
                        body.namedParticipantId,
                        body.qrCodeRequired
                    )
                }
            }
        }

        route("/participant/{participantId}") {
            get {
                call.respondComprehension {
                    !authenticateAny(Privilege.ReadEventGlobal, Privilege.UpdateAppEventRequirementGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val participantId = !pathParam("participantId", uuid)
                    val onlyForApp = !queryParam("onlyForApp", boolean)

                    ParticipantRequirementService.getForParticipant(eventId, participantId, onlyForApp)
                }
            }
        }
    }
}