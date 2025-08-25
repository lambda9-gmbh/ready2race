package de.lambda9.ready2race.backend.app.results.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.results.control.toDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionChoiceDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionHavingResultsSort
import de.lambda9.ready2race.backend.app.results.entity.EventResultData
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.fileResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.pdf.elements.table.TableBuilder
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.util.UUID

object ResultsService {

    fun pageCompetitionsHavingResults(
        eventId: UUID,
        params: PaginationParameters<CompetitionHavingResultsSort>,
        publishedOnly: Boolean,
    ): App<ServiceError, ApiResponse.Page<CompetitionChoiceDto, CompetitionHavingResultsSort>> = KIO.comprehension {

        if (publishedOnly) {
            !EventService.checkEventPublished(eventId)
        } else {
            !EventService.checkEventExisting(eventId)
        }

        ResultsRepo.pageCompetitionsHavingResults(eventId, params).orDie().pageResponse { it.toDto() }
    }

    fun downloadResultsDocument(
        eventId: UUID,
        publishedOnly: Boolean,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {

        if (publishedOnly) {
            !EventService.checkEventPublished(eventId)
        }

        generateResultsDocument(eventId).fileResponse()
    }

    fun generateResultsDocument(
        eventId: UUID,
    ): App<ServiceError, FileUpload> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        val competitions = !CompetitionRepo.getByEvent(eventId).orDie()

        val competitionsData = !competitions.traverse { competition ->
            KIO.comprehension {
                val places = !CompetitionExecutionService.computeCompetitionPlaces(competition.id!!)

                KIO.ok(
                    EventResultData.CompetitionResultData(
                        identifier = competition.identifier!!,
                        name = competition.name!!,
                        shortName = competition.shortName,
                        days = competition.eventDays!!.map { it!! },
                        teams = places.map { (team, place) ->

                            val substitutions =
                                !SubstitutionRepo.getOriginalsByCompetitionRegistration(team.competitionRegistration).orDie()


                            val clubs = team.participants.map { it.externalClubName }.toSet()
                            val actualClubName = if (clubs.size == 1) {
                                clubs.first()
                            } else {
                                event.mixedTeamTerm
                            }

                            EventResultData.TeamResultData(
                                place = place,
                                clubName = team.clubName,
                                teamName = team.registrationName,
                                participatingClubName = actualClubName,
                                ratingCategory = team.ratingCategory,
                                participants = team.participants.map {
                                    EventResultData.ParticipantResultData(
                                        role = it.namedParticipantName,
                                        firstname = it.firstName,
                                        lastname = it.lastName,
                                        year = it.year,
                                        gender = it.gender,
                                        externalClubName = it.externalClubName,
                                    )
                                },
                                sortedSubstitutions = substitutions.sortedBy { it.orderForRound!! }
                                    .fold(emptyList<EventResultData.SubstitutionResultData>() to false) { (acc, skip), sub ->
                                        if (skip) {
                                            acc to false
                                        } else {
                                            val swappedWithId =
                                                SubstitutionService.getSwapSubstitution(sub, substitutions)

                                            if (swappedWithId != null) {

                                                val sub2 = substitutions.first { it.id == swappedWithId }

                                                (acc + EventResultData.SubstitutionResultData.RoleSwap(
                                                    left = EventResultData.ParticipantResultData(
                                                        role = sub.namedParticipantName!!,
                                                        firstname = sub.participantOut!!.firstname,
                                                        lastname = sub.participantOut!!.lastname,
                                                        year = sub.participantOut!!.year,
                                                        gender = sub.participantOut!!.gender,
                                                        externalClubName = sub.participantOut!!.externalClubName,
                                                    ),
                                                    right = EventResultData.ParticipantResultData(
                                                        role = sub2.namedParticipantName!!,
                                                        firstname = sub2.participantOut!!.firstname,
                                                        lastname = sub2.participantOut!!.lastname,
                                                        year = sub2.participantOut!!.year,
                                                        gender = sub2.participantOut!!.gender,
                                                        externalClubName = sub2.participantOut!!.externalClubName,
                                                    ),
                                                    round = sub.competitionSetupRoundName!!
                                                )) to true
                                            } else {
                                                (acc + EventResultData.SubstitutionResultData.ParticipantSwap(
                                                    subOut = EventResultData.ParticipantResultData(
                                                        role = sub.namedParticipantName!!,
                                                        firstname = sub.participantOut!!.firstname,
                                                        lastname = sub.participantOut!!.lastname,
                                                        year = sub.participantOut!!.year,
                                                        gender = sub.participantOut!!.gender,
                                                        externalClubName = sub.participantOut!!.externalClubName,
                                                    ),
                                                    subIn = EventResultData.ParticipantResultData(
                                                        role = sub.namedParticipantName!!,
                                                        firstname = sub.participantIn!!.firstname,
                                                        lastname = sub.participantIn!!.lastname,
                                                        year = sub.participantIn!!.year,
                                                        gender = sub.participantIn!!.gender,
                                                        externalClubName = sub.participantIn!!.externalClubName,
                                                    ),
                                                    round = sub.competitionSetupRoundName!!
                                                )) to false
                                            }
                                        }
                                    }.first,
                            )
                        }
                    )
                )
            }
        }

        val bytes = buildPdf(EventResultData(event.name, competitionsData), null)

        KIO.ok(
            FileUpload(
                fileName = "results-${event.name}.pdf",
                bytes = bytes,
            )
        )
    }

    fun buildPdf(
        data: EventResultData,
        template: PageTemplate?,
    ): ByteArray {
        val doc = document(template) {
            page {
                block(
                    padding = Padding(top = 40f, bottom = 5f),
                ) {
                    text(
                        fontStyle = FontStyle.BOLD,
                        fontSize = 16f,
                        centered = true,
                    ) {
                        "Veranstaltungsergebnisse"
                    }
                }
                text(
                    fontSize = 13f,
                    centered = true,
                ) {
                    data.name
                }
            }

            data.competitions.forEach { competition ->
                page {
                    block(
                        padding = Padding(bottom = 15f),
                    ) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 14f,
                        ) {
                            "Wettkampf"
                        }

                        table(
                            padding = Padding(5f, 10f, 0f, 0f)
                        ) {
                            column(0.1f)
                            column(0.25f)
                            column(0.65f)

                            row {
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.identifier }
                                }
                                cell {
                                    competition.shortName?.let {
                                        text(
                                            fontSize = 12f,
                                        ) { it }
                                    }
                                }
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.name }
                                }
                            }
                        }

                        if (competition.days.isNotEmpty()) {
                            block(
                                padding = Padding(top = 15f),
                            ) {
                                text {
                                    "Veranstaltungstag" +
                                        (if (competition.days.size == 1) "" else "e") +
                                        ": " +
                                        competition.days.sortedBy { it.date }.joinToString(", ") { day ->
                                            day.date.hr() + (day.name?.let { " ($it)" } ?: "")
                                        }
                                }
                            }
                        }
                    }

                    if (competition.teams.isEmpty()) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 11f,
                        ) { "Keine Ergebnisse" }
                    } else {
                        competition.teams.sortedBy { it.place }.forEach { team ->
                            block(
                                padding = Padding(0f, 0f, 0f, 25f)
                            ) {

                                block {
                                    text(
                                        fontSize = 15f,
                                        fontStyle = FontStyle.BOLD,
                                        centered = true,
                                    ) {
                                        team.place.toString()
                                    }
                                }

                                block {
                                    text(
                                        fontStyle = FontStyle.BOLD
                                    ) { team.clubName }
                                    team.teamName?.let {
                                        text(
                                            newLine = false,
                                        ) { " $it" }
                                    }
                                    team.participatingClubName?.let {
                                        text(
                                            newLine = false,
                                        ) { " [$it]" }
                                    }
                                    team.ratingCategory?.let {
                                        text(
                                            newLine = false,
                                        ) { " $it" }
                                    }
                                }

                                table(
                                    padding = Padding(5f, 0f, 0f, 0f),
                                    withBorder = true,
                                ) {
                                    column(0.15f)
                                    column(0.05f)
                                    column(0.2f)
                                    column(0.2f)
                                    column(0.1f)
                                    column(0.3f)

                                    team.participants
                                        .sortedBy { it.role }
                                        .forEachIndexed { idx, member ->
                                            row(
                                                color = if (idx % 2 == 1) Color(230, 230, 230) else null,
                                            ) {
                                                cell {
                                                    text { member.role }
                                                }
                                                cell {
                                                    text { member.gender.name }
                                                }
                                                cell {
                                                    text { member.firstname }
                                                }
                                                cell {
                                                    text { member.lastname }
                                                }
                                                cell {
                                                    text { member.year.toString() }
                                                }
                                                cell {
                                                    text { member.externalClubName ?: team.clubName }
                                                }
                                            }
                                        }
                                }

                                if (team.sortedSubstitutions.isNotEmpty()) {
                                    block(
                                        padding = Padding(top = 5f)
                                    ) {
                                        text {
                                            "Ummeldungen"
                                        }

                                        table(
                                            padding = Padding(top = 2f),
                                            withBorder = true,
                                        ) {
                                            column(0.25f)
                                            column(0.25f)
                                            column(0.25f)
                                            column(0.25f)

                                            team.sortedSubstitutions
                                                .forEachIndexed { idx, sub ->

                                                    row(
                                                        color = if (idx % 2 == 1) Color(230, 230, 230) else null,
                                                    ) {
                                                        when (sub) {

                                                            is EventResultData.SubstitutionResultData.ParticipantSwap -> {

                                                                cell {
                                                                    text { "${sub.subOut.firstname} ${sub.subOut.lastname}" }
                                                                }
                                                                cell {
                                                                    text { "ersetzt durch" }
                                                                }
                                                                cell {
                                                                    text { "${sub.subIn.firstname} ${sub.subIn.lastname}" }
                                                                }
                                                                cell {
                                                                    text { sub.round }
                                                                }

                                                            }

                                                            is EventResultData.SubstitutionResultData.RoleSwap -> {

                                                                cell {
                                                                    text { "${sub.left.firstname} ${sub.left.lastname}" }
                                                                }
                                                                cell {
                                                                    text { "tauscht mit" }
                                                                }
                                                                cell {
                                                                    text { "${sub.right.firstname} ${sub.right.lastname}" }
                                                                }
                                                                cell {
                                                                    text { sub.round }
                                                                }

                                                            }
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }

}