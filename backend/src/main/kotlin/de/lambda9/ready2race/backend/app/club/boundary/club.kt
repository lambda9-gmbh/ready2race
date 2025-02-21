package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.boundary.ParticipantService
import de.lambda9.ready2race.backend.app.participant.boundary.participant
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.club() {
    route("/club") {

        post {
            val payload = call.receiveV(ClubUpsertDto.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.CLUB)

                    ClubService.addClub(!payload, user.id!!)

                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                    val params = !pagination<ClubSort>()
                    ClubService.page(params)
                }
            }
        }

        route("/{clubId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.CLUB)
                        val id = !pathParam("clubId") { UUID.fromString(it) }
                        ClubService.getClub(id)
                    }
                }
            }

            put {
                val payload = call.receiveV(ClubUpsertDto.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.CLUB)
                        val id = !pathParam("clubId") { UUID.fromString(it) }
                        ClubService.updateClub(!payload, user.id!!, id)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.CLUB)
                        val id = !pathParam("clubId") { UUID.fromString(it) }
                        ClubService.deleteClub(id)
                    }
                }
            }

            participant()

        }
    }
}