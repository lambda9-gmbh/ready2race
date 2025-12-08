package de.lambda9.ready2race.backend.app.club.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.clubDto
import de.lambda9.ready2race.backend.app.club.control.clubSearchDto
import de.lambda9.ready2race.backend.app.club.entity.ClubImportRequest
import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.app.participant.boundary.ParticipantService
import de.lambda9.ready2race.backend.app.participant.boundary.participant
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantImportRequest
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.PartData
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray

fun Route.club() {
    route("/club") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.CreateClubGlobal)
                val payload = !receiveKIO(ClubUpsertDto.example)
                ClubService.addClub(payload, user.id!!)

            }
        }

        get {
            call.respondComprehension {
                val params = !pagination<ClubSort>()
                ClubService.page(params) { it.clubDto() }
            }
        }


        route("/search") {
            get {
                call.respondComprehension {
                    val params = !pagination<ClubSort>()
                    val eventId = !optionalQueryParam("eventId", uuid)
                    ClubService.page(params, eventId) { it.clubSearchDto() }
                }
            }
        }

        route("/{clubId}") {

            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                    val id = !pathParam("clubId", uuid)
                    if (scope == Privilege.Scope.OWN && id != user.club) {
                        KIO.fail(AuthError.PrivilegeMissing)
                    } else {
                        ClubService.getClub(id)
                    }
                }
            }

            put {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                    val id = !pathParam("clubId", uuid)
                    val payload = !receiveKIO(ClubUpsertDto.example)
                    if (scope == Privilege.Scope.OWN && id != user.club) {
                        KIO.fail(AuthError.PrivilegeMissing)
                    } else {
                        ClubService.updateClub(payload, user.id!!, id)
                    }
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.DeleteClubGlobal)
                    val id = !pathParam("clubId", uuid)
                    ClubService.deleteClub(id)
                }
            }

            route("/user") {
                get {
                    call.respondComprehension {
                        val id = !pathParam("clubId", uuid)
                        val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.USER)
                        if (scope == Privilege.Scope.OWN && id != user.club) {
                            KIO.fail(AuthError.PrivilegeMissing)
                        } else {
                            AppUserService.getAllByClubId(id)
                        }
                    }
                }
            }
            route("/clubRepresentative") {
                get {

                    call.respondComprehension {
                        !authenticate(Privilege.ReadClubOwn)
                        val id = !pathParam("clubId", uuid)

                        AppUserService.getPendingClubRepresentativeApprovals(id)
                    }
                }
            }

            participant()

        }

        post("/import") {
            call.respondComprehension {
                val user = !authenticate(Privilege.CreateClubGlobal)
                val multiPartData = receiveMultipart()

                var upload: File? = null
                var request: ClubImportRequest? = null

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
                                    request = jsonMapper.readValue<ClubImportRequest>(part.value)
                                }
                            }

                            else -> {}
                        }
                    }
                }

                val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                val req = !KIO.failOnNull(request) { RequestError.BodyMissing(ParticipantImportRequest.example) }

                // TODO: check file is valid csv?

                ClubService.importClubs(file, req, user.id!!)

            }
        }
    }
}
