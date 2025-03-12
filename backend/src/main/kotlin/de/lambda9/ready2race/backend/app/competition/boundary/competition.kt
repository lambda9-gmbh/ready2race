package de.lambda9.ready2race.backend.app.competition.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.entity.AssignDaysToCompetitionRequest
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionRequest
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionWithPropertiesSort
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.competitionSetup
import de.lambda9.ready2race.backend.requests.*
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.competition() {
    route("/competition") {

        post {
            val payload = call.receiveV(CompetitionRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }

                    val body = !payload
                    CompetitionService.addCompetition(body, user.id!!, eventId)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    val params = !pagination<CompetitionWithPropertiesSort>()
                    val eventDayId = !optionalQueryParam("eventDayId") { UUID.fromString(it) }

                    CompetitionService.pageWithPropertiesByEvent(eventId, params, eventDayId)
                }
            }
        }

        route("/{competitionId}") {

            get {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                        val competitionId = !pathParam("competitionId") { UUID.fromString(it) }
                        CompetitionService.getCompetitionWithProperties(competitionId)
                    }
                }
            }

            put {
                val payload = call.receiveV(CompetitionRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val competitionId = !pathParam("competitionId") { UUID.fromString(it) }

                        val body = !payload
                        CompetitionService.updateCompetition(body, user.id!!, competitionId)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val competitionId = !pathParam("competitionId") { UUID.fromString(it) }
                        CompetitionService.deleteCompetition(competitionId)
                    }
                }
            }

            route("/days"){

                put {
                    val payload = call.receiveV(AssignDaysToCompetitionRequest.example)
                    call.respondKIO {
                        KIO.comprehension {
                            val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                            val competitionId = !pathParam("competitionId") { UUID.fromString(it) }

                            val body = !payload
                            CompetitionService.updateEventDayHasCompetition(body, user.id!!, competitionId)
                        }
                    }
                }
            }

            competitionSetup("competitionId")
        }
    }
}