package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.eventDocument.boundary.EventDocumentService
import de.lambda9.ready2race.backend.app.substitution.boundary.substitution
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.xls.checkValidXls
import de.lambda9.tailwind.core.KIO
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

private enum class StartListFileTypeParam {
    PDF,
    CSV
}

fun Route.competitionExecution() {
    route("/competitionExecution") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.getProgress(eventId, competitionId)
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
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)

                    CompetitionExecutionService.createNewRound(eventId, competitionId, user.id!!)
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

                        val body = !receiveKIO<UpdateCompetitionMatchRunningStateRequest>(
                            UpdateCompetitionMatchRunningStateRequest.example
                        )
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

            put("/results-file") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    val multiPartData = receiveMultipart()

                    var upload: File? = null
                    var request: UploadMatchResultRequest? = null

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
                                        request = jsonMapper.readValue<UploadMatchResultRequest>(part.value)
                                    }
                                }

                                else -> {}
                            }
                        }
                    }

                    val file = !KIO.failOnNull(upload) { RequestError.File.Missing }
                    val req = !KIO.failOnNull(request) { RequestError.BodyMissing(UploadMatchResultRequest.example) }

                    !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                    CompetitionExecutionService.updateMatchResultByFile(
                        competitionId,
                        competitionMatchId,
                        file,
                        req,
                        user.id!!
                    )

                }
            }

            get("/startList") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)
                    val typeParam = !queryParam("fileType", enum<StartListFileTypeParam>())

                    val type = when (typeParam) {
                        StartListFileTypeParam.PDF -> StartListFileType.PDF
                        StartListFileTypeParam.CSV -> {
                            val config = !queryParam("config", uuid)
                            StartListFileType.CSV(config)
                        }
                    }

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
        route("/challenge") {
            route("/team-results") {
                post("/{competitionRegistrationId}") {
                    call.respondComprehension {

                        // GLOBAL can always submit results - OWN only if self_submission is enabled for the event
                        val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.RESULT)
                        val competitionId = !pathParam("competitionId", uuid)
                        val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                        val multiPartData = receiveMultipart()

                        // Todo: Limit file size
                        var upload: File? = null
                        var request: CompetitionChallengeResultRequest? = null

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
                                            request =
                                                jsonMapper.readValue<CompetitionChallengeResultRequest>(part.value)
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }

                        val req =
                            !KIO.failOnNull(request) { RequestError.BodyMissing(UploadMatchResultRequest.example) }

                        // TODO: check valid image
                        // !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                        CompetitionExecutionChallengeService.saveChallengeResult(
                            user = user,
                            scope = scope,
                            competitionId = competitionId,
                            competitionRegistrationId = competitionRegistrationId,
                            request = req,
                            file = upload
                        )

                    }
                }
            }
        }
        get("/result-document/{resultDocumentId}") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.RESULT)
                val id = !pathParam("resultDocumentId", uuid)

                CompetitionExecutionService.downloadTeamResultDocument(id, user.club, scope)
            }
        }

        substitution()
    }

}