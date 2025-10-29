package de.lambda9.ready2race.backend.app.results.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.app.results.control.ChallengeResultParticipantViewRepo
import de.lambda9.ready2race.backend.app.results.control.ChallengeResultTeamViewRepo
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.results.control.toDto
import de.lambda9.ready2race.backend.app.results.entity.*
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.fileResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
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

    fun pageChallengeClubs(
        eventId: UUID,
        params: PaginationParameters<ResultsChallengeClubSort>,
        publishedOnly: Boolean,
        competition: UUID?,
        ratingCategory: UUID?,
    ): App<ServiceError, ApiResponse.Page<ResultChallengeClubDto, ResultsChallengeClubSort>> = KIO.comprehension {

        if (publishedOnly) {
            !EventService.checkEventPublished(eventId)
        } else {
            !EventService.checkEventExisting(eventId)
        }

        val allTeams = !ChallengeResultTeamViewRepo.get(eventId, competition, ratingCategory).orDie()

        // Get all participants for the teams
        val registrationIds = allTeams.mapNotNull { it.competitionRegistrationId }
        val allParticipants = !ChallengeResultTeamViewRepo.getParticipantsByRegistrations(registrationIds).orDie()

        // Group teams by club
        val clubGroups = allTeams.groupBy { it.clubId!! to it.clubName!! }

        // Calculate rankings for each club
        val clubDtos = clubGroups.map { (clubInfo, teams) ->
            val (clubId, clubName) = clubInfo

            // Calculate total result (sum of all team result values)
            val totalResult = teams.sumOf { it.teamResultValue ?: 0 }
            val teamCount = teams.size

            // Calculate relative result (average per team)
            val relativeResult = if (teamCount > 0) totalResult / teamCount else 0

            // Build team DTOs
            val teamDtos = teams.map { team ->
                val teamParticipants = allParticipants.filter { it.teamId == team.competitionRegistrationId }

                // Group participants by named participant (role)
                val namedParticipantDtos = teamParticipants
                    .groupBy { it.roleId!! to it.role!! }
                    .map { (roleInfo, participants) ->
                        val (roleId, roleName) = roleInfo
                        ResultChallengeClubDto.ResultChallengeClubNamedParticipantDto(
                            id = roleId,
                            name = roleName,
                            participants = participants.map { p ->
                                ResultChallengeClubDto.ResultChallengeClubParticipantDto(
                                    id = p.participantId!!,
                                    firstName = p.firstname!!,
                                    lastName = p.lastname!!,
                                    gender = p.gender!!,
                                    year = p.year!!,
                                    external = p.external ?: false,
                                    externalClubName = p.externalClubName
                                )
                            }
                        )
                    }

                ResultChallengeClubDto.ResultChallengeClubTeamDto(
                    competitionRegistrationId = team.competitionRegistrationId!!,
                    competitionRegistrationName = team.competitionRegistrationName,
                    result = team.teamResultValue ?: 0,
                    competitionId = team.competitionId!!,
                    competitionIdentifier = team.competitionIdentifier!!,
                    competitionName = team.competitionName!!,
                    namedParticipants = namedParticipantDtos,
                    ratingCategoryDto = team.ratingCategoryId?.let {
                        RatingCategoryDto(
                            id = team.ratingCategoryId!!,
                            name = team.ratingCategoryName!!,
                            description = team.ratingCategoryDescription
                        )
                    }
                )
            }

            ResultChallengeClubDto(
                id = clubId,
                clubName = clubName,
                totalRank = 0, // Will be calculated after sorting
                totalResult = totalResult,
                relativeRank = 0, // Will be calculated after sorting
                relativeResult = relativeResult,
                teams = teamDtos
            )
        }

        // Sort by total result (descending) to calculate total ranks
        val sortedByTotal = clubDtos.sortedByDescending { it.totalResult }
        val withTotalRanks = sortedByTotal.mapIndexed { index, dto ->
            dto.copy(totalRank = index + 1)
        }

        // Sort by relative result (descending) to calculate relative ranks
        val sortedByRelative = withTotalRanks.sortedByDescending { it.relativeResult }
        val withAllRanks = sortedByRelative.mapIndexed { index, dto ->
            dto.copy(relativeRank = index + 1)
        }

        // Apply fake pagination
        val allClubs = withAllRanks

        // Search (if provided)
        val searchedClubs = params.search?.takeIf { it.isNotBlank() }?.let { searchText ->
            val searchTokens = searchText.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (searchTokens.isEmpty()) return@let allClubs

            allClubs.filter { club ->
                val haystack = club.clubName.lowercase()
                searchTokens.all { token -> haystack.contains(token) }
            }
        } ?: allClubs

        // Sort (if provided)
        val sortedClubs = params.sort?.let { orders ->
            val comparator = orders
                .map {
                    if (it.direction == de.lambda9.ready2race.backend.pagination.Direction.ASC) it.field.comparator() else it.field.comparator()
                        .reversed()
                }
                .reduce { acc, comparator -> acc.thenComparing(comparator) }

            searchedClubs.sortedWith(comparator)
        } ?: searchedClubs

        // Pagination
        val offsetClubs = params.offset?.let { sortedClubs.drop(it) } ?: sortedClubs
        val limitedClubs = params.limit?.let { offsetClubs.take(it) } ?: offsetClubs

        KIO.ok(
            ApiResponse.Page(
                data = limitedClubs,
                pagination = params.toPagination(allClubs.size)
            )
        )
    }

    fun pageChallengeParticipants(
        eventId: UUID,
        params: PaginationParameters<ResultsChallengeParticipantSort>,
        publishedOnly: Boolean,
        competition: UUID?,
        ratingCategory: UUID?,
    ): App<ServiceError, ApiResponse.Page<ResultChallengeParticipantDto, ResultsChallengeParticipantSort>> =
        KIO.comprehension {

            if (publishedOnly) {
                !EventService.checkEventPublished(eventId)
            } else {
                !EventService.checkEventExisting(eventId)
            }

            val allParticipantResults =
                !ChallengeResultParticipantViewRepo.get(eventId, competition, ratingCategory).orDie()

            // Group results by participant
            val participantGroups = allParticipantResults.groupBy { it.id!! to (it.firstname!! to it.lastname!!) }

            // Calculate rankings for each participant
            val participantDtos = participantGroups.map { (participantInfo, results) ->
                val (participantId, names) = participantInfo
                val (firstName, lastName) = names

                // Sum up all team results for this participant
                val totalResult = results.sumOf { it.teamResultValue ?: 0 }

                // Get club info (should be same for all results)
                val clubId = results.first().clubId!!
                val clubName = results.first().clubName!!

                // Build team DTOs
                val teamDtos = results.map { result ->
                    ResultChallengeParticipantDto.ResultChallengeParticipantTeamDto(
                        competitionRegistrationId = result.competitionRegistrationId!!,
                        competitionRegistrationName = result.competitionRegistrationName,
                        result = result.teamResultValue ?: 0,
                        competitionId = result.competitionId!!,
                        competitionIdentifier = result.competitionIdentifier!!,
                        competitionName = result.competitionName!!,
                        ratingCategoryDto = result.ratingCategoryId?.let {
                            RatingCategoryDto(
                                id = result.ratingCategoryId!!,
                                name = result.ratingCategoryName!!,
                                description = result.ratingCategoryDescription
                            )
                        }
                    )
                }

                ResultChallengeParticipantDto(
                    id = participantId,
                    firstName = firstName,
                    lastName = lastName,
                    rank = 0, // Will be calculated after sorting
                    result = totalResult,
                    clubId = clubId,
                    clubName = clubName,
                    teams = teamDtos
                )
            }

            // Sort by result (descending) to calculate ranks
            val sortedByResult = participantDtos.sortedByDescending { it.result }
            val withRanks = sortedByResult.mapIndexed { index, dto ->
                dto.copy(rank = index + 1)
            }

            val allParticipants = withRanks

            // Search (if provided)
            val searchedParticipants = params.search?.takeIf { it.isNotBlank() }?.let { searchText ->
                val searchTokens = searchText.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (searchTokens.isEmpty()) return@let allParticipants

                allParticipants.filter { participant ->
                    val haystack =
                        "${participant.firstName} ${participant.lastName} ${participant.clubName}".lowercase()
                    searchTokens.all { token -> haystack.contains(token) }
                }
            } ?: allParticipants

            // Sort (if provided)
            val sortedParticipants = params.sort?.let { orders ->
                val comparator = orders
                    .map {
                        if (it.direction == de.lambda9.ready2race.backend.pagination.Direction.ASC) it.field.comparator() else it.field.comparator()
                            .reversed()
                    }
                    .reduce { acc, comparator -> acc.thenComparing(comparator) }

                searchedParticipants.sortedWith(comparator)
            } ?: searchedParticipants

            // Pagination
            val offsetParticipants = params.offset?.let { sortedParticipants.drop(it) } ?: sortedParticipants
            val limitedParticipants = params.limit?.let { offsetParticipants.take(it) } ?: offsetParticipants

            KIO.ok(
                ApiResponse.Page(
                    data = limitedParticipants,
                    pagination = params.toPagination(allParticipants.size)
                )
            )
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
    ): App<ServiceError, File> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        val competitions = !CompetitionRepo.getByEvent(eventId).orDie()

        val competitionsData = !competitions
            .sortedWith(lexiNumberComp { it.identifier })
            .traverse { competition ->
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
                                    !SubstitutionRepo.getOriginalsByCompetitionRegistration(team.competitionRegistration)
                                        .orDie()


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
            File(
                name = "results-${event.name}.pdf",
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