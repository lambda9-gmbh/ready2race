package de.lambda9.ready2race.backend.app.competition.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.entity.AssignDaysToCompetitionRequest
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionRequest
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionWithPropertiesSort
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.competitionSetup
import de.lambda9.ready2race.backend.app.competition.entity.*
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.competitionExecution
import de.lambda9.ready2race.backend.app.competitionRegistration.boundary.competitionRegistration
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.competition() {
    route("/competition") {

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(CompetitionRequest.example)
                CompetitionService.addCompetition(body, user.id!!, eventId)
            }
        }

        get {
            call.respondComprehension {
                val optionalUserWithScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId", uuid)

                val params =
                    if (optionalUserWithScope?.second == Privilege.Scope.OWN) {
                        !pagination<CompetitionForClubWithPropertiesSort>()
                    } else if ((optionalUserWithScope?.second == Privilege.Scope.GLOBAL)) {
                        !pagination<CompetitionWithPropertiesSort>()
                    } else {
                        !pagination<CompetitionPublicSort>()
                    }
                val eventDayId = !optionalQueryParam("eventDayId", uuid)

                CompetitionService.pageWithPropertiesByEvent(
                    eventId,
                    params,
                    eventDayId,
                    optionalUserWithScope?.first,
                    optionalUserWithScope?.second
                )
            }
        }

        route("/{competitionId}") {

            get {
                call.respondComprehension {
                    val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val competitionId = !pathParam("competitionId", uuid)
                    CompetitionService.getCompetitionWithProperties(competitionId, optionalUserAndScope?.first, optionalUserAndScope?.second)
                }
            }

            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val competitionId = !pathParam("competitionId", uuid)

                    val body = !receiveKIO(CompetitionRequest.example)
                    CompetitionService.updateCompetition(body, user.id!!, competitionId)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val competitionId = !pathParam("competitionId", uuid)
                    CompetitionService.deleteCompetition(competitionId)
                }
            }

            put("/days") {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val competitionId = !pathParam("competitionId", uuid)

                    val body = !receiveKIO(AssignDaysToCompetitionRequest.example)
                    CompetitionService.updateEventDayHasCompetition(body, user.id!!, competitionId)
                }
            }

            competitionRegistration()
            competitionSetup("competitionId")
            competitionExecution()
        }
    }
}