package de.lambda9.ready2race.backend.app.competition.entity


import java.time.LocalDate
import java.util.UUID

data class EventDataForCompetitionResultsDto (
    val eventName: String,
    val competitionId: UUID,
    val competitionName: String,
    val eventDateRange: Pair<LocalDate, LocalDate>?,
)