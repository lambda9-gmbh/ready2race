package de.lambda9.ready2race.backend.app.participant.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UploadMatchResultRequest
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantImportRequest
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.PartData
import io.ktor.server.http.content.file
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray

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

        get("/event") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                val clubId = !pathParam("clubId", uuid)
                val eventId = !queryParam("eventId", uuid)
                val ratingCategoryId = !optionalQueryParam("ratingCategoryId", uuid)
                ParticipantService.getByClubFilteredByEventRatingCategory(
                    clubId,
                    user,
                    scope,
                    eventId,
                    ratingCategoryId
                )
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

        post("/import") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                val clubId = !pathParam("clubId", uuid)
                if (scope == Privilege.Scope.OWN && clubId != user.club) {
                    KIO.fail(AuthError.PrivilegeMissing)
                } else {

                    val multiPartData = receiveMultipart()

                    var upload: File? = null
                    var request: ParticipantImportRequest? = null

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
                                    if (part.name == "request") {
                                        request = jsonMapper.readValue<ParticipantImportRequest>(part.value)
                                    }
                                }

                                else -> {}
                            }
                        }
                    }

                    val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                    val req = !KIO.failOnNull(request) { RequestError.BodyMissing(ParticipantImportRequest.example) }

                    // TODO: check file is valid csv?

                    ParticipantService.importParticipants(file, req, user.id!!, clubId)
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