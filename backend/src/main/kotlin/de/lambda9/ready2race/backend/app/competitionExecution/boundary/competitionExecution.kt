package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListFileType
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchResultRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchRunningStateRequest
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.boundary.substitution
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.competitionExecution() {
    route("/competitionExecution") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.getProgress(competitionId)
            }
        }
        delete {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.deleteCurrentRound(competitionId)
            }
        }
        route("/createNextRound") {
            post {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val competitionId = !pathParam("competitionId", uuid)

                    CompetitionExecutionService.createNewRound(competitionId, user.id!!)
                }
            }
        }
        route("/{competitionMatchId}") {
            route("/data") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO(UpdateCompetitionMatchRequest.example)
                        CompetitionExecutionService.updateMatchData(competitionMatchId, user.id!!, body)
                    }
                }
            }
            route("/running-state") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO<UpdateCompetitionMatchRunningStateRequest>(UpdateCompetitionMatchRunningStateRequest.example)
                        CompetitionExecutionService.updateMatchRunningState(competitionMatchId, user.id!!, body)
                    }
                }
            }
            route("/results") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val competitionId = !pathParam("competitionId", uuid)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO(UpdateCompetitionMatchResultRequest.example)
                        CompetitionExecutionService.updateMatchResult(
                            competitionId,
                            competitionMatchId,
                            user.id!!,
                            body
                        )
                    }
                }
            }
            get("/startList") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)
                    val type = !queryParam("fileType", enum<StartListFileType>())
                    CompetitionExecutionService.downloadStartlist(competitionMatchId, type)
                }
            }
        }
        route("/places") {
            get {
                call.respondComprehension {
                    val optionalUserAndScope = !optionalAuthenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)

                    CompetitionExecutionService.getCompetitionPlaces(
                        eventId,
                        competitionId,
                        optionalUserAndScope?.second
                    )
                }
            }
        }

        substitution()
    }

}