package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionParticipantDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RegisteredCompetitionTeamParticipantRecord
import de.lambda9.tailwind.core.KIO

fun CompetitionSetupRoundWithMatches.toCompetitionRoundDto() = KIO.ok(
    CompetitionRoundDto(
        setupRoundId = setupRoundId,
        name = setupRoundName,
        matches = matches.map { match -> match to setupMatches.first { setupMatch -> setupMatch.id == match.competitionSetupMatch } }
            .map { match ->
                CompetitionMatchDto(
                    id = match.second.id,
                    name = match.second.name,
                    teams = match.first.teams.map { team ->
                        CompetitionMatchTeamDto(
                            registrationId = team.competitionRegistration,
                            teamNumber = team.teamNumber!!, // This should not be null because competition_match_teams are not created if the registration teamNumber is missing
                            clubId = team.clubId,
                            clubName = team.clubName,
                            name = team.registrationName,
                            startNumber = team.startNumber,
                            place = team.place
                        )
                    },
                    weighting = match.second.weighting,
                    executionOrder = match.second.executionOrder,
                    startTime = match.first.startTime,
                    startTimeOffset = match.second.startTimeOffset,
                )
            },
        required = required,
        substitutions = substitutions
    )
)

fun RegisteredCompetitionTeamParticipantRecord.toSubstituteParticipantDto() = SubstitutionParticipantDto(
    id = participantId!!,
    firstName = firstname!!,
    lastName = lastname!!,
    year = year!!,
    gender = gender!!,
    external = external,
    externalClubName = externalClubName,
)


fun RegisteredCompetitionTeamParticipantRecord.toCompetitionMatchTeamParticipant() = CompetitionMatchTeamParticipant(
    competitionRegistrationId = teamId!!,
    namedParticipantId = roleId!!,
    namedParticipantName = role!!,
    participantId = participantId!!,
    firstName = firstname!!,
    lastName = lastname!!,
    year = year!!,
    gender = gender!!,
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
        placesOption = placesOption!!,
        places = places!!.toList().filterNotNull(),
        setupMatches = setupMatches!!.toList().filterNotNull(),
        matches = matches!!.filterNotNull().map { match ->
            CompetitionMatchWithTeams(
                competitionSetupMatch = match.competitionSetupMatch!!,
                startTime = match.startTime,
                teams = match.teams!!.filterNotNull().map { team ->
                    CompetitionMatchTeamWithRegistration(
                        id = team.id!!,
                        competitionMatch = team.competitionMatch!!,
                        startNumber = team.startNumber!!,
                        place = team.place,
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
                        }
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
                competitionRegistrationName = sub.competitionRegistrationName!!,
                clubId = sub.clubId!!,
                clubName = sub.clubName!!,
                participantOut = sub.participantOut!!.toSubstituteParticipantDto(),
                participantIn = sub.participantIn!!.toSubstituteParticipantDto(),
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
        namedParticipants = participants.groupBy { it.namedParticipantId }.map { np ->
            CompetitionTeamNamedParticipantDto(
                namedParticipantId = np.key,
                namedParticipantName = np.value[0].namedParticipantName, // todo: cleaner?
                participants = np.value.map { p ->
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
        },
        place = place
    )
)