package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.singletonOrFallback
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService.getSwapSubstitution
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionParticipantDto
import de.lambda9.ready2race.backend.app.timecode.control.toTimecode
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

private fun List<CompetitionMatchTeamParticipant>.toNamedParticipantsDto() =
    groupBy { it.namedParticipantId }.map { (namedParticipantId, participants) ->
        CompetitionTeamNamedParticipantDto(
            namedParticipantId = namedParticipantId,
            namedParticipantName = participants.first().namedParticipantName,
            participants = participants.map { p ->
                CompetitionTeamParticipantDto(
                    participantId = p.participantId,
                    namedParticipantName = p.namedParticipantName,
                    firstName = p.firstName,
                    lastName = p.lastName,
                    year = p.year,
                    gender = p.gender,
                    external = p.external,
                    externalClubName = p.externalClubName,
                )
            }
        )
    }

/**
 * [lastScanByParticipant] ist der letzte Steg-Scan je Person (aus
 * [CompetitionMatchRepo.getScansByCompetition]) und speist allein den Wasser-Chip. Eine leere Karte
 * heißt "keine Scans" - dann bleibt `teamsOnWater` null und der Chip entfällt, statt bei jedem Lauf
 * dauerhaft "Wasser 0/6" zu behaupten.
 *
 * Bekannte Ungenauigkeit: die Crew kommt hier aus der Anmeldung (View
 * `registered_competition_team_participant`), Ummeldungen der Runde sind darin nicht aufgelöst -
 * anders als im Schiedsrichter-Dashboard, das sie über `buildParticipants` nachzieht. Der Fehler
 * geht in die harmlose Richtung: eine ummeldete Crew erscheint eher als "noch nicht draußen", der
 * Chip bleibt also länger stehen, statt einen Start zu behaupten, den es nicht gibt.
 */
fun CompetitionSetupRoundWithMatches.toCompetitionRoundDto(
    mixedTeamTerm: String?,
    lastScanByParticipant: Map<UUID, Pair<String, LocalDateTime>> = emptyMap(),
) = run {
    val matchPairs =
        matches.map { match -> match to setupMatches.first { setupMatch -> setupMatch.id == match.competitionSetupMatch } }

    // Ein Aufruf für die ganze Runde: nur so lässt sich der Fall "kein Team der Runde hatte je
    // einen Scan" überhaupt erkennen (Abschnitt 6 der Spec).
    val teamsOnWaterPerMatch = MatchStatusLogic.teamsOnWaterPerMatch(
        matchPairs.map { (match, _) ->
            match.teams.map { team ->
                team.participants.distinctBy { it.participantId }
                    .map { lastScanByParticipant[it.participantId] }
            }
        }
    )

    KIO.ok(
        CompetitionRoundDto(
            setupRoundId = setupRoundId,
            name = setupRoundName,
            matches = matchPairs
                .mapIndexed { index, match ->
                    CompetitionMatchDto(
                        id = match.second.id,
                        name = match.second.name,
                        teams = match.first.teams.map { team ->
                            CompetitionMatchTeamDto(
                                registrationId = team.competitionRegistration,
                                teamNumber = team.teamNumber!!, // This should not be null because competition_match_teams are not created if the registration teamNumber is missing
                                clubId = team.clubId,
                                clubName = team.clubName,
                                actualClubName = singletonOrFallback(
                                    team.participants.map { it.externalClubName }.toSet(),
                                    mixedTeamTerm
                                ),
                                namedParticipants = team.participants.toNamedParticipantsDto(),
                                name = team.registrationName,
                                startNumber = team.startNumber,
                                place = team.place,
                                timeString = team.timeString,
                                placesCalculated = team.placesCalculated,
                                deregistered = team.deregistered,
                                deregistrationReason = if (team.deregistered) team.deregistrationReason else null,
                                failed = team.failed,
                                failedReason = team.failedReason,
                                penaltySeconds = team.penaltySeconds,
                                penaltyNote = team.penaltyNote,
                            )
                        },
                        weighting = match.second.weighting,
                        executionOrder = match.second.executionOrder,
                        startTime = match.first.startTime,
                        startTimeOffset = match.second.startTimeOffset,
                        currentlyRunning = match.first.currentlyRunning,
                        startedAt = match.first.startedAt,
                        finishedAt = match.first.finishedAt,
                        skipped = match.first.skipped,
                        // Dieselbe Ableitung wie im Schiedsrichter-Dashboard - eine Ableitung, drei
                        // Aufrufer. teamsOnWater führt nur diese Ansicht: Zeitplan und öffentliche
                        // Anzeigen lassen es null ("nicht erhoben" ist etwas anderes als 0).
                        status = MatchStatusLogic.matchStatus(
                            currentlyRunning = match.first.currentlyRunning,
                            startTime = match.first.startTime,
                            startedAt = match.first.startedAt,
                            finishedAt = match.first.finishedAt,
                            skipped = match.first.skipped,
                            teams = match.first.teams.map { team ->
                                MatchStatusTeam(
                                    place = team.place,
                                    failed = team.failed,
                                    deregistered = team.deregistered,
                                )
                            },
                            teamsOnWater = teamsOnWaterPerMatch[index],
                        ),
                        raceClockerPolledAt = match.first.raceClockerPolledAt,
                        raceClockerPollError = match.first.raceClockerPollError,
                        raceClockerAutoPausedAt = match.first.raceClockerAutoPausedAt,
                    )
                },
            required = required,
            substitutions = substitutions
        )
    )
}

fun ParticipantRecord.toSubstituteParticipantDto() = SubstitutionParticipantDto(
    id = id,
    firstName = firstname,
    lastName = lastname,
    year = year,
    gender = gender,
    external = external,
    externalClubName = externalClubName,
)

fun CompetitionSetupRoundWithMatchesRecord.toCompetitionSetupRoundWithMatches() = KIO.ok(
    CompetitionSetupRoundWithMatches(
        setupRoundId = setupRoundId!!,
        competitionSetup = competitionSetup!!,
        nextRound = nextRound,
        setupRoundName = setupRoundName!!,
        required = required!!,
        isQualification = isQualification ?: false,
        placesOption = placesOption!!,
        places = places!!.toList().filterNotNull(),
        setupMatches = setupMatches!!.toList().filterNotNull(),
        matches = matches!!.filterNotNull().map { match ->
            CompetitionMatchWithTeams(
                competitionSetupMatch = match.competitionSetupMatch!!,
                startTime = match.startTime,
                currentlyRunning = match.currentlyRunning ?: false,
                startedAt = match.startedAt,
                finishedAt = match.finishedAt,
                skipped = match.skipped ?: false,
                raceClockerPolledAt = match.raceclockerPolledAt,
                raceClockerPollError = match.raceclockerPollError,
                raceClockerAutoPausedAt = match.raceclockerAutoPausedAt,
                teams = match.teams!!.filterNotNull().map { team ->
                    CompetitionMatchTeamWithRegistration(
                        id = team.id!!,
                        competitionMatch = team.competitionMatch!!,
                        startNumber = team.startNumber!!,
                        place = team.place,
                        timeString = team.timecode?.toTimecode()?.toString(),
                        placesCalculated = team.placesCalculated!!,
                        competitionRegistration = team.competitionRegistration!!,
                        clubId = team.clubId!!,
                        clubName = team.clubName!!,
                        registrationName = team.registrationName,
                        teamNumber = team.teamNumber,
                        participants = team.participants!!.filterNotNull().map { p ->
                            CompetitionMatchTeamParticipant(
                                competitionRegistrationId = p.teamId!!,
                                namedParticipantId = p.roleId!!,
                                namedParticipantName = p.role!!,
                                participantId = p.participantId!!,
                                firstName = p.firstname!!,
                                lastName = p.lastname!!,
                                year = p.year!!,
                                gender = p.gender!!,
                                external = p.external,
                                externalClubName = p.externalClubName,
                            )
                        },
                        deregistered = team.deregistered!!,
                        deregistrationReason = team.deregistrationReason,
                        out = team.out!!,
                        failed = team.failed!!,
                        failedReason = team.failedReason,
                        penaltySeconds = team.penaltySeconds,
                        penaltyNote = team.penaltyNote,
                        ratingCategory = team.ratingCategoryName,
                        mixedTeamTerm = mixedTeamTerm,
                    )
                }
            )
        },
        substitutions = substitutions!!.filterNotNull().map { sub ->
            SubstitutionDto(
                id = sub.id!!,
                reason = sub.reason,
                orderForRound = sub.orderForRound!!,
                setupRoundId = sub.competitionSetupRoundId!!,
                setupRoundName = sub.competitionSetupRoundName!!,
                competitionRegistrationId = sub.competitionRegistrationId!!,
                competitionRegistrationName = sub.competitionRegistrationName,
                clubId = sub.clubId!!,
                clubName = sub.clubName!!,
                namedParticipantId = sub.namedParticipantId!!,
                namedParticipantName = sub.namedParticipantName!!,
                participantOut = sub.participantOut!!.toSubstituteParticipantDto(),
                participantIn = sub.participantIn!!.toSubstituteParticipantDto(),
                swapSubstitution = getSwapSubstitution(sub, substitutions!!.filterNotNull()),
                inheritedFrom = sub.inheritedFrom,
            )
        }
    )
)


fun CompetitionMatchTeamWithRegistration.toCompetitionTeamPlaceDto(place: Int) = KIO.ok(
    CompetitionTeamPlaceDto(
        competitionRegistrationId = competitionRegistration,
        teamNumber = teamNumber!!, // This should not be null because competition_match_teams are not created if the registration teamNumber is missing
        teamName = registrationName,
        clubId = clubId,
        clubName = clubName,
        namedParticipants = participants.toNamedParticipantsDto(),
        place = place,
        deregistered = deregistered,
        deregistrationReason = deregistrationReason,
        excluded = deregistered || out || failed,
        actualClubName = singletonOrFallback(participants.map { it.externalClubName }.toSet(), mixedTeamTerm)
    )
)