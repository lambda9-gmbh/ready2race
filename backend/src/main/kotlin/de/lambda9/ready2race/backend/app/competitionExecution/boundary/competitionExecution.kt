package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.updateMatchResultFromRaceClocker
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
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.deleteCurrentRound(competitionId = competitionId, eventId = eventId)
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
        // Statisches Segment VOR der {competitionMatchId}-Route: Ktor bevorzugt exakte Segmente,
        // "round" wird also nie als Match-Id gelesen.
        get("/round/{setupRoundId}/startList") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)
                val setupRoundId = !pathParam("setupRoundId", uuid)

                CompetitionExecutionService.downloadRoundStartlist(
                    eventId = eventId,
                    competitionId = competitionId,
                    setupRoundId = setupRoundId,
                )
            }
        }
        route("/{competitionMatchId}") {
            route("/data") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val eventId = !pathParam("eventId", uuid)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO(UpdateCompetitionMatchRequest.example)
                        CompetitionExecutionService.updateMatchData(
                            eventId = eventId,
                            matchId = competitionMatchId,
                            userId = user.id!!,
                            request = body
                        )
                    }
                }
            }
            route("/activation") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val eventId = !pathParam("eventId", uuid)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO<UpdateCompetitionMatchActivationRequest>(
                            UpdateCompetitionMatchActivationRequest.example
                        )
                        CompetitionExecutionService.updateMatchActivation(
                            eventId = eventId,
                            matchId = competitionMatchId,
                            userId = user.id!!,
                            request = body
                        )
                    }
                }
            }

            // Ist-Start aus dem Büro — dasselbe „Läuft" wie im Schiedsrichter-Dashboard.
            put("/mark-started") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    CompetitionExecutionService.markMatchStarted(
                        eventId = eventId,
                        matchId = competitionMatchId,
                        userId = user.id!!,
                    )
                }
            }

            // Beenden zurücknehmen — nur in der jüngsten Runde, siehe Service-KDoc.
            put("/reopen") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    CompetitionExecutionService.reopenMatch(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        userId = user.id!!,
                    )
                }
            }
            route("/results") {
                put {
                    call.respondComprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val eventId = !pathParam("eventId", uuid)
                        val competitionId = !pathParam("competitionId", uuid)
                        val competitionMatchId = !pathParam("competitionMatchId", uuid)

                        val body = !receiveKIO(UpdateCompetitionMatchResultRequest.example)
                        CompetitionExecutionService.updateMatchResult(
                            eventId = eventId,
                            competitionId = competitionId,
                            matchId = competitionMatchId,
                            userId = user.id!!,
                            request = body
                        )
                    }
                }
            }

            put("/results-file") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    val multiPartData = receiveMultipart()

                    var upload: File? = null

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
                                        !KIO.fail(RequestError.File.Multiple)
                                    }
                                }

                                else -> {}
                            }
                            part.dispose()
                        }
                    }

                    val file = !KIO.failOnNull(upload) { RequestError.File.Missing }

                    !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                    CompetitionExecutionService.updateMatchResultByFile(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        file = file,
                        userId = user.id!!
                    )

                }
            }

            // Notfallweg zum Live-Abruf: eine von RaceClocker heruntergeladene Ergebnis-xlsx
            // einspielen, wenn am Steg das Netz fehlt. Eigener Parser fürs „Results"-Blatt.
            put("/results-file/raceclocker") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    val multiPartData = receiveMultipart()

                    var upload: File? = null
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
                                        !KIO.fail(RequestError.File.Multiple)
                                    }
                                }

                                else -> {}
                            }
                            part.dispose()
                        }
                    }

                    val file = !KIO.failOnNull(upload) { RequestError.File.Missing }

                    !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                    CompetitionExecutionService.importRaceClockerResultsFile(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        file = file,
                        userId = user.id!!,
                    )
                }
            }

            post("/results/from-raceclocker") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    updateMatchResultFromRaceClocker(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        userId = user.id!!,
                    )
                }
            }

            post("/results/raceclocker/resume") {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)

                    CompetitionExecutionService.resumeRaceClockerAutoPull(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        userId = user.id!!,
                    )
                }
            }

            get("/startList") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)
                    val fileType = !queryParam("fileType", enum<StartListFileType>())

                    CompetitionExecutionService.downloadStartlist(
                        eventId = eventId,
                        matchId = competitionMatchId,
                        type = fileType
                    )
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
            get("/csv") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionId = !pathParam("competitionId", uuid)

                    CompetitionExecutionService.downloadCompetitionPlacesCSV(
                        eventId = eventId,
                        competitionId = competitionId,
                    )
                }
            }
        }
        route("/challenge") {
            route("/team-results/{competitionRegistrationId}") {
                post("/accessToken/{accessToken}") {
                    call.respondComprehension {

                        val accessToken = !pathParam("accessToken")
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
                                            !KIO.fail(RequestError.File.Multiple)
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
                                part.dispose()
                            }
                        }

                        val req =
                            !KIO.failOnNull(request) { RequestError.BodyMissing(CompetitionChallengeResultRequest.example) }

                        // TODO: check valid image
                        // !KIO.failOn(!checkValidXls(file.bytes)) { RequestError.File.UnsupportedType }

                        CompetitionExecutionChallengeService.saveChallengeResult(
                            accessToken = accessToken,
                            competitionId = competitionId,
                            competitionRegistrationId = competitionRegistrationId,
                            request = req,
                            file = upload
                        )

                    }
                }
                delete {
                    call.respondComprehension {

                        // GLOBAL can always verify results - OWN only if self_submission is enabled for the event
                        val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.RESULT)
                        val competitionId = !pathParam("competitionId", uuid)
                        val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                        CompetitionExecutionChallengeService.deleteResult(
                            competitionId = competitionId,
                            competitionRegistrationId = competitionRegistrationId,
                            user = user,
                            scope = scope,
                        )
                    }
                }
                put("/verify") {
                    call.respondComprehension {

                        // GLOBAL can always verify results - OWN only if self_submission is enabled for the event
                        val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.RESULT)
                        val competitionId = !pathParam("competitionId", uuid)
                        val competitionRegistrationId = !pathParam("competitionRegistrationId", uuid)

                        CompetitionExecutionChallengeService.verifyChallengeResult(
                            competitionId = competitionId,
                            competitionRegistrationId = competitionRegistrationId,
                            user = user,
                            scope = scope,
                        )
                    }
                }
                post {
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
                                            !KIO.fail(RequestError.File.Multiple)
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
                                part.dispose()
                            }
                        }

                        val req =
                            !KIO.failOnNull(request) { RequestError.BodyMissing(CompetitionChallengeResultRequest.example) }

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

        route("/result-document/{resultDocumentId}") {

            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.RESULT)
                    val docId = !pathParam("resultDocumentId", uuid)

                    CompetitionExecutionService.downloadTeamResultDocument(
                        documentId = docId,
                        clubId = user.club,
                        scope = scope
                    )
                }
            }

            get("/accessToken/{accessToken}") {
                call.respondComprehension {
                    val docId = !pathParam("resultDocumentId", uuid)
                    val accessToken = !pathParam("accessToken")

                    CompetitionExecutionService.downloadTeamResultDocument(
                        documentId = docId,
                        accessToken = accessToken,
                    )
                }
            }

        }



        substitution()
    }

}