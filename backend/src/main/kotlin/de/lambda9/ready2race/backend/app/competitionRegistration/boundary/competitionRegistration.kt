package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.eventRegistration.entity.CompetitionRegistrationTeamUpsertDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.CheckInOutRequest
import de.lambda9.ready2race.backend.app.teamTracking.boundary.TeamTrackingService
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionRegistration() {

    route("/competitionRegistration") {
        get {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                val competitionId = !pathParam("competitionId", uuid)
                val params = !pagination<CompetitionRegistrationSort>()

                CompetitionRegistrationService.getByCompetition(params, competitionId, scope, user)
            }
        }

        post {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                val competitionId = !pathParam("competitionId", uuid)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(CompetitionRegistrationTeamUpsertDto.example)

                CompetitionRegistrationService.create(body, eventId, competitionId, scope, user)
            }
        }

        route("/{competitionRegistrationId}") {

            put {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)
                    val body = !receiveKIO(CompetitionRegistrationTeamUpsertDto.example)

                    CompetitionRegistrationService.update(body, competitionId, competitionRegistrationId, scope, user)
                }
            }

            delete {
                call.respondComprehension {

                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                    CompetitionRegistrationService.delete(competitionId, competitionRegistrationId, scope, user)
                }
            }

            route("/check-in") {
                post {
                    call.respondComprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                        val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)
                        val body = !receiveKIO(CheckInOutRequest.example)

                        TeamTrackingService.handleTeamCheckIn(competitionRegistrationId, body.eventId, user.id)
                    }
                }
            }

            route("/check-out") {
                post {
                    call.respondComprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                        val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)
                        val body = !receiveKIO(CheckInOutRequest.example)

                        TeamTrackingService.handleTeamCheckOut(competitionRegistrationId, body.eventId, user.id)
                    }
                }
            }

        }

    }


}