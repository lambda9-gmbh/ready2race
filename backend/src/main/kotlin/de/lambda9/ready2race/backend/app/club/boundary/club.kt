package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.clubDto
import de.lambda9.ready2race.backend.app.club.control.clubSearchDto
import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.app.participant.boundary.participant
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.club() {
    route("/club") {

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.CLUB)
                val payload = !receiveKIO(ClubUpsertDto.example)
                ClubService.addClub(payload, user.id!!)

            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                val params = !pagination<ClubSort>()
                ClubService.page(params) { it.clubDto() }
            }
        }


        route("/search") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                    val params = !pagination<ClubSort>()
                    ClubService.page(params) { it.clubSearchDto() }
                }
            }
        }

        route("/{clubId}") {

            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                    val id = !pathParam("clubId", uuid)
                    ClubService.getClub(id)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                    val id = !pathParam("clubId", uuid)
                    val payload = !receiveKIO(ClubUpsertDto.example)
                    ClubService.updateClub(payload, user.id!!, id)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.CLUB)
                    val id = !pathParam("clubId", uuid)
                    ClubService.deleteClub(id)
                }
            }

            participant()

        }
    }
}
