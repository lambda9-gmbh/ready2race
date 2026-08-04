package de.lambda9.ready2race.backend.app.eventInfo.control

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardMatch
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardParticipant
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardResult
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardResultTeam
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardTeam
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationRequest
import de.lambda9.ready2race.backend.app.eventInfo.entity.LatestMatchResultInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchParticipantInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchTeamInfo
import de.lambda9.ready2race.backend.database.generated.tables.records.InfoViewConfigurationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INFO_VIEW_CONFIGURATION
import org.jooq.JSONB
import java.time.LocalDateTime
import java.util.*

fun InfoViewConfigurationRecord.toDto() = InfoViewConfigurationDto(
    id = id!!,
    eventId = eventId!!,
    viewType = viewType!!,
    displayDurationSeconds = displayDurationSeconds!!,
    dataLimit = dataLimit!!,
    filters = filters?.let { ObjectMapper().readTree(it.data()) },
    sortOrder = sortOrder!!,
    isActive = isActive!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!
)

fun InfoViewConfigurationRequest.toRecord(eventId: UUID): InfoViewConfigurationRecord {
    val record = INFO_VIEW_CONFIGURATION.newRecord()
    record.id = UUID.randomUUID()
    record.eventId = eventId
    record.viewType = this.viewType
    record.displayDurationSeconds = this.displayDurationSeconds
    record.dataLimit = this.dataLimit
    record.filters = this.filters?.let { JSONB.jsonb(it.toString()) }
    record.sortOrder = this.sortOrder
    record.isActive = this.isActive
    record.createdAt = LocalDateTime.now()
    record.updatedAt = LocalDateTime.now()
    return record
}

private fun participantName(firstName: String, lastName: String) = "$firstName $lastName"

fun UpcomingMatchParticipantInfo.toAthleteBoardParticipant() = AthleteBoardParticipant(
    name = participantName(firstName, lastName),
    role = namedRole,
)

fun RunningMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    lane = startNumber,
    // Der tatsächliche Verein gewinnt; die Auflösung passiert hier statt in jeder Ansicht.
    clubName = actualClubName ?: clubName,
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
)

fun UpcomingMatchTeamInfo.toAthleteBoardTeam() = AthleteBoardTeam(
    lane = startNumber,
    clubName = actualClubName ?: clubName,
    teamName = teamName,
    participants = participants.map { it.toAthleteBoardParticipant() },
)

fun RunningMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
    )

fun UpcomingCompetitionMatchInfo.toAthleteBoardMatch(now: LocalDateTime, showCountdown: Boolean) =
    AthleteBoardMatch(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = scheduledStartTime,
        startState = AthleteBoardLogic.startState(scheduledStartTime, now, showCountdown),
        teams = teams.map { it.toAthleteBoardTeam() },
        pendingRound = pendingRound,
        name = name,
    )

fun LatestMatchResultInfo.toAthleteBoardResult() = AthleteBoardResult(
    matchId = matchId,
    competitionName = competitionName,
    categoryName = categoryName,
    roundName = roundName,
    matchName = matchName,
    startTime = startTime,
    // Abgemeldete Mannschaften sind nicht gefahren und würden ohne Platz und ohne Zeit
    // wie ein Darstellungsfehler aussehen.
    teams = teams
        .filterNot { it.deregistered }
        .map {
            AthleteBoardResultTeam(
                place = it.place,
                lane = it.startNumber,
                clubName = it.actualClubName ?: it.clubName,
                teamName = it.teamName,
                timeString = it.timeString,
                failed = it.failed,
                failedReason = it.failedReason,
            )
        },
)