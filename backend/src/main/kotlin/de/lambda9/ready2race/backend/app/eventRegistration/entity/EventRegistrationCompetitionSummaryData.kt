package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.RegisteredCompetitionTeamParticipantRecord

data class EventRegistrationCompetitionSummaryData(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val teams: List<Team>,
) {
    data class Team(
        val teamName: String?,
        val participants: List<RegisteredCompetitionTeamParticipantRecord>,
    )
}