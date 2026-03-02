package de.lambda9.ready2race.backend.app.eventDay.entity

import java.util.UUID

data class EventDayScheduleCompetitionDataDto(
    val eventDayId: UUID,
    val competitionName: String,
    val competitionId: UUID,
    val matchDuration: Int?,
    val matchGapsDuration: Int?,
    val rounds : List<EventDayScheduleCompetitionRoundDataDto>,
    val matchCount: Int,
)

data class EventDayScheduleCompetitionRoundDataDto(
    val roundName: String,
    val roundId: UUID,
    val matchCount: Int,
    val matches: List<EventDayScheduleCompetitionMatchDataDto>,
)

data class EventDayScheduleCompetitionMatchDataDto(
    val matchName: String,
    val matchId: UUID,
)
